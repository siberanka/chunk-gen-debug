# chunk-gen-debug 1.0.0

Initial forensic release by siberanka.

- Writes `chunk-gen.log`, `chunk-load.log`, and `chunk-unload.log` under each
  world's plugin data directory.
- Captures API facts, before/after state, registered listener order, stack-based
  plugin candidates, server mechanism heuristics, plugin tickets, entities,
  generators, populators, and datapack context.
- Uses bounded non-blocking ingestion, asynchronous UTF-8 JSONL output, rotation,
  overflow accounting, and deterministic shutdown.
- Targets Paper/Folia 1.21.x (Java 21) and 26.x (Java 25 runtime).

No migration is required. Back up the existing `plugins/chunk-gen-debug` folder
before replacing a prior development build. Rollback consists of stopping the
server, restoring the previous JAR/config, and retaining logs for investigation.

Known limitation: Bukkit/Paper does not expose an authoritative universal
causing-plugin/datapack field for chunk events. Candidate and correlation fields
are deliberately labelled as evidence rather than proof.
