# Mod Runtime Architecture

## 1. Scope

This document describes the Phase 10 runtime model for Fv2j3.

The runtime remains intentionally standalone: it loads real mod JARs, creates per-mod ClassLoaders, resolves entrypoints, instantiates real `Mod` implementations, invokes lifecycle callbacks, and closes runtime resources without any Forge dependency or Forge bootstrap.

Phase 10 adds a real standalone bootstrap path and a minimal profiling layer while continuing to avoid any fake or mock Minecraft launch.

## 2. Current runtime state

The project currently supports:

- Bootstrap startup
- Environment detection
- Loader lifecycle
- Mod source discovery
- Metadata validation
- Candidate registration
- Dependency graph ordering
- Container readiness
- Real mod runtime execution
- Lifecycle cleanup and resource release
- Bootstrap validation for Minecraft 1.12.2 availability
- Event dispatch and lightweight performance benchmark tracking

## 3. Runtime pipeline

```text
Bootstrap
  ↓
Detect Minecraft 1.12.2 runtime
  ↓
Fv2j3Loader
  ↓
ModSource(s)
  ↓
Discovery
  ↓
Metadata Validation
  ↓
Candidate
  ↓
ModRegistry
  ↓
Dependency Graph
  ↓
Real ModClassLoader
  ↓
Entrypoint resolution
  ↓
Mod instance creation
  ↓
Lifecycle: onLoad → onInitialize → onStart → onStop
  ↓
Runtime cleanup / ClassLoader close
```

This pipeline is a real runtime pipeline for Fv2j3 and is intentionally independent of Minecraft and Forge.

## 4. Bootstrap boundary

Phase 10 adds an explicit host boundary model for the Minecraft runtime:

```text
Fv2j3 bootstrap
  ↓
MinecraftBootstrap.detect(...)
  ↓
Runtime located or explicit startup failure
  ↓
Fv2j3 loader startup
```

This design is intentionally honest. If the environment does not contain a real Minecraft 1.12.2 runtime, Fv2j3 fails with a clear diagnostic instead of pretending to have launched the client.

## 5. ClassLoader model

Each mod is isolated in a dedicated `ModClassLoader` with parent delegation to the stable Fv2j3 API layer. The loader preserves the protected namespace boundaries:

- `com.fv2j3.*`
- `net.minecraft.*`
- `net.minecraftforge.*`

This prevents mod code from claiming ownership of the runtime or host namespaces without introducing any forge dependency.

## 6. API class visibility

The public API stays canonical across all mod classloaders. `Mod`, `ModContainer`, `ModRuntime`, `ModLifecycleAdapter`, and related types resolve from the parent layer so they keep a single class identity across the runtime.

## 7. Dependency and lifecycle model

For `A -> B`:

- `B` is resolved before `A`
- runtime creation happens only after dependency ordering is valid
- missing or cyclic dependencies fail explicitly
- lifecycle failures stop the mod and trigger cleanup

## 8. Event and profiling model

Phase 10 adds a lightweight, non-Forge event bus and benchmark runner for startup profiling:

```text
LoaderEventBus
  ↓
subscribe/publish
```

This keeps event dispatch simple, avoids reflection-heavy scanning, and allows Fv2j3 to report startup timing without adding a large dependency tree.

## 9. Known limitation

This repository does not vend a Minecraft 1.12.2 client jar or game assets. That means the project can validate the standalone bootstrap contract, the classloading pipeline, and the runtime lifecycle, but it cannot claim to have launched a real Minecraft client from this environment without an external runtime installation.


Important constraints:

- `READY` means the mod has passed validation and dependency checks but is not yet executed
- `LOADING` and `INITIALIZING` require a real ClassLoader and real class resolution
- `FAILED` is terminal for one mod and should not silently mutate unrelated mods
- stop order should be reverse of start order

## 10. Lifecycle adapter

A `ModLifecycleAdapter` should be introduced in the future as the coordination layer between:

- `ModContainer`
- `Mod instance`
- `LoaderContext`
- `LoaderLogger`

It should be responsible for:

- invoking lifecycle hooks in order
- translating lifecycle exceptions into structured failure reports
- keeping a deterministic order consistent with dependency graph resolution
- making sure startup/shutdown events never bypass the registry and runtime state

This adapter is a design target, not a current runtime implementation.

## 11. Minecraft 1.12.2 integration boundary

Fv2j3 is fixed to Minecraft 1.12.2 and must keep this goal explicit.

The recommended architecture is:

```text
Mod
  ↓
Fv2j3 API
  ↓
minecraft-compat
  ↓
Minecraft 1.12.2 implementation
```

This is important because:

- loader-api must remain free of `net.minecraft.*` or `net.minecraftforge.*`
- minecraft-compat remains the compatibility boundary
- mod code should not directly depend on game internals unless a dedicated compatibility layer is deliberately introduced

## 12. Contract-level runtime lock-in for Phase 8

Phase 8 does not instantiate a real mod runtime. Instead, it locks the runtime contract so a later implementation can be derived from a stable API.

The runtime contract is intentionally explicit:

