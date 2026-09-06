package com.fv2j3.universe.streaming;

import com.fv2j3.universe.identifiers.ChunkCoord;
import com.fv2j3.universe.identifiers.UniverseId;

import java.util.Map;
import java.util.Objects;

/**
 * A region groups a small number of chunks for streaming and persistence.
 */
public record RegionKey(UniverseId id, int rx, int ry, int rz) {
    public RegionKey {
        Objects.requireNonNull(id, "id");
    }

    public ChunkCoord toChunkCoord() {
        return new ChunkCoord(rx, ry, rz);
    }

    public static RegionKey of(UniverseId id, int rx, int ry, int rz) {
        return new RegionKey(id, rx, ry, rz);
    }

    public String encode() {
        return id.encode() + "#" + rx + "/" + ry + "/" + rz;
    }

    public static RegionKey decode(String encoded) {
        int hashIdx = encoded.indexOf('#');
        if (hashIdx < 0) throw new IllegalArgumentException("invalid region key");
        UniverseId uid = UniverseId.decode(encoded.substring(0, hashIdx));
        String[] parts = encoded.substring(hashIdx + 1).split("/");
        if (parts.length != 3) throw new IllegalArgumentException("invalid region coords");
        return new RegionKey(uid, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }
}