package com.fv2j3.universe.weather;

import com.fv2j3.universe.identifiers.ChunkCoord;
import com.fv2j3.universe.terrain.Chunk;
import com.fv2j3.universe.terrain.VoxelType;
import com.fv2j3.universe.units.Units;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Precipitation system (§26). Manages falling droplets and pooling.
 * Droplets are NOT heavyweight objects; stored in a single pooled array.
 * Per-chunk droplet arrays are pooled / reused. Droplets collide with terrain
 * and produce surface accumulation (water or snow).
 */
public final class PrecipitationSystem {

    public static final class Droplet {
        public double x, y, z;
        public double vx, vy, vz;
        public boolean alive;
        public double mass; // kg
    }

    private static final int MAX_DROPLETS_PER_CHUNK = 1024;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<ChunkCoord, Droplet[]> droplets = new LinkedHashMap<>();
    private final Map<ChunkCoord, int[]> counts = new LinkedHashMap<>();
    private final AtomicLong emitted = new AtomicLong();
    private final AtomicLong collided = new AtomicLong();

    public long emit(ChunkCoord coord, double x, double y, double z,
                     double vx, double vy, double vz, double mass) {
        Objects.requireNonNull(coord, "coord");
        if (!Double.isFinite(x + y + z + vx + vy + vz + mass)) return 0L;
        lock.writeLock().lock();
        try {
            Droplet[] arr = droplets.computeIfAbsent(coord, k -> {
                Droplet[] a = new Droplet[MAX_DROPLETS_PER_CHUNK];
                for (int i = 0; i < a.length; i++) a[i] = new Droplet();
                return a;
            });
            int[] cnt = counts.computeIfAbsent(coord, k -> new int[]{0});
            for (int i = 0; i < arr.length; i++) {
                if (!arr[i].alive) {
                    arr[i].x = x; arr[i].y = y; arr[i].z = z;
                    arr[i].vx = vx; arr[i].vy = vy; arr[i].vz = vz;
                    arr[i].mass = mass;
                    arr[i].alive = true;
                    cnt[0]++;
                    emitted.incrementAndGet();
                    return 1L;
                }
            }
            return 0L; // pool full
        } finally { lock.writeLock().unlock(); }
    }

    public int dropletCount(ChunkCoord coord) {
        lock.readLock().lock();
        try {
            int[] c = counts.get(coord);
            return c == null ? 0 : c[0];
        } finally { lock.readLock().unlock(); }
    }

    /**
     * Step droplets: gravity + simple wind advection. Collision against solid
     * voxels within the chunk marks the droplet dead; rain accumulates as water
     * depth, snow as snow depth (handled by SnowSystem).
     */
    public int step(ChunkCoord coord, double dt, double windVx, double windVz, Chunk chunk, double surfaceWater) {
        if (dt <= 0) return 0;
        Objects.requireNonNull(coord, "coord");
        Objects.requireNonNull(chunk, "chunk");
        lock.writeLock().lock();
        try {
            Droplet[] arr = droplets.get(coord);
            if (arr == null) return 0;
            int[] cnt = counts.get(coord);
            int collisions = 0;
            double g = 9.81;
            for (Droplet d : arr) {
                if (!d.alive) continue;
                d.vy -= g * dt;
                d.vx += (windVx - d.vx) * 0.1 * dt;
                d.vz += (windVz - d.vz) * 0.1 * dt;
                d.x += d.vx * dt;
                d.y += d.vy * dt;
                d.z += d.vz * dt;
                int cx = (int) Math.floor(d.x);
                int cy = (int) Math.floor(d.y);
                int cz = (int) Math.floor(d.z);
                if (cx < 0 || cx >= Chunk.CHUNK_EDGE || cy < 0 || cy >= Chunk.CHUNK_EDGE || cz < 0 || cz >= Chunk.CHUNK_EDGE) {
                    d.alive = false; cnt[0]--; continue;
                }
                if (VoxelType.isSolid(chunk.voxel(cx, cy, cz))) {
                    d.alive = false; cnt[0]--;
                    collisions++;
                }
            }
            collided.addAndGet(collisions);
            return collisions;
        } finally { lock.writeLock().unlock(); }
    }

    public void unload(ChunkCoord coord) {
        lock.writeLock().lock();
        try {
            droplets.remove(coord);
            counts.remove(coord);
        } finally { lock.writeLock().unlock(); }
    }

    public long totalEmitted() { return emitted.get(); }
    public long totalCollided() { return collided.get(); }

    public long residentBytes() {
        long bytes = 0;
        for (Droplet[] arr : droplets.values()) {
            bytes += (long) arr.length * 56L;
        }
        return bytes;
    }
}