- `ModClassLoaderProvider` defines ownership and cleanup semantics without invoking a real classloader
- `ModRuntime` defines transition validation and terminal state rules
- `ModContainer` exposes runtime ownership and close policy
- `ModInstance` defines the instance boundary without creating a real Java object
- `ModLifecycleAdapter` defines lifecycle ordering without invoking lifecycle hooks on real mod code

This keeps the architecture consistent with the project's rule that no mod classes are loaded during design phases.

## 13. ClassLoader ownership and closure

The runtime must make ownership explicit before any real `ClassLoader` implementation is introduced.

Recommended ownership model:

```text
Fv2j3Loader
  ↓
LoaderContext
  ↓
ModRegistry
  ↓
ModContainer
  ↓
ModRuntime
  ↓
ClassLoader
```

Responsibilities:

- `Fv2j3Loader`: owns the overall lifecycle and shutdown sequence
- `LoaderContext`: owns runtime configuration, logger, registry, and source metadata
- `ModRegistry`: owns container registration and the dependency graph
- `ModContainer`: owns lifecycle state and runtime reference
- `ModRuntime`: owns classloader, instance, and lifecycle adapter
- `ClassLoader`: owns mod classes, jar resources, and classpath exposure

The close rules must be deterministic:

- `STOPPED` or `FAILED` must trigger `closeClassLoader(...)`
- shutdown order must respect dependency ordering
- a failed mod cannot leave a live classloader behind

## 14. Parent delegation strategy

The project recommends parent-first class loading for `com.fv2j3.api.*` and implementation packages owned by Fv2j3 itself.

Suggested delegation rules:

```text
Mod class loader
  ↓
Parent: Fv2j3 API
  ↓
Parent: Fv2j3 Core / Runtime
  ↓
Parent: minecraft-compat
  ↓
Local mod classes
```

This avoids duplicate API identities while still supporting mod-local code.

### Package ownership table

| Package | Owner | Mod can define it? |
| --- | --- | --- |
| `com.fv2j3.api.*` | Fv2j3 API parent | No |
| `com.fv2j3.loader.*` | Fv2j3 core | No |
| `com.fv2j3.minecraft.*` | minecraft-compat | No |
| `net.minecraft.*` | Minecraft | No |
| `net.minecraftforge.*` | Forge | No |
| Mod package | Mod | Yes |

The rule is intentionally strict: Fv2j3 must never permit a mod to shadow its own API or loader internals.

## 15. Dependency class visibility

For `A -> B`, the class visibility rules must be explicit.

Recommended strategy:

- dependency order is resolved using `ModDependencyGraph`
- `B` remains visible to `A` only through the runtime’s controlled class visibility model
- no hidden class loading is allowed when a dependency is missing
- no circular dependency may reach runtime loading

This avoids the two classes problem:

```text
B.class loaded by A loader
!=
B.class loaded by B loader
```

The runtime must preserve one canonical class identity for each shared dependency class.

## 16. ModRuntime and ModInstance boundary

The runtime should separate the idea of a container from the idea of an executable or loaded instance.

```text
ModContainer
  ├── descriptor
  ├── state
  ├── context
  └── runtime reference

ModRuntime
  ├── ClassLoader
  ├── ModInstance
  ├── lifecycle adapter
  └── runtime state

ModInstance
  ├── mod reference
  ├── container reference
  └── runtime binding
```

This keeps discovery, validation, registry, and runtime execution separate.

## 17. Entrypoint contract and lifecycle adapter

The `entrypoint` field remains a contract rather than an execution implementation in this phase.

Future contract:

```text
entrypoint
  ↓
resolve class in mod classloader
  ↓
verify implements Mod
  ↓
construct public no-arg instance or factory
  ↓
bind to ModLifecycleAdapter
```

The lifecycle adapter is responsible for translating `ModContainer` state into actual execution of lifecycle methods without mixing runtime state logic into the mod itself.

## 18. Lifecycle ordering and failure handling

The runtime must always obey dependency order.

```text
A -> B

Initialization/start:
B
A

Stop:
A
B
```

Failure rules:

- a failed dependency prevents dependents from advancing
- unrelated mod states remain isolated
- `FAILED` on one mod does not mutate the entire loader
- shutdown must continue in reverse order to ensure resources are released deterministically

## 19. Minecraft compatibility contract

`minecraft-compat` provides the compatibility boundary between Fv2j3 and Minecraft 1.12.2.

Contract obligations:

- no `net.minecraft.*` leakage into `loader-api`
- no direct Forge API leakage into public mod contracts
- stable compatibility version string for Minecraft 1.12.2
- platform checks remain limited to environment detection, not classloading

This preserves a clean layer between mod code and the game runtime.

## 20. Forge / LaunchWrapper / ASM analysis

These technologies are not needed for the design-only stage.

They are only relevant if a future stage requires actual Minecraft/Forge bootstrap integration. At that point, the question should be answered as a deliberate runtime architecture decision rather than a hidden implementation detail.

### Decision summary

