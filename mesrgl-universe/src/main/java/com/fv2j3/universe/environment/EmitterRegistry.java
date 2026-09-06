package com.fv2j3.universe.environment;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe registry of active environmental emitters (§63).
 * Environmental systems poll this for active emitters per chunk.
 */
public final class EmitterRegistry {

    private final ConcurrentHashMap<Long, EnvironmentalEmitter> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<EnvironmentalEmitter>> byChunk = new ConcurrentHashMap<>();

    public void register(EnvironmentalEmitter e) {
        Objects.requireNonNull(e, "emitter");
        if (byId.putIfAbsent(e.emitterId(), e) != null) {
            throw new IllegalStateException("duplicate emitter id: " + e.emitterId());
        }
        long ck = chunkKey(e.chunk());
        byChunk.computeIfAbsent(ck, k -> new CopyOnWriteArrayList<>()).add(e);
    }

    public boolean unregister(long id) {
        EnvironmentalEmitter removed = byId.remove(id);
        if (removed == null) return false;
        CopyOnWriteArrayList<EnvironmentalEmitter> list = byChunk.get(chunkKey(removed.chunk()));
        if (list != null) list.remove(removed);
        return true;
    }

    public List<EnvironmentalEmitter> emittersIn(com.fv2j3.universe.identifiers.ChunkCoord coord) {
        CopyOnWriteArrayList<EnvironmentalEmitter> list = byChunk.get(chunkKey(coord));
        if (list == null) return List.of();
        return list;
    }

    public int totalEmitters() { return byId.size(); }

    private static long chunkKey(com.fv2j3.universe.identifiers.ChunkCoord c) {
        // Pack the 3 int coordinates into a long (lossy but unique per chunk).
        return ((long) c.x() & 0x1FFFFF) << 42 | ((long) c.y() & 0x1FFFFF) << 21 | ((long) c.z() & 0x1FFFFF);
    }
}
