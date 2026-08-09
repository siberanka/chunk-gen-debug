# Changelog

All notable changes follow [Semantic Versioning](https://semver.org/).

## 1.0.0 - 2026-08-09

### Added

- Separate JSON Lines logs for chunk generation, load, and unload events.
- Stack-based plugin/task candidates, mechanism classification, listener order,
  plugin chunk tickets, world generator/populator ownership, and datapack context.
- Bounded asynchronous I/O, log rotation, overflow accounting, and clean shutdown.
- Paper/Folia-compatible event-only hot path with no NMS dependency.
- Unit, concurrency, reproducibility, and real-server smoke-test tooling.
