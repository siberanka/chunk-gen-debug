# chunk-gen-debug

`chunk-gen-debug` is a forensic Paper/Folia plugin by **siberanka**. It records
chunk generation, load, and unload lifecycles as structured UTF-8 JSON Lines,
without performing disk I/O on a tick or Folia region thread.

## Output

For a normal world named `world`, files are written to:

```text
plugins/chunk-gen-debug/world/chunk-gen.log
plugins/chunk-gen-debug/world/chunk-load.log
plugins/chunk-gen-debug/world/chunk-unload.log
```

Unsafe filesystem characters in unusual world names are replaced and a short
hash is appended, preventing path traversal and name collisions. Each record has
an `eventId` and `contextId` and includes the world key/UUID, chunk/region
coordinates, thread, event async flag, plugin tickets, listener pipeline,
before/after flags, stack frames, plugin candidates, mechanism classification,
and recent causal markers. Generation logs use both `ChunkLoadEvent#isNewChunk`
and `ChunkPopulateEvent`; entity load/unload records include bounded type/detail
summaries.

World-context records list the current generator, block populators, their plugin
classloader owners, enabled datapacks, server/JDK versions, and world properties.
These are context—not a claim that every listed datapack or listener caused the
chunk operation.

## Attribution semantics

| Status | Meaning |
|---|---|
| API fact | Paper/Bukkit directly exposes it (`isNewChunk`, save flag, ticket) |
| `STACK_CANDIDATE` | A plugin-owned class was in the synchronous call stack |
| `CORRELATED_NOT_PROOF` | A recent marker targeted the same world/chunk |
| mechanism heuristic | Stack class names match a known server mechanism |

There is no universal Bukkit event field that names the plugin, datapack, or
complete async task chain that caused a chunk operation. The plugin never turns
context into a false “confirmed cause.” For deferred chains, use event IDs,
timestamps, stack candidates, listener order, and nearby server logs together.

## Compatibility

| Platform | Minecraft | Runtime JDK | Status |
|---|---|---:|---|
| Paper | 1.21.x | 21 | Supported; CI 1.21 and 1.21.11 boundary smokes |
| Paper | 26.x | 25 | Supported; CI 26.1.2 and 26.2 boundary smokes |
| Folia | 1.21.x | 21 | Supported; CI earliest available 1.21.4 and 1.21.11 smokes |
| Folia | 26.x | 25 | Preview-supported; CI 26.1.2 and beta 26.2 smokes |

The JAR is compiled to Java 21 bytecode and runs forward on Java 25. It uses the
lowest `1.21` Paper API and no NMS, CraftBukkit version parsing, bytecode agent,
or server-internal reflection. Minecraft's year-based `26.x` version format is
parsed explicitly.

Multiverse-Core, MyWorlds, AdvancedWorldManager, and custom world plugins need no
adapter: startup loading plus Bukkit `WorldInitEvent`/`WorldLoadEvent` covers
existing and dynamically created worlds. Synchronous world-manager activity can
appear as a plugin stack candidate. See [research decisions](docs/RESEARCH.md).
CI also boots the current pinned Multiverse-Core release, creates a world through
its command API, probes that world, and validates its independent log directory.

## Install and operate

1. Install the JAR in `plugins/` and start the server.
2. Review `plugins/chunk-gen-debug/config.yml`; defaults are bounded and exclude
   player identities.
3. Use `/cgd status` to inspect accepted/written/dropped/failure counters.
4. Use `/cgd reload` for diagnostic-only settings. Writer/rotation changes need
   a full restart so records never straddle incompatible writer configurations.
5. In a disposable area, `/cgd probe world 10000 10000` generates, loads, then
   requests unload of a chunk. It is permission-gated and can modify world files.

On Folia, `reload` and `probe` must be issued from console so their initial global
world/config lookup never crosses from a player-owned region.

Logs rotate by size. If the queue fills, producers do not block; a later
`overflow` record states exactly how many observations were dropped. Treat logs
as sensitive operational data even though player IDs are disabled by default.

## Build and test

```bash
./gradlew clean check jar
python3 scripts/smoke_test.py --project paper --version 1.21.11
python3 scripts/smoke_test.py --project paper --version 26.2
python3 scripts/smoke_test.py --project folia --version 1.21.11
python3 scripts/smoke_test.py --project folia --version 26.2
```

The 26.x commands require Java 25 (`--java /path/to/java` can select it). `check`
runs unit/concurrency tests, JaCoCo, and CycloneDX SBOM generation. CI additionally
scans that SBOM with OSV, performs clean-build reproducibility checks, and runs
eight boundary real-server smokes plus a pinned Multiverse dynamic-world smoke.

## Performance model

The hot path performs bounded snapshot construction, stack walking (configurable),
and non-blocking `ArrayBlockingQueue#offer`. A single daemon writer owns all file
handles, rotation, flushing, and JSON encoding. Open files, queue capacity,
stack/listener/entity detail, correlation entries, retained rotations, and
shutdown drain time are all bounded. Disable stack capture if profiling proves it
too expensive for a specific incident rate; this reduces attribution quality.

## License

[MIT](LICENSE)
