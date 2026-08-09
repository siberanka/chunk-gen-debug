# Validation report

Audit date: 2026-08-09, Europe/Istanbul

## Executive summary

Version 1.0.0 implements a greenfield, read-only forensic observer. It does not
mutate inventories, economy, or persistent gameplay state; the only intentional
world mutation is the explicit, permission-gated `/cgd probe` diagnostic command.
The principal correctness risk was blocking/cross-thread work in chunk event hot
paths. Disk I/O is isolated behind bounded non-blocking ingestion, and a real
Folia test found and drove removal of two world-global reads from region events.

## Verified environment

| Platform | Minecraft/build | JDK | Result |
|---|---|---:|---|
| Paper | 1.21.11 build 132 stable | Temurin 21.0.11 | Pass |
| Paper | 26.2 build 111 stable | Temurin 25.0.4 | Pass |
| Folia | 1.21.8 build 6 stable | Temurin 21.0.11 | Pass with spawn-region bootstrap |
| Folia | 1.21.11 build 14 stable | Temurin 21.0.11 | Pass after FOLIA-001 fix |
| Folia | 26.2 build 1 beta | Temurin 25.0.4 | Pass, including final code-state rerun |
| Paper + Multiverse-Core | Paper 26.2-111 + MV 5.7.3 | Temurin 25.0.4 | Dynamic world pass |

CI additionally blocks release on Paper 1.21/1.21.11/26.1.2/26.2 and Folia
1.21.8/1.21.11/26.1.2/26.2 representative tests. Folia 26.2 remains a beta upstream,
so its support status is intentionally labelled preview-supported.

Build: Gradle wrapper 8.14.3 (distribution SHA-256 pinned), Java 21 bytecode
(class major 65), Paper 1.21 compile API, no NMS and no runtime dependencies.

## Mechanics and data flow

```text
Bukkit/Paper/Folia event
  -> region-owned immutable observation
  -> bounded non-blocking queue offer
  -> single daemon writer
  -> safe per-world path
  -> rotation + UTF-8 JSONL flush
```

Accepted records have exactly one target stream. Rejected offers are counted and
later represented by an `overflow` record. World/datapack/plugin context is
revisioned separately so large context is not recomputed for every event.

## Findings

| ID | Severity | Confidence | Status | Regression evidence |
|---|---|---|---|---|
| FOLIA-001 | High | Confirmed | Fixed | Real Folia probe would throw if global ticket getters return |
| CI-001 | Medium | Confirmed | Fixed | JDK 21 build and JDK 25 runtime phases are separate |
| SUPPLY-001 | High/Moderate | Confirmed | Fixed | OSV gate covers patched Plexus/Commons Lang constraints |
| PATH-001 | High | High confidence | Mitigated | traversal/collision unit tests + real-path/symlink guard |
| PERF-001 | High | High confidence | Mitigated | bounded concurrent producer conservation test |
| ATTR-001 | Informational | Confirmed API limit | Documented | records distinguish fact/candidate/correlation/heuristic |

FOLIA-001 root cause was calling `Chunk#isForceLoaded()` and plugin ticket reads
from a Folia chunk-region callback even though those values are global-region
owned. Folia now records an explicit `UNAVAILABLE_REQUIRES_GLOBAL_REGION` value,
preserving thread ownership rather than fabricating or asynchronously racing the
event snapshot. The final Folia 26.2 rerun wrote 42/42 records with zero drops,
write failures, or handler exceptions.

CI-001 was exposed only by the first remote matrix: Gradle 8.14.3 cannot run the
Java 21-targeted build under the runner's JDK 25. The matrix now builds with JDK
21, then switches only the 26.x server process to JDK 25. The Folia harness also
boots the spawn region before a distant probe because older Folia builds lazily
initialise the world on its first region tick.

