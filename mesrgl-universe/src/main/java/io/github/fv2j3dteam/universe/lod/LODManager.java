package io.github.fv2j3dteam.universe.lod;

import io.github.fv2j3dteam.universe.cache.CacheManager;
import io.github.fv2j3dteam.universe.streaming.RegionKey;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * LOD manager (§19, §59, §N). Records the current LOD per region. Distant
 * regions are at coarse LOD; near regions are at fine LOD. Sleep policy:
 * regions at GLOBAL_CLIMATE with no active consumers are sleep candidates.
 */
public final class LODManager {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<RegionKey, LOD> tiers = new LinkedHashMap<>();
    private final CacheManager cache;

    public LODManager(CacheManager cache) {
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    public void setLOD(RegionKey key, LOD lod) {
        lock.writeLock().lock();
        try { tiers.put(key, lod); } finally { lock.writeLock().unlock(); }
    }

    public LOD lodOf(RegionKey key) {
        lock.readLock().lock();
        try { return tiers.getOrDefault(key, LOD.GLOBAL_CLIMATE); } finally { lock.readLock().unlock(); }
    }

    public int countAtOrAbove(LOD min) {
        lock.readLock().lock();
        try {
            int c = 0;
            for (LOD l : tiers.values()) if (l.tier() >= min.tier()) c++;
            return c;
        } finally { lock.readLock().unlock(); }
    }

    public int sleepingCandidates() {
        // Sleeping = at GLOBAL_CLIMATE, not recently accessed, not pinned.
        lock.readLock().lock();
        try {
            int c = 0;
            for (RegionKey k : tiers.keySet()) {
                LOD l = tiers.get(k);
                if (l == LOD.GLOBAL_CLIMATE && cache.contains(k)) c++;
            }
            return c;
        } finally { lock.readLock().unlock(); }
    }
}
