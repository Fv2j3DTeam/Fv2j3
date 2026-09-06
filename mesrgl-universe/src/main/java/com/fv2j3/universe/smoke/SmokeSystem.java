package com.fv2j3.universe.smoke;

import com.fv2j3.universe.identifiers.ChunkCoord;
import com.fv2j3.universe.terrain.Chunk;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Smoke system (§29, §30). Per-chunk volumetric grids plus shared sources.
 * Sources (fire, etc.) emit smoke into the grid; grid step runs advection /
 * diffusion / dissipation. LOD policy: distant chunks are evicted.
 */
public final class SmokeSystem {

    public static final class Source {
        public final long id;
        public final ChunkCoord chunk;
        public final int cellX, cellY, cellZ;
        public final double emissionPerSec;
        public final double temperatureK;
        public final double initVx, initVy, initVz;
        public final String gasId;
        public double remainingSeconds;

        public Source(long id, ChunkCoord chunk, int cellX, int cellY, int cellZ,
                      double emissionPerSec, double temperatureK,
                      double initVx, double initVy, double initVz, String gasId,
                      double durationSeconds) {
            this.id = id; this.chunk = chunk; this.cellX = cellX; this.cellY = cellY; this.cellZ = cellZ;
            this.emissionPerSec = emissionPerSec; this.temperatureK = temperatureK;
            this.initVx = initVx; this.initVy = initVy; this.initVz = initVz;
            this.gasId = gasId; this.remainingSeconds = durationSeconds;
        }
    }

    private final GasRegistry registry;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Object stepMutex = new Object();
    private final Map<ChunkCoord, Map<String, SmokeGrid>> grids = new LinkedHashMap<>();
    private final Map<Long, Source> sources = new LinkedHashMap<>();
    private final AtomicLong nextId = new AtomicLong();

    public SmokeSystem(GasRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public GasRegistry registry() { return registry; }

    public SmokeGrid getOrCreate(ChunkCoord coord, String gasId, double ambientTemperatureK) {
        Objects.requireNonNull(coord, "coord");
        Objects.requireNonNull(gasId, "gasId");
        GasDefinition def = registry.get(gasId);
        if (def == null) throw new IllegalArgumentException("unknown gas: " + gasId);
        lock.writeLock().lock();
        try {
            return grids.computeIfAbsent(coord, k -> new LinkedHashMap<>())
                    .computeIfAbsent(gasId, k -> new SmokeGrid(def, ambientTemperatureK));
        } finally { lock.writeLock().unlock(); }
    }

    public void unload(ChunkCoord coord) {
        lock.writeLock().lock();
        try {
            grids.remove(coord);
            sources.entrySet().removeIf(e -> e.getValue().chunk.equals(coord));
        } finally { lock.writeLock().unlock(); }
    }

    public long addSource(Source s) {
        Objects.requireNonNull(s, "source");
        long id = nextId.incrementAndGet();
        Source withId = new Source(id, s.chunk, s.cellX, s.cellY, s.cellZ,
                s.emissionPerSec, s.temperatureK, s.initVx, s.initVy, s.initVz, s.gasId, s.remainingSeconds);
        lock.writeLock().lock();
        try { sources.put(id, withId); } finally { lock.writeLock().unlock(); }
        return id;
    }

    public boolean removeSource(long id) {
        lock.writeLock().lock();
        try { return sources.remove(id) != null; } finally { lock.writeLock().unlock(); }
    }

    public void step(double dt) {
        if (dt <= 0) return;
        // Concurrent step() calls would double-emit and double-decrement the
        // same source, so steps are serialized.
        synchronized (stepMutex) {
            // Snapshot the sources under the read lock. getOrCreate below
            // acquires the write lock, which must never happen while this
            // thread holds the read lock: a read→write upgrade self-deadlocks
            // (the writer waits for the very reader that blocks it).
            java.util.List<Source> active = new java.util.ArrayList<>();
            lock.readLock().lock();
            try {
                active.addAll(sources.values());
            } finally {
                lock.readLock().unlock();
            }

            java.util.List<Source> toRemove = new java.util.ArrayList<>();
            for (Source s : active) {
                s.remainingSeconds -= dt;
                if (s.remainingSeconds <= 0) {
                    toRemove.add(s);
                    continue;
                }
                SmokeGrid g = getOrCreate(s.chunk, s.gasId, 293.0);
                double emission = s.emissionPerSec * dt;
                g.addSmoke(s.cellX, s.cellY, s.cellZ, emission, s.temperatureK, s.initVx, s.initVy, s.initVz);
            }
            for (Source s : toRemove) removeSource(s.id);

            // Snapshot the grids under the read lock and advance them outside
            // it: stepping mutates the grids, and mutating shared state under
            // only a read lock is a data race.
            java.util.List<SmokeGrid> toStep = new java.util.ArrayList<>();
            lock.readLock().lock();
            try {
                for (Map<String, SmokeGrid> inner : grids.values()) {
                    toStep.addAll(inner.values());
                }
            } finally {
                lock.readLock().unlock();
            }
            for (SmokeGrid g : toStep) g.step(dt);
        }
    }

    public int sourceCount() {
        lock.readLock().lock();
        try { return sources.size(); } finally { lock.readLock().unlock(); }
    }

    public int gridCount() {
        lock.readLock().lock();
        try { return grids.size(); } finally { lock.readLock().unlock(); }
    }

    public void applySolids(ChunkCoord coord, Chunk chunk) {
        lock.readLock().lock();
        Map<String, SmokeGrid> inner = grids.get(coord);
        if (inner == null) { lock.readLock().unlock(); return; }
        for (SmokeGrid g : inner.values()) g.importSolids(chunk);
        lock.readLock().unlock();
    }
}