SUPPLY-001 involved compile-only dependencies inherited from the old Paper API
POM: Commons Lang 3.12.0 and Plexus Utils 3.5.1. They were never packaged or
called at runtime, but constraints lift them to 3.18.0 and 3.6.1. The final OSV
scan reported zero vulnerabilities across 65 SBOM components.

## Test evidence

| Claim | Command/test | Result | Constraint |
|---|---|---|---|
| Compiler/static gate | `./gradlew check jar --rerun-tasks --warning-mode all` | Pass, no warnings | Java compiler + project checks |
| Unit/concurrency | JUnit 5 suite | 15/15, 0 failures, 0 errors, 0.4 s | Synthetic producer load |
| Queue conservation | 8 producers, 16,000 records | `accepted + dropped == submitted`; accepted drained | Not a full server load profile |
| Dependency security | `scripts/osv_scan.py build/reports/cyclonedx/bom.json` | 65 scanned, 0 findings | OSV known-vulnerability coverage |
| Paper runtime | `scripts/smoke_test.py` on 1.21.11 and 26.2 | Pass | Isolated offline server |
| Folia runtime | same probe on 1.21.8, 1.21.11, and 26.2 | Pass after fixes | 26.2 upstream is beta |
| Multiverse | MV 5.7.3 creates `cgd_multiverse`, then probe | 102/102 records, all streams | One representative world manager |
| Reproducibility | two clean JAR builds | identical SHA-256 | Same host/toolchain/cache ecosystem |
| JAR inspection | `jar tf`, `javap -verbose` | 74,241 bytes, no duplicates, class major 65 | Manual content gate |

Local verified JAR SHA-256:

```text
eda63a5771bf1b30241283a3bcd06f4f466cfdf9fd78e7cd398b4849ae0508f7
```

The GitHub release computes and publishes its own hash after the complete remote
boundary matrix; that release hash is authoritative if it differs because of a
later reviewed commit.

## Performance result

This is a greenfield implementation, so there is no honest before/after baseline.
The measured local concurrency test completed its 16,000 producer attempts within
the suite's 10-second ceiling (the full 15-test suite took 0.4 seconds). Real
server smoke shutdown counters showed no queue drops or write failures. No claim
is made about a specific TPS/MSPT improvement. Production profiling should use
spark/JFR with the server's actual chunk rate; stack capture can be disabled if
measured incident overhead is too high.

## Dupe/item-loss invariant

No inventory/value mutation exists, so item conservation and idempotent delivery
are not applicable. The relevant conservation invariant is diagnostic accounting:
every submission increments exactly one of accepted or dropped; accepted records
are drained on a normal close. Reverting the bounded writer/overflow behavior
breaks `AsyncLogWriterTest`; reverting the Folia ownership fix breaks the real
Folia smoke with an event-handler exception. The test suite also checks rotation,
path traversal, correlation bounds/expiry, JSON control-character escaping, and
both Minecraft version schemes.

## Migration, rollback, and privacy

There is no database/data migration. Back up `plugins/chunk-gen-debug` before an
upgrade if logs must be retained. To roll back, stop the server, restore the
previous JAR and matching config, then restart; log files are append-only JSONL
and may be archived independently. Downgrading a Minecraft world itself is not
supported by Paper and is outside this plugin's rollback.

Player identities are disabled by default. Logs still contain coordinates,
world UUIDs, plugin versions, class names, and stack frames; redact before sharing.

## Remaining limitations

- Bukkit/Paper has no authoritative universal causing-plugin/datapack field.
  Deferred task chains can lose plugin frames; evidence is deliberately labelled.
- Folia global ticket detail is unavailable inside region event snapshots by
  design. Crossing ownership merely to enrich a log would make the snapshot stale.
- Only Multiverse-Core was exercised locally; other world managers are covered by
  the same Bukkit lifecycle contract but not falsely claimed as separately tested.
- No 30–60 minute multiplayer soak or production spark/JFR profile was run locally.
  CI runs lifecycle smokes, not a realistic player-load benchmark.
