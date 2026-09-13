package io.github.fv2j3dteam.universe.diagnostics;

/**
 * Observable diagnostics. Thread-safe (§69). Exposed via {@code Universe.diagnostics()}.
 * Tracks counters, timings, queue depth, and active cell counts. Does not throw.
 */
public interface Diagnostics {
    /** Record one event of a named type. */
    void record(String name, long delta);

    /** Record a single event of a named type (count += 1). */
    void increment(String name);

    /** Record a duration sample. */
    void recordTiming(String name, long nanos);

    /** Snapshot all current counters and timings. */
    DiagnosticsSnapshot snapshot();

    /** Reset all counters and timings. */
    void reset();

    /** Convenience: read a counter by name (0 if missing). */
    long counter(String name);
}
