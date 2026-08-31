# Fv2j3 Mod Loader — GitHub Copilot Instructions

## 1. Project Overview

This repository contains **Fv2j3 Mod Loader**, a custom Minecraft mod loader.

The loader is designed specifically for:

* Minecraft **1.12.2**
* Java **26**
* Modern Java development practices
* A modular and extensible mod-loading architecture

The project is intentionally focused on Minecraft 1.12.2.

### Critical compatibility rule

**Fv2j3 only supports Minecraft 1.12.2.**

Do not design APIs, metadata, configuration systems, or compatibility layers for other Minecraft versions unless explicitly requested.

Do not add unnecessary version abstraction for Minecraft versions that Fv2j3 does not support.

---

## 2. Required Development Environment

Use:

* Java **26**
* The latest stable compatible versions of external libraries and Gradle plugins
* Modern Gradle practices
* Modern Java language features where appropriate

Do not deliberately downgrade dependencies or Java versions unless explicitly instructed.

When selecting a dependency version:

1. Prefer the latest stable release.
2. Verify that it is compatible with Java 26.
3. Verify that it is compatible with Minecraft 1.12.2 and the project's architecture.
4. Avoid obsolete versions unless required for Minecraft 1.12.2 compatibility.

---

## 3. Minecraft Version Scope

Fv2j3 targets **Minecraft 1.12.2 exclusively**.

Do not introduce:

* Minecraft 1.13+ compatibility
* Version-specific abstraction layers for unsupported versions
* Multi-version mappings
* `requiredMinecraftVersion`
* Metadata fields whose only purpose is supporting multiple Minecraft versions

The project should remain simple and focused on 1.12.2.

---

## 4. Mod Metadata

Mod metadata must not contain:

```text
requiredMinecraftVersion
```

Do not introduce this field into:

* Public APIs
* Metadata schemas
* Metadata parsers
* Mod descriptors
* Validation systems
* Documentation

The Minecraft version is already fixed by the loader itself.

If a metadata field is not necessary for the 1.12.2-only architecture, do not add it merely because another mod loader uses it.

---

## 5. General Engineering Principles

Prioritize:

1. Correctness
2. Maintainability
3. Simplicity
4. Clear architecture
5. API stability
6. Good error handling
7. Performance
8. Compatibility with Minecraft 1.12.2

Avoid unnecessary complexity.

Do not introduce abstractions merely because they might be useful in the future.

Do not implement speculative features.

Prefer the smallest clean implementation that satisfies the current requirements.

---

## 6. Repository Analysis Before Modification

Before making significant changes:

1. Inspect the repository structure.
2. Identify the relevant modules.
3. Read the existing implementation.
4. Identify existing APIs and architectural conventions.
5. Determine how the requested feature integrates with the current architecture.
6. Check existing tests.
7. Check Gradle configuration and dependencies.
8. Avoid duplicating functionality that already exists.

Do not assume that an implementation pattern from another mod loader is appropriate for Fv2j3.

The existing repository architecture takes priority over generic examples found online.

---

## 7. Preserve Existing Architecture

Do not rewrite existing systems unnecessarily.

When implementing a feature:

* Reuse existing abstractions where appropriate.
* Preserve public APIs unless changing them is explicitly required.
* Avoid unrelated refactoring.
* Avoid renaming classes, packages, methods, or fields without a reason.
* Do not change behavior unrelated to the requested task.

If an architectural change is genuinely necessary, explain why before performing a large refactor.

---

## 8. Public API vs Internal Implementation

Clearly distinguish between:

### Public API

Code intended for mods or external consumers.

Public APIs should be:

* Stable
* Simple
* Well documented
* Independent of unnecessary internal implementation details

### Internal implementation

Loader internals may change when necessary.

Do not expose internal implementation details through the public API merely for convenience.

Do not make internal classes public unless there is a concrete API requirement.

---

## 9. Mod Loader Architecture

Keep the architecture modular.

Potential responsibilities should remain separated where appropriate, including:

* Mod discovery
* Mod metadata
* Mod dependency resolution
* Class loading
* Mod lifecycle
* Event systems
* Resource loading
* Logging
* Error handling
* Runtime integration

Do not merge unrelated responsibilities into a single class.

Avoid creating extremely large manager classes.

---

## 10. Dependency Handling

Dependencies between mods must be represented explicitly.

When implementing dependency resolution:

* Detect missing dependencies.
* Detect invalid dependency declarations.
* Detect dependency conflicts.
* Detect circular dependencies where applicable.
* Produce useful error messages.
* Load mods in an order consistent with their dependencies.

Do not silently ignore dependency errors.

Do not invent dependency semantics without documenting them.

---

## 11. Error Handling

Errors during mod loading should provide useful diagnostic information.

Whenever possible, include:

* Mod ID
* Relevant class or component
* Operation being performed
* Original exception
* Clear explanation of the failure

Do not swallow exceptions silently.

Avoid:

```java
catch (Exception ignored) {
}
```

unless there is a deliberate and documented reason.

---

## 12. Logging

Use the project's existing logging system.

Do not introduce a second logging framework unless explicitly required.

Logs should be useful for diagnosing:

* Mod discovery
* Mod loading
* Dependency resolution
* Class loading
* Lifecycle events
* Initialization failures

Avoid excessive debug logging in normal operation.

---

## 13. Performance

Minecraft startup and runtime performance matter.

