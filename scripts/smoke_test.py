#!/usr/bin/env python3
"""Download a verified Paper/Folia build and exercise real chunk lifecycle events."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import queue
import shutil
import subprocess
import sys
import threading
import time
import urllib.request


def request_json(url: str) -> object:
    request = urllib.request.Request(url, headers={"User-Agent": "chunk-gen-debug-smoke/1.0"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def latest_build(project: str, version: str) -> dict[str, object]:
    payload = request_json(f"https://fill.papermc.io/v3/projects/{project}/versions/{version}/builds")
    builds = payload if isinstance(payload, list) else [payload]
    stable = [build for build in builds if build.get("channel") == "STABLE"]
    candidates = stable or builds
    if not candidates:
        raise RuntimeError(f"No {project} build found for {version}")
    return max(candidates, key=lambda build: int(build["id"]))


def download_verified(build: dict[str, object], destination: Path) -> None:
    downloads = build["downloads"]
    artifact = downloads["server:default"]
    expected = artifact["checksums"]["sha256"]
    if destination.exists() and sha256(destination) == expected:
        return
    temporary = destination.with_suffix(".tmp")
    request = urllib.request.Request(artifact["url"], headers={"User-Agent": "chunk-gen-debug-smoke/1.0"})
    with urllib.request.urlopen(request, timeout=120) as response, temporary.open("wb") as output:
        shutil.copyfileobj(response, output)
    actual = sha256(temporary)
    if actual != expected:
        temporary.unlink(missing_ok=True)
        raise RuntimeError(f"Server SHA-256 mismatch: expected {expected}, got {actual}")
    temporary.replace(destination)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def read_output(process: subprocess.Popen[str], log_path: Path, messages: queue.Queue[str]) -> None:
    assert process.stdout is not None
    with log_path.open("w", encoding="utf-8", newline="\n") as log:
        for line in process.stdout:
            sys.stdout.write(line)
            log.write(line)
            log.flush()
            messages.put(line)


def wait_for_ready(messages: queue.Queue[str], process: subprocess.Popen[str], timeout: int) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"Server exited before ready with code {process.returncode}")
        try:
            line = messages.get(timeout=1)
        except queue.Empty:
            continue
        if "Done (" in line and "For help" in line:
            return
    raise TimeoutError(f"Server did not become ready within {timeout}s")


def send(process: subprocess.Popen[str], command: str) -> None:
    if process.stdin is None:
        raise RuntimeError("Server stdin is unavailable")
    process.stdin.write(command + "\n")
    process.stdin.flush()


def kinds(path: Path) -> set[str]:
    result: set[str] = set()
    candidates = [path] + sorted(path.parent.glob(path.name + ".*"))
    for candidate in candidates:
        with candidate.open(encoding="utf-8") as source:
            for number, line in enumerate(source, 1):
                try:
                    record = json.loads(line)
                except json.JSONDecodeError as failure:
                    raise RuntimeError(f"Invalid JSONL in {candidate}:{number}: {failure}") from failure
                if isinstance(record, dict) and isinstance(record.get("kind"), str):
                    result.add(record["kind"])
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", choices=("paper", "folia"), required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--java", default=os.environ.get("JAVA_BIN", "java"))
    parser.add_argument("--timeout", type=int, default=300)
    parser.add_argument("--extra-plugin-url")
    parser.add_argument("--extra-plugin-sha256")
    parser.add_argument("--before-probe-command", action="append", default=[])
    parser.add_argument("--probe-world", default="world")
    arguments = parser.parse_args()

    repository = Path(__file__).resolve().parents[1]
    plugin_jars = sorted((repository / "build" / "libs").glob("chunk-gen-debug-*.jar"))
    plugin_jars = [path for path in plugin_jars if not path.name.endswith(("-sources.jar", "-javadoc.jar"))]
    if len(plugin_jars) != 1:
        raise RuntimeError("Expected exactly one built plugin JAR; run ./gradlew clean jar first")

    cache = repository / ".smoke-cache"
    cache.mkdir(exist_ok=True)
    build = latest_build(arguments.project, arguments.version)
    server_jar = cache / f"{arguments.project}-{arguments.version}-{build['id']}.jar"
    download_verified(build, server_jar)

    work_root = (repository / "smoke-work").resolve()
    work = (work_root / f"{arguments.project}-{arguments.version}").resolve()
    if work_root not in work.parents:
        raise RuntimeError("Refusing to clean a smoke directory outside the workspace")
    if work.exists():
        shutil.rmtree(work)
    (work / "plugins").mkdir(parents=True)
    shutil.copy2(plugin_jars[0], work / "plugins" / plugin_jars[0].name)
    if arguments.extra_plugin_url:
        if not arguments.extra_plugin_sha256:
            raise RuntimeError("--extra-plugin-sha256 is required with --extra-plugin-url")
        name = arguments.extra_plugin_url.rsplit("/", 1)[-1]
        cached_extra = cache / name
        if not cached_extra.exists() or sha256(cached_extra) != arguments.extra_plugin_sha256:
            request = urllib.request.Request(
                arguments.extra_plugin_url, headers={"User-Agent": "chunk-gen-debug-smoke/1.0"})
            temporary = cached_extra.with_suffix(".tmp")
            with urllib.request.urlopen(request, timeout=120) as response, temporary.open("wb") as output:
                shutil.copyfileobj(response, output)
            actual = sha256(temporary)
            if actual != arguments.extra_plugin_sha256:
                temporary.unlink(missing_ok=True)
                raise RuntimeError(f"Extra plugin SHA-256 mismatch: expected "
                                   f"{arguments.extra_plugin_sha256}, got {actual}")
            temporary.replace(cached_extra)
        shutil.copy2(cached_extra, work / "plugins" / name)
    shutil.copy2(server_jar, work / "server.jar")
    (work / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (work / "server.properties").write_text(
        "online-mode=false\nview-distance=2\nsimulation-distance=2\n"
        "max-tick-time=-1\nlevel-seed=chunk-gen-debug-smoke\nserver-port=0\n",
        encoding="utf-8",
    )

    command = [arguments.java, "-Xms512M", "-Xmx1G", "-jar", "server.jar", "--nogui"]
    process = subprocess.Popen(
        command,
        cwd=work,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        bufsize=1,
    )
    messages: queue.Queue[str] = queue.Queue()
    log_path = work / "server.log"
    reader = threading.Thread(target=read_output, args=(process, log_path, messages), daemon=True)
    reader.start()
    try:
        wait_for_ready(messages, process, arguments.timeout)
        for pre_command in arguments.before_probe_command:
            send(process, pre_command)
            time.sleep(10)
        if arguments.project == "folia":
            # Older Folia builds lazily initialise a world on its first region tick.
            # Start the spawn region before probing a distant region; otherwise the
            # server itself can attempt a cross-region synchronous spawn load.
            send(process, f"cgd probe {arguments.probe_world} 0 0")
            time.sleep(15)
            if process.poll() is not None:
                raise RuntimeError("Folia exited while initialising the spawn region")
        send(process, f"cgd probe {arguments.probe_world} 10000 10000")
        time.sleep(30)
        if arguments.project == "paper":
            send(process, "save-all flush")
            time.sleep(3)
        send(process, "stop")
        process.wait(timeout=120)
    except BaseException:
        if process.poll() is None:
            try:
                send(process, "stop")
                process.wait(timeout=30)
            except BaseException:
                process.kill()
        raise
    finally:
        reader.join(timeout=5)

    if process.returncode != 0:
        raise RuntimeError(f"Server exited with code {process.returncode}")
    server_log = log_path.read_text(encoding="utf-8")
    if "Forensic chunk diagnostics enabled" not in server_log:
        raise RuntimeError("Plugin enable marker not found in server log")
    if "Error occurred while enabling chunk-gen-debug" in server_log:
        raise RuntimeError("Plugin enable failure found in server log")
    if "Could not pass event" in server_log and "chunk-gen-debug" in server_log:
        raise RuntimeError("Plugin event-handler failure found in server log")

    log_root = work / "plugins" / "chunk-gen-debug" / arguments.probe_world
    expected = {
        "chunk-gen.log": {"generation_detected", "population_complete"},
        "chunk-load.log": {"chunk_load"},
        "chunk-unload.log": {"chunk_unload"},
    }
    summary: dict[str, list[str]] = {}
    for name, required_any in expected.items():
        path = log_root / name
        if not path.exists():
            raise RuntimeError(f"Missing expected log file: {path}")
        observed = kinds(path)
        if not observed.intersection(required_any):
            raise RuntimeError(f"{name} lacks {sorted(required_any)}; observed {sorted(observed)}")
        summary[name] = sorted(observed)
    print("SMOKE_TEST_OK " + json.dumps({
        "project": arguments.project,
        "version": arguments.version,
        "build": build["id"],
        "channel": build["channel"],
        "probeWorld": arguments.probe_world,
        "logs": summary,
    }, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
