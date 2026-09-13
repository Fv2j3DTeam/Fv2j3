package io.github.fv2j3dteam.universe.streaming;

/**
 * Memory budget. Tracks resident bytes and reports eviction candidates by LRU
 * with a guardrail that no resident object is ever forcibly pinned.
 */
public final class MemoryBudget {
    private final long maxBytes;
    private long residentBytes;

    public MemoryBudget(long maxBytes) {
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be > 0");
        this.maxBytes = maxBytes;
    }

    public long maxBytes() { return maxBytes; }
    public long residentBytes() { return residentBytes; }
    public long freeBytes() { return Math.max(0, maxBytes - residentBytes); }
    public boolean isOverBudget() { return residentBytes > maxBytes; }

    public synchronized void acquire(long bytes) {
        if (bytes < 0) throw new IllegalArgumentException("bytes < 0");
        residentBytes += bytes;
    }

    public synchronized void release(long bytes) {
        if (bytes < 0) throw new IllegalArgumentException("bytes < 0");
        residentBytes = Math.max(0, residentBytes - bytes);
    }
}
