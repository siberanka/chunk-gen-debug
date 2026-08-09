# Research and engineering decisions

Audit date: 2026-08-09 (Europe/Istanbul)

## Verified target context

| Item | Decision |
|---|---|
| Minecraft | 1.21.x and 26.x |
| Server | Paper and Folia; Bukkit API lifecycle events only |
| Runtime | Java 21 for 1.21.x; Java 25 for 26.1+ |
| Build bytecode | Java 21, forward-runnable on Java 25 |
| Compile API | `paper-api:1.21-R0.1-SNAPSHOT`, lowest target API; checksum gate pins resolved JAR bytes |
| Build | Gradle wrapper 8.14.3 |
| Multiverse | Transparent Bukkit world lifecycle compatibility; no hard dependency |

## Source matrix

| Source | Accessed | Relevant version | Supported decision | Confidence |
|---|---:|---|---|---|
| Paper Getting Started | 2026-08-09 | 1.21.11 / 26.1+ | Java 21 / Java 25 runtime split | Official |
| Paper 1.21.11 announcement | 2026-08-09 | 1.21.11 / 26.1 | New version scheme and removal of runtime remapper | Official |
| Mojang version numbering announcement | 2026-08-09 | 26.x | Year/drop version model | Official |
| Paper 1.21.11 Javadocs | 2026-08-09 | 1.21.11 | Chunk event contracts and `isNewChunk` limitation | Official API |
| Paper 26.2 Javadocs | 2026-08-09 | 26.2 | Chunk lifecycle events remain present | Official API |
| Paper Folia support docs | 2026-08-09 | current | Region ownership and non-blocking I/O requirements | Official |
| Multiverse developer docs/release | 2026-08-09 | 5.7.3 | Worlds are exposed through Bukkit; pinned dynamic-world smoke asset | Project official |

## Attribution boundary

Paper/Bukkit does not expose a universal, authoritative “causing plugin/datapack”
field on a chunk event. The plugin therefore labels evidence precisely:

- `CONFIRMED`: API facts such as `isNewChunk`, final save flag, plugin chunk
  tickets, generator/populator class, event listener registration, and enabled
  datapacks.
- `STACK_CANDIDATE`: plugin-owned classes still present in the synchronous call
  stack. This is strong evidence but not proof of semantic causation.
- `CORRELATED`: a recent teleport/portal destination marker for the same chunk.
- `MECHANISM_HEURISTIC`: stable, human-readable classification of observed stack
  class names (player view, teleport, spawn preload, scheduler, generator, etc.).

Enabled datapacks and event listeners are context, never reported as the cause.
No NMS hooks, bytecode instrumentation, scheduler replacement, or unsafe
reflection is used. Those techniques would make broad 1.21.x/26.x and Folia
support substantially less reliable.

## Data flow and invariants

`event thread -> immutable observation -> bounded offer -> one writer thread -> rotated UTF-8 JSONL`

- Event and region threads never wait for disk I/O.
- Folia world-global ticket state is not read from chunk region callbacks;
  affected fields are explicitly marked unavailable instead of crossing ownership.
- Queue and open file handles are bounded.
- Each accepted record is written to exactly one requested stream.
- Every rejected record increments a per-target counter; the writer emits an
  `overflow` diagnostic when capacity becomes available.
- Paths are normalized beneath the plugin data directory; hostile world names
  cannot escape it.
- Shutdown stops intake, drains within the configured deadline, flushes, and
  closes every handle.

## Supply-chain review

The release JAR contains no third-party classes. An all-configuration CycloneDX
SBOM initially exposed vulnerable `commons-lang3:3.12.0` (CVE-2025-48924) and
`plexus-utils:3.5.1` (CVE-2025-67030) through the compile-only Paper API POM.
They are not runtime-shipped or called by the plugin, but constraints still lift
the build graph to patched 3.18.0 and 3.6.1. OSV scanning remains a blocking CI
gate. Dependency resolution is locked and a blocking SHA-256 task rejects silent
Paper API snapshot byte changes. Paper only introduced directly addressable
build coordinates in 26.1+, so 1.21's canonical snapshot coordinate is retained.

## Compatibility notes

Multiverse-Core, MyWorlds, AdvancedWorldManager, custom generators, and similar
tools ultimately create/register Bukkit worlds and fire the same lifecycle
events. Loading at `STARTUP`, enumerating already-loaded worlds, and observing
future `WorldInitEvent`/`WorldLoadEvent` makes integration dependency-free.
When a world manager synchronously requests a chunk, its plugin classloader can
appear as a stack candidate. Async or deferred chains may only be classifiable
as a server mechanism; this limitation is surfaced in every record.