- Forge is not required for the current project architecture
- LaunchWrapper is not required for the current design-only stage
- ASM is not required unless a future runtime needs bytecode transformation
- Fv2j3 can remain an independent loader while Minecraft 1.12.2 remains the target runtime

## 21. Java 26 strategy

The project remains fixed to Java 26.

That means:

- toolchain remains Java 26
- source compatibility remains Java 26
- runtime assumptions are documented rather than hidden by compatibility hacks
- any legacy Minecraft/Forge constraints remain a compatibility issue to be solved at the integration boundary, not by downgrading the project target

## 22. Security and sandboxing

ClassLoader isolation is not a security sandbox.

It helps with:

- class identity isolation
- dependency visibility
- runtime cleanup

It does not solve:

- filesystem restrictions
- network restrictions
- JVM-level privilege boundaries

Mod code must be treated as regular JVM code with the runtime’s own trust model; security must be addressed separately if required.

## 23. Resource lifecycle

A mod runtime must explicitly own its resources.

Required cleanup:

- classloader release
- jar or directory handle release
- any runtime/workspace resource close
- instance disposal after stop or failure

No runtime stage may leave a live classloader or open resource behind after `STOPPED` or `FAILED`.

## 24. Current non-implemented scope

This project phase intentionally does not implement:

- real `ClassLoader`
- URLClassLoader or custom loader logic
- `defineClass`/`loadClass`
- reflection loading of mod entrypoints
- lifecycle execution for real mod code
- Minecraft injection
- ASM / Mixin / transformers
- Forge bootstrap integration

## 25. Status

The current runtime architecture remains intentionally conservative and is designed to support the next implementation stage without prematurely executing mod code or modifying Minecraft itself.

### Phase 8 contract lock summary

The current Phase 8 work locks the following contracts without creating a real mod runtime:

- `ModClassLoaderProvider` is a provider contract, not a loader implementation
- `ModRuntime` is a stateful contract with explicit transition validation and terminal cleanup semantics
- `ModContainer` owns runtime state and close policy metadata
- `ModInstance` is a logical instance boundary, not a reflective runtime object
- `ModLifecycleAdapter` defines lifecycle ordering and failure escalation without invoking real mod code
- `DependencyVisibility` and `PackageOwnership` define visibility rules independent of ad hoc class loading
- `ClassLoaderOwnership` and `RuntimeClosePolicy` define ownership and cleanup semantics for future loading stages

These contracts are sufficient for the next implementation stage while keeping all real Mod loading explicitly deferred.

### Forge

Forge is not required for the base runtime model. A loader can be designed independent of Forge, and the compatibility layer can treat Forge integration as a later integration target.

### LaunchWrapper

LaunchWrapper is historically associated with old Forge bootstrap and is not a prerequisite for a clean Fv2j3 runtime design. It should be treated as a compatibility option only when a real integration target requires it.

### ASM / Mixin / Coremod

These are transformation tools. They are not required for the current metadata, registry, and readiness architecture.

They should remain out of scope until the loader explicitly requires bytecode weaving for valid runtime purposes.

## 13. Java 26 compatibility risks

The architectural design must still respect Java 26 realities:

- modern JDKs have stricter runtime behavior for reflection, modules, and class visibility
- older Minecraft and Forge assumptions may fail under Java 26 if directly used without compatibility boundaries
- per-mod class loaders must not accidentally leak legacy assumptions into the API layer

The project is correct to keep `loader-api` and `loader-core` independent of game internals until a dedicated compatibility layer is introduced.

## 14. Security model

Fv2j3 should treat all mod metadata and code as untrusted runtime input.

ClassLoader isolation can reduce direct cross-mod interference, but it cannot provide a complete sandbox.

Future design must explicitly recognize:

- file system access
- network access
- reflection and introspection of loader internals
- resource poisoning
- malicious metadata and path traversal

Security is a runtime concern, not a metadata concern.

## 15. Resource lifecycle

Future runtime cleanup must include:

- closing all source streams
- releasing classloader references
- clearing container state on stop
- cleaning registry entries when shutdown is required
- ensuring mod resources are released before the loader is marked STOPPED

This is a necessary next step after readiness is established.

## 16. Error model

Future errors should be categorized as:

- metadata error
- discovery error
- dependency error
- class loading error
- entrypoint error
- lifecycle error
- minecraft compatibility error

The key rule is:

- a single mod failure should not silently corrupt the global runtime state
- loader-wide failures should be explicit and should move the loader to `FAILED`

## 17. Current non-implemented boundary

This document intentionally does not define the actual Mod class loading path.

The following are intentionally deferred:

- ClassLoader implementation
- URLClassLoader usage
- Mod class resolution
- `defineClass`
- real entrypoint invocation
- runtime lifecycle execution
- Forge integration
- Minecraft injection

## 18. Conclusion

The correct architecture for the next stage is a layered model with a stable API parent, a runtime core, and per-mod classloaders isolated by scope and dependency graph.

This design matches the project’s existing structure and keeps the public API clean.

It also respects the current rule that Fv2j3 is a Minecraft 1.12.2-only loader and that Minecraft-specific compatibility remains explicitly outside the API boundary.
