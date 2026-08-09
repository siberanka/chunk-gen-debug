#!/usr/bin/env python3
"""Fail when OSV reports a vulnerability for a CycloneDX component PURL."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
import urllib.request


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("bom", type=Path)
    arguments = parser.parse_args()
    document = json.loads(arguments.bom.read_text(encoding="utf-8"))
    purls = sorted({component.get("purl") for component in document.get("components", [])
                    if component.get("purl")})
    findings: list[dict[str, object]] = []
    for offset in range(0, len(purls), 1_000):
        batch = purls[offset:offset + 1_000]
        body = json.dumps({"queries": [{"package": {"purl": purl}} for purl in batch]}).encode()
        request = urllib.request.Request(
            "https://api.osv.dev/v1/querybatch",
            data=body,
            headers={"Content-Type": "application/json", "User-Agent": "chunk-gen-debug-sca/1.0"},
            method="POST",
        )
        with urllib.request.urlopen(request, timeout=60) as response:
            results = json.load(response).get("results", [])
        for purl, result in zip(batch, results, strict=True):
            for vulnerability in result.get("vulns", []):
                findings.append({
                    "purl": purl,
                    "id": vulnerability.get("id"),
                    "aliases": vulnerability.get("aliases", []),
                    "summary": vulnerability.get("summary", ""),
                })
    print(json.dumps({"componentsScanned": len(purls), "vulnerabilities": findings},
                     indent=2, sort_keys=True))
    return 1 if findings else 0


if __name__ == "__main__":
    raise SystemExit(main())
