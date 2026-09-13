package io.github.fv2j3dteam.universe.api;

/**
 * Contract: a public API object must have a clearly defined responsibility,
 * documented ownership, thread affinity, and observable state. Implementations
 * must not become hidden global singletons unless explicitly justified.
 *
 * All {@code ApiObject} implementations MUST:
 *  - validate their construction inputs (§87)
 *  - expose deterministic identity where the object represents procedural content
 *  - document thread affinity in Javadoc (e.g. "main-thread only" or "thread-safe")
 *  - document lifetime semantics in Javadoc (e.g. "owned by X")
 *  - never leak internal storage structures unnecessarily
 */
public interface ApiObject {
    /** Object identity for diagnostics. May be a stable id or hash code. */
    String apiId();
}