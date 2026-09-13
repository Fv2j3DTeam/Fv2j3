package io.github.fv2j3dteam.universe;

import io.github.fv2j3dteam.universe.cache.CacheManager;
import io.github.fv2j3dteam.universe.streaming.MemoryBudget;
import io.github.fv2j3dteam.universe.streaming.RegionKey;
import io.github.fv2j3dteam.universe.streaming.RegionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CacheManagerTest {

    @Test
    void putAndGet() {
        CacheManager cache = new CacheManager(new MemoryBudget(10_000));
        RegionKey k = new RegionKey(new io.github.fv2j3dteam.universe.identifiers.UniverseId(1, 0, 0, 0, 0, 0, 0), 0, 0, 0);
        cache.put(k, RegionState.LOADED, "hello", 100);
        assertEquals("hello", cache.get(k));
        cache.unpin(k);
    }

    @Test
    void evictionWhenOverBudget() {
        CacheManager cache = new CacheManager(new MemoryBudget(200));
        RegionKey k1 = new RegionKey(new io.github.fv2j3dteam.universe.identifiers.UniverseId(1, 0, 0, 0, 0, 0, 0), 0, 0, 0);
        RegionKey k2 = new RegionKey(new io.github.fv2j3dteam.universe.identifiers.UniverseId(1, 0, 0, 0, 0, 0, 0), 1, 0, 0);
        RegionKey k3 = new RegionKey(new io.github.fv2j3dteam.universe.identifiers.UniverseId(1, 0, 0, 0, 0, 0, 0), 2, 0, 0);
        cache.put(k1, RegionState.LOADED, "a", 100);
        cache.put(k2, RegionState.LOADED, "b", 100);
        cache.put(k3, RegionState.LOADED, "c", 100);
        // k1 should have been evicted (LRU, not pinned)
        assertFalse(cache.contains(k1));
        assertTrue(cache.contains(k2));
        assertTrue(cache.contains(k3));
        assertTrue(cache.evictions() >= 1);
    }

    @Test
    void pinnedNotEvicted() {
        CacheManager cache = new CacheManager(new MemoryBudget(200));
        RegionKey k1 = new RegionKey(new io.github.fv2j3dteam.universe.identifiers.UniverseId(1, 0, 0, 0, 0, 0, 0), 0, 0, 0);
        RegionKey k2 = new RegionKey(new io.github.fv2j3dteam.universe.identifiers.UniverseId(1, 0, 0, 0, 0, 0, 0), 1, 0, 0);
        cache.put(k1, RegionState.ACTIVE, "a", 100);
        cache.get(k1); // pin it
        cache.put(k2, RegionState.LOADED, "b", 100);
        // ACTIVE entries are skipped during LRU eviction; we keep both (over budget tolerated)
        assertTrue(cache.contains(k1));
    }

    @Test
    void clearFreesMemory() {
        CacheManager cache = new CacheManager(new MemoryBudget(10_000));
        RegionKey k = new RegionKey(new io.github.fv2j3dteam.universe.identifiers.UniverseId(1, 0, 0, 0, 0, 0, 0), 0, 0, 0);
        cache.put(k, RegionState.LOADED, "x", 1000);
        cache.clear();
        assertEquals(0, cache.size());
        assertEquals(0, cache.budget().residentBytes());
    }
}
