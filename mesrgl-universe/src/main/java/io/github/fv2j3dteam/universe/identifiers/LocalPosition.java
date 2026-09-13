package io.github.fv2j3dteam.universe.identifiers;

import java.util.Objects;

/**
 * Hierarchical global position combining chunk coords with an in-chunk floating offset.
 * The floating offset is local to the chunk; origin shifts are applied by translating
 * chunk coords and resetting the local offset.
 */
public final class LocalPosition implements Comparable<LocalPosition> {
    private final ChunkCoord chunk;
    private final double localX;
    private final double localY;
    private final double localZ;

    public LocalPosition(ChunkCoord chunk, double localX, double localY, double localZ) {
        this.chunk = Objects.requireNonNull(chunk, "chunk");
        if (!Double.isFinite(localX) || !Double.isFinite(localY) || !Double.isFinite(localZ)) {
            throw new IllegalArgumentException("local coords must be finite");
        }
        this.localX = localX;
        this.localY = localY;
        this.localZ = localZ;
    }

    public ChunkCoord chunk() { return chunk; }
    public double localX() { return localX; }
    public double localY() { return localY; }
    public double localZ() { return localZ; }

    public LocalPosition withLocal(double x, double y, double z) {
        return new LocalPosition(chunk, x, y, z);
    }

    @Override
    public int compareTo(LocalPosition o) {
        int c = chunk.compareTo(o.chunk);
        if (c != 0) return c;
        c = Double.compare(localX, o.localX); if (c != 0) return c;
        c = Double.compare(localY, o.localY); if (c != 0) return c;
        return Double.compare(localZ, o.localZ);
    }

    @Override
    public String toString() { return chunk + "+(" + localX + "," + localY + "," + localZ + ")"; }
}
