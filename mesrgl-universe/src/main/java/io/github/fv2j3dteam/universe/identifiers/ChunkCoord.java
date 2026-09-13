package io.github.fv2j3dteam.universe.identifiers;

import java.util.Objects;

/**
 * Local chunk-local integer coordinate. Stable across saves and origin shifts.
 */
public record ChunkCoord(long x, long y, long z) implements Comparable<ChunkCoord> {
    public ChunkCoord {
        // long coordinates are always exact, but guard against MIN/MAX wraparound in callers.
        if (x == Long.MIN_VALUE || y == Long.MIN_VALUE || z == Long.MIN_VALUE) {
            throw new IllegalArgumentException("invalid chunk coord");
        }
    }

    @Override
    public int compareTo(ChunkCoord o) {
        int c = Long.compare(x, o.x); if (c != 0) return c;
        c = Long.compare(y, o.y); if (c != 0) return c;
        return Long.compare(z, o.z);
    }

    public long manhattanDistance(ChunkCoord other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y) + Math.abs(z - other.z);
    }

    public long chebyshevDistance(ChunkCoord other) {
        return Math.max(Math.abs(x - other.x), Math.max(Math.abs(y - other.y), Math.abs(z - other.z)));
    }

    @Override
    public String toString() { return "Chunk(" + x + "," + y + "," + z + ")"; }
}
