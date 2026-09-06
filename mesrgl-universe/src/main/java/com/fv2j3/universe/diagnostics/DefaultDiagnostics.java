package com.fv2j3.universe.diagnostics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe diagnostics implementation using LongAdder counters. Does not allocate
 * per-event (counters use LongAdder) and stores aggregate timing statistics per name.
 */
public final class DefaultDiagnostics implements Diagnostics {
    private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> timingCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> timingTotal = new ConcurrentHashMap<>();

    @Override
    public void record(String name, long delta) {
        if (name == null) return;
        counters.computeIfAbsent(name, k -> new LongAdder()).add(delta);
    }

    @Override
    public void increment(String name) {
        if (name == null) return;
        counters.computeIfAbsent(name, k -> new LongAdder()).increment();
    }

    @Override
    public void recordTiming(String name, long nanos) {
        if (name == null || nanos < 0) return;
        timingCount.computeIfAbsent(name, k -> new LongAdder()).increment();
        timingTotal.computeIfAbsent(name, k -> new LongAdder()).add(nanos);
    }

    @Override
    public DiagnosticsSnapshot snapshot() {
        Map<String, Long> counterSnap = new LinkedHashMap<>();
        for (var e : counters.entrySet()) counterSnap.put(e.getKey(), e.getValue().sum());
        Map<String, Long> countSnap = new LinkedHashMap<>();
        Map<String, Double> avgSnap = new LinkedHashMap<>();
        for (var e : timingCount.entrySet()) {
            long n = e.getValue().sum();
            countSnap.put(e.getKey(), n);
            long total = timingTotal.getOrDefault(e.getKey(), new LongAdder()).sum();
            avgSnap.put(e.getKey(), n == 0 ? 0.0 : (double) total / (double) n);
        }
        return new DiagnosticsSnapshot(counterSnap, countSnap, avgSnap);
    }

    @Override
    public void reset() {
        counters.clear();
        timingCount.clear();
        timingTotal.clear();
    }

    @Override
    public long counter(String name) {
        LongAdder a = counters.get(name);
        return a == null ? 0L : a.sum();
    }
}
