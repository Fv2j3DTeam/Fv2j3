package com.fv2j3.universe.cache;

import com.fv2j3.universe.streaming.MemoryBudget;
import com.fv2j3.universe.streaming.RegionKey;
import com.fv2j3.universe.streaming.RegionState;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * LRU + memory-budgeted cache of resident region contents (§20).
 *
 * Eviction order: LRU among LOADED/INACTIVE regions (ACTIVE regions are pinned).
 * When the budget is exceeded, evict LRU non-active regions until under budget.
 *
 * Thread-safe. Each entry exposes its approximate resident byte size.
 */
public final class CacheManager {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    /** Insertion-order map (LRU via accessOrder=true). */
    private final LinkedHashMap<RegionKey, Entry> entries = new LinkedHashMap<>();
    private final MemoryBudget budget;
    private long evictions;
    private long bytesFreed;
    private long insertions;

    public CacheManager(MemoryBudget budget) {
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    public MemoryBudget budget() { return budget; }

    public int size() { lock.readLock().lock(); try { return entries.size(); } finally { lock.readLock().unlock(); } }

    public boolean contains(RegionKey key) {
        lock.readLock().lock();
        try { return entries.containsKey(key); } finally { lock.readLock().unlock(); }
    }

    public Object get(RegionKey key) {
        lock.writeLock().lock();
        try {
            Entry e = entries.get(key);
            if (e == null) return null;
            e.lastAccess = System.nanoTime();
            e.pinCount++;
            return e.payload;
        } finally { lock.writeLock().unlock(); }
    }

    public void unpin(RegionKey key) {
        lock.writeLock().lock();
        try {
            Entry e = entries.get(key);
            if (e == null) return;
            if (e.pinCount > 0) e.pinCount--;
            e.lastAccess = System.nanoTime();
        } finally { lock.writeLock().unlock(); }
    }

    /**
     * Insert or replace an entry. Adds to resident memory budget. May trigger
     * eviction of older entries if the budget is exceeded.
     */
    public void put(RegionKey key, RegionState state, Object payload, long bytes) {
        lock.writeLock().lock();
        try {
            Entry existing = entries.remove(key);
            if (existing != null) {
                budget.release(existing.bytes);
            }
            if (bytes < 0) bytes = 0;
            budget.acquire(bytes);
            entries.put(key, new Entry(key, state, payload, bytes, System.nanoTime()));
            insertions++;
            if (budget.isOverBudget()) {
                evictUntilUnder();
            }
        } finally { lock.writeLock().unlock(); }
    }

    public void remove(RegionKey key) {
        lock.writeLock().lock();
        try {
            Entry e = entries.remove(key);
            if (e != null) {
                budget.release(e.bytes);
                bytesFreed += e.bytes;
            }
        } finally { lock.writeLock().unlock(); }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            long total = 0;
            for (Entry e : entries.values()) total += e.bytes;
            entries.clear();
            budget.release(total);
            bytesFreed += total;
        } finally { lock.writeLock().unlock(); }
    }

    private void evictUntilUnder() {
        // LRU: oldest first; do not evict pinned entries.
        Iterator<Map.Entry<RegionKey, Entry>> it = entries.entrySet().iterator();
        while (budget.isOverBudget() && it.hasNext()) {
            Map.Entry<RegionKey, Entry> me = it.next();
            Entry e = me.getValue();
            if (e.pinCount > 0) continue;
            if (e.state == RegionState.ACTIVE) continue; // never evict active without explicit unload
            it.remove();
            budget.release(e.bytes);
            bytesFreed += e.bytes;
            evictions++;
        }
    }

    public long evictions() { lock.readLock().lock(); try { return evictions; } finally { lock.readLock().unlock(); } }
    public long bytesFreed() { lock.readLock().lock(); try { return bytesFreed; } finally { lock.readLock().unlock(); } }
    public long insertions() { lock.readLock().lock(); try { return insertions; } finally { lock.readLock().unlock(); } }

    /** Test/audit hook: returns the current LRU order. */
    public java.util.List<RegionKey> lruOrder() {
        lock.readLock().lock();
        try { return new java.util.ArrayList<>(entries.keySet()); } finally { lock.readLock().unlock(); }
    }

    private static final class Entry {
        final RegionKey key;
        RegionState state;
        Object payload;
        long bytes;
        long lastAccess;
        int pinCount;

        Entry(RegionKey key, RegionState state, Object payload, long bytes, long lastAccess) {
            this.key = key; this.state = state; this.payload = payload;
            this.bytes = bytes; this.lastAccess = lastAccess;
        }
    }
}
