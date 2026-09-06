package com.fv2j3.universe.fluid;

import com.fv2j3.universe.identifiers.ChunkCoord;
import com.fv2j3.universe.terrain.Chunk;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Per-chunk fluid system (§36, §38, §78). Holds one {@link FluidGrid} per
 * fluid per chunk. Sleep-friendly: an idle chunk is removed from the map.
 */
public final class FluidSystem {

    private final FluidRegistry registry;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<ChunkCoord, Map<String, FluidGrid>> grids = new LinkedHashMap<>();

    public FluidSystem(FluidRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public FluidRegistry registry() { return registry; }

    public FluidGrid getOrCreate(ChunkCoord coord, String fluidId) {
        Objects.requireNonNull(coord, "coord");
        Objects.requireNonNull(fluidId, "fluidId");
        FluidDefinition def = registry.get(fluidId);
        if (def == null) throw new IllegalArgumentException("unknown fluid: " + fluidId);
        lock.writeLock().lock();
        try {
            return grids.computeIfAbsent(coord, k -> new LinkedHashMap<>())
                    .computeIfAbsent(fluidId, k -> new FluidGrid(def));
        } finally { lock.writeLock().unlock(); }
    }

    public void unload(ChunkCoord coord) {
        lock.writeLock().lock();
        try { grids.remove(coord); } finally { lock.writeLock().unlock(); }
    }

    public void unloadAll() {
        lock.writeLock().lock();
        try { grids.clear(); } finally { lock.writeLock().unlock(); }
    }

    public int activeChunks() {
        lock.readLock().lock();
        try { return grids.size(); } finally { lock.readLock().unlock(); }
    }

    /** Run a step on a chunk for all fluids. */
    public void step(ChunkCoord coord, double dt, double gravityMs2) {
        lock.readLock().lock();
        Map<String, FluidGrid> inner = grids.get(coord);
        if (inner == null) { lock.readLock().unlock(); return; }
        // Copy values to avoid holding lock during step (steps are independent)
        for (FluidGrid g : inner.values()) {
            g.step(dt, gravityMs2);
        }
        lock.readLock().unlock();
    }

    /** Apply host chunk solids into each grid (call after terrain edit). */
    public void applySolids(ChunkCoord coord, Chunk chunk) {
        lock.readLock().lock();
        Map<String, FluidGrid> inner = grids.get(coord);
        if (inner == null) { lock.readLock().unlock(); return; }
        for (FluidGrid g : inner.values()) g.importSolids(chunk);
        lock.readLock().unlock();
    }
}
