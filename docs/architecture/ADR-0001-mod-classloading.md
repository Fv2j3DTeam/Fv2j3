# ADR 0001: Mod ClassLoading Strategy

- Status: Implemented in Phase 10, with zero Forge dependency
- Date: 2026-08-30

## Context

Fv2j3 has a stable metadata discovery, validation, registry, and readiness pipeline. The next challenge is to connect that runtime to a real Minecraft 1.12.2 host boundary without introducing Forge or overly broad host coupling.

The project must remain fixed to Minecraft 1.12.2, must keep `loader-api` free of game code, and must not expose Minecraft internals to the public API layer.

## Decision

The project uses a layered class loading strategy and extends it in Phase 10 with an explicit bootstrap boundary. Each valid mod receives a dedicated `ModClassLoader`, the runtime resolves the entrypoint class, instantiates the `Mod`, invokes lifecycle callbacks, and closes classloader resources during shutdown. The host bootstrap checks for a real Minecraft 1.12.2 runtime and fails explicitly if none is present instead of claiming a fake launch.

Implemented approach:

```text
Bootstrap/System
  ↓
MinecraftBootstrap.detect(...)
  ↓
Fv2j3 API parent
  ↓
Fv2j3 Core / Runtime
  ↓
minecraft-compat
  ↓
Per-Mod ClassLoader
  ↓
Mod code
```

This means:

- `io.github.fv2j3dteam.api.*` resolves from a stable parent loader
- mod code is isolated per mod
- dependency and lifecycle ordering are resolved before class loading begins
- the loader remains decoupled from Minecraft game code and Forge bootstrapping
- `ModClassLoader` and `DefaultModRuntime` are the concrete runtime implementation
- `ModRuntime`, `ModInstance`, and `ModLifecycleAdapter` coordinate the actual lifecycle execution
- `MinecraftBootstrap` validates the host runtime without pretending to launch unavailable game files

## Alternatives considered

### Shared ClassLoader

Pros:
- simple
- low overhead

Cons:
- unacceptable class conflicts across mods
- not suitable for long-term isolation

### One ClassLoader per Mod

Pros:
- isolation
- future-friendly

Cons:
- more lifecycle complexity

### Layered approach (chosen)

Pros:
- preserves API stability
- matches the project structure
- supports future dependency visibility rules
- allows a clear host/bootstrap boundary

Cons:
- more design work
- requires a real external Minecraft runtime for a full launch verification

## Consequences

### Positive

- API code remains stable
- runtime can reason about dependencies before class loading
- mod isolation becomes possible
- loader remains decoupled from Minecraft internals
- real ClassLoader cleanup and runtime shutdown are enforced
- bootstrap can fail explicitly when no real Minecraft 1.12.2 runtime is present

### Negative

- per-mod classloading adds lifecycle complexity
- dependency visibility must be managed carefully
- host bootstrap is intentionally explicit and cannot fake a real game launch

## Phase 10 status

The implementation is complete for the standalone Fv2j3 runtime and bootstrap boundary. This architecture is intentionally Forge-independent and does not require any `net.minecraftforge.*` dependency or bootstrap.

## Notes

The runtime remains focused on the standalone Fv2j3 mod-loader contract and does not claim to supply a Minecraft client launch unless a valid external Minecraft 1.12.2 runtime is available in the environment.
