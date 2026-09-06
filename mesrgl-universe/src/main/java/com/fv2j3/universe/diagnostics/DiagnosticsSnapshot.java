package com.fv2j3.universe.diagnostics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable snapshot of {@link Diagnostics} state.
 */
public final class DiagnosticsSnapshot {
    private final Map<String, Long> counters;
    private final Map<String, Long> timingsCount;
    private final Map<String, Double> timingsAvgNanos;

    public DiagnosticsSnapshot(Map<String, Long> counters,
                               Map<String, Long> timingsCount,
                               Map<String, Double> timingsAvgNanos) {
        this.counters = Collections.unmodifiableMap(new LinkedHashMap<>(counters));
        this.timingsCount = Collections.unmodifiableMap(new LinkedHashMap<>(timingsCount));
        this.timingsAvgNanos = Collections.unmodifiableMap(new LinkedHashMap<>(timingsAvgNanos));
    }

    public Map<String, Long> counters() { return counters; }
    public Map<String, Long> timingsCount() { return timingsCount; }
    public Map<String, Double> timingsAvgNanos() { return timingsAvgNanos; }

    public long counter(String name) {
        Long v = counters.get(name);
        return v == null ? 0L : v;
    }

    public double avgNanos(String name) {
        Double v = timingsAvgNanos.get(name);
        return v == null ? 0.0 : v;
    }

    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("counters:\n");
        for (var e : counters.entrySet()) {
            sb.append("  ").append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
        }
        sb.append("timings (avg ns):\n");
        for (var e : timingsAvgNanos.entrySet()) {
            sb.append("  ").append(e.getKey()).append(" = ").append(String.format("%.1f", e.getValue()))
                    .append(" (n=").append(timingsCount.get(e.getKey())).append(")\n");
        }
        return sb.toString();
    }
}