Avoid:

* Unnecessary reflection
* Repeated classpath scanning
* Repeated metadata parsing
* Unbounded caches
* Expensive operations inside frequently executed Minecraft code

Perform expensive work during initialization when possible.

Do not prematurely optimize code at the expense of clarity.

---

## 14. Thread Safety

Do not assume loader code is thread-safe.

Before introducing asynchronous behavior:

1. Determine whether the existing system is single-threaded.
2. Identify shared mutable state.
3. Determine ownership of that state.
4. Add synchronization or concurrency primitives only when necessary.

Do not introduce asynchronous loading simply to make startup appear more modern.

---

## 15. Java 26

Use Java 26 features when they improve the implementation.

Prefer modern Java APIs over obsolete patterns.

However, do not use new language features merely for novelty.

Code should remain understandable.

Prefer:

* `record` where immutable data carriers are appropriate
* Pattern matching where it improves clarity
* Modern collection APIs
* `Optional` where appropriate
* Try-with-resources
* Strong typing
* Immutable data where practical

Avoid unnecessary cleverness.

---

## 16. Build System

Use Gradle according to the existing project structure.

Before modifying Gradle configuration:

1. Inspect `../settings.gradle` / `settings.gradle.kts`.
2. Inspect root build configuration.
3. Inspect module build files.
4. Understand existing repositories.
5. Understand existing dependency versions.

Do not replace the build system or restructure modules unless explicitly requested.

Keep dependency declarations centralized where the existing architecture supports it.

---

## 17. Testing

After modifying code:

1. Compile the affected module.
2. Run relevant tests.
3. Run broader tests when practical.
4. Check for warnings or obvious regressions.
5. Verify generated artifacts when relevant.

Do not claim a change works if it has not been validated.

If the environment prevents testing, explicitly state what could not be tested.

---

## 18. Documentation

When introducing a public API:

* Document its purpose.
* Document important parameters.
* Document return values.
* Document exceptions where relevant.
* Explain lifecycle requirements when relevant.

Documentation should describe the actual Fv2j3 behavior.

Do not copy documentation from other mod loaders without adapting it.

---

## 19. Code Style

Follow the existing repository style.

Prefer:

* Clear names
* Small focused methods
* Small focused classes
* Explicit control flow
* Minimal duplication
* Useful comments

Avoid comments that merely restate the code.

Bad:

```java
// Increment i
i++;
```

Good comments should explain:

* Why something is done
* An important compatibility constraint
* A non-obvious implementation decision
* A Minecraft 1.12.2-specific behavior

---

## 20. Changes Must Be Scoped

Only modify files that are relevant to the current task.

Do not:

* Reformat unrelated files.
* Rename unrelated classes.
* Upgrade unrelated dependencies.
* Rewrite unrelated code.
* Fix unrelated warnings unless explicitly requested.

A clean and reviewable diff is preferred.

---

# 21. Phased Development Rule

Large tasks must be divided into explicit phases.

For each phase:

1. Inspect the current implementation.
2. Explain the intended changes briefly.
3. Implement only that phase.
4. Build/test the affected code.
5. Summarize what changed.
6. STOP.

### Extremely important

**After completing a requested phase, do not automatically continue to the next phase.**

Wait for the user to explicitly request the next phase.

Do not assume that completing one phase authorizes implementation of future phases.

---

# 22. User Instructions Have Priority

When the user specifies:

* A particular architecture
* A particular file
* A particular API
* A particular implementation strategy
* A particular development phase

follow those instructions unless they conflict with a fundamental technical limitation.

If a requested approach appears problematic:

1. Explain the problem.
2. Suggest an alternative.
3. Do not silently replace the requested design.

---

# 23. Before Large Changes

For large architectural changes, first provide:

### Current understanding

Briefly explain how the current repository works.

### Proposed change

Explain which components will change.

### Files affected

List the files expected to change.

### Risks

Mention compatibility or architectural risks.

Then wait for approval if the requested task did not already clearly authorize the implementation.

---

# 24. Do Not Invent Existing Code

Never assume a class, method, package, configuration field, or API exists.

Before referencing an existing component:

* Search the repository.
* Confirm its name.
* Confirm its signature.
* Confirm its behavior.

If something does not exist, say so instead of pretending it exists.

---

# 25. Do Not Follow Outdated Minecraft Loader Patterns Blindly

Minecraft 1.12.2 has a very different environment from modern Minecraft versions.

Do not blindly copy architecture from:

* NeoForge
* Forge 1.20+
* Fabric
* Quilt
* Modern Minecraft mod loaders

Use them only as conceptual references when appropriate.

Fv2j3's architecture must be designed around its actual 1.12.2 runtime environment.

---

# 26. Final Verification Checklist

Before finishing a task, verify:

* [ ] Only requested functionality was changed.
* [ ] Minecraft 1.12.2 compatibility is preserved.
* [ ] No `requiredMinecraftVersion` was introduced.
* [ ] Java 26 compatibility is preserved.
* [ ] Existing public APIs were not unnecessarily changed.
* [ ] No unrelated files were modified.
* [ ] Relevant code compiles.
* [ ] Relevant tests pass when available.
* [ ] Errors are handled appropriately.
* [ ] Documentation is updated when necessary.
* [ ] The implementation follows the existing architecture.
* [ ] The current phase is complete.

**After this checklist is complete, STOP and wait for the next user instruction.**
