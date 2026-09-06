package com.fv2j3.universe.smoke;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Generalised gas system (§47). Wraps smoke and steam / volcanic gas / etc.
 * Each gas has a registry entry and a smoke-style grid.
 */
public final class GasSystem {

    private final GasRegistry registry;
    private final SmokeSystem smokeSystem;
    private final Map<String, Long> gasSourceCounts = new LinkedHashMap<>();

    public GasSystem(GasRegistry registry, SmokeSystem smokeSystem) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.smokeSystem = Objects.requireNonNull(smokeSystem, "smokeSystem");
    }

    public GasRegistry registry() { return registry; }
    public SmokeSystem smokeSystem() { return smokeSystem; }

    public long emit(SmokeSystem.Source source) {
        long id = smokeSystem.addSource(source);
        gasSourceCounts.merge(source.gasId, 1L, Long::sum);
        return id;
    }

    public boolean extinguish(long sourceId) {
        boolean removed = smokeSystem.removeSource(sourceId);
        if (removed) {
            // The count will re-sync on next snapshot call.
        }
        return removed;
    }
}
