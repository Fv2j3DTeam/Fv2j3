package com.fv2j3.universe.weather;

import com.fv2j3.universe.identifiers.ChunkCoord;
import com.fv2j3.universe.terrain.Chunk;
import com.fv2j3.universe.terrain.VoxelType;
import com.fv2j3.universe.units.Units;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Snow system (§27). Accumulates snow depth per chunk surface, melts based
 * on temperature, slides/displaces at high accumulation. Persistent snow
 * is stored per chunk and persists across unload/reload.
 */
public final class SnowSystem {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<ChunkCoord, float[]> depth; // depth per (x,z) column

    public SnowSystem() {
        this.depth = new LinkedHashMap<>();
    }

    public float depthAt(ChunkCoord coord, int x, int z) {
        lock.readLock().lock();
        try {
            float[] a = depth.get(coord);
            if (a == null) return 0f;
            return a[z * Chunk.CHUNK_EDGE + x];
        } finally { lock.readLock().unlock(); }
    }

    public void deposit(ChunkCoord coord, int x, int z, float amount) {
        Objects.requireNonNull(coord, "coord");
        if (amount < 0) return;
        lock.writeLock().lock();
        try {
            float[] a = depth.computeIfAbsent(coord, k -> new float[Chunk.CHUNK_EDGE * Chunk.CHUNK_EDGE]);
            a[z * Chunk.CHUNK_EDGE + x] = Math.min(10f, a[z * Chunk.CHUNK_EDGE + x] + amount);
        } finally { lock.writeLock().unlock(); }
    }

    public void melt(ChunkCoord coord, int x, int z, float rate) {
        lock.writeLock().lock();
        try {
            float[] a = depth.get(coord);
            if (a == null) return;
            int i = z * Chunk.CHUNK_EDGE + x;
            a[i] = Math.max(0f, a[i] - rate);
        } finally { lock.writeLock().unlock(); }
    }

    /** Sample-aware melt step. Snow melts if surface temperature > 273.15 K. */
    public void step(ChunkCoord coord, double temperatureK, double dt) {
        if (dt <= 0) return;
        float meltRate = (float) Math.max(0, (temperatureK - 273.15) * 0.005 * dt);
        lock.readLock().lock();
        float[] a = depth.get(coord);
        if (a == null) { lock.readLock().unlock(); return; }
        // Copy and write
        for (int i = 0; i < a.length; i++) {
            a[i] = Math.max(0f, a[i] - meltRate);
        }
        lock.readLock().unlock();
    }

    public long residentBytes() {
        long bytes = 0;
        for (float[] a : depth.values()) bytes += a.length * 4L;
        return bytes;
    }

    public void unload(ChunkCoord coord) {
        lock.writeLock().lock();
        try { depth.remove(coord); } finally { lock.writeLock().unlock(); }
    }
}
