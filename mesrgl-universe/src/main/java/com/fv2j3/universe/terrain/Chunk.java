package com.fv2j3.universe.terrain;

import java.util.Arrays;
import java.util.Objects;

/**
 * Dense 3D voxel chunk. Edge size is fixed to {@code CHUNK_EDGE} (16) for this
 * implementation. Stores the voxel type, a heightmap (max y for solid), and
 * the surface height (highest non-air column per xz). Terrain streaming and
 * simulation both address chunks.
 */
public final class Chunk {
    public static final int CHUNK_EDGE = 16;
    public static final int VOXELS_PER_CHUNK = CHUNK_EDGE * CHUNK_EDGE * CHUNK_EDGE;

    private final byte[] voxels; // length = VOXELS_PER_CHUNK
    private final short[] surfaceY; // length = CHUNK_EDGE * CHUNK_EDGE (top non-air y per column)
    private final int chunkX, chunkY, chunkZ;

    public Chunk(int chunkX, int chunkY, int chunkZ) {
        this.chunkX = chunkX; this.chunkY = chunkY; this.chunkZ = chunkZ;
        this.voxels = new byte[VOXELS_PER_CHUNK];
        Arrays.fill(this.voxels, (byte) VoxelType.AIR);
        this.surfaceY = new short[CHUNK_EDGE * CHUNK_EDGE];
    }

    public int chunkX() { return chunkX; }
    public int chunkY() { return chunkY; }
    public int chunkZ() { return chunkZ; }
    public byte[] voxels() { return voxels; }
    public short[] surfaceY() { return surfaceY; }

    public int linearIndex(int x, int y, int z) {
        if (x < 0 || x >= CHUNK_EDGE || y < 0 || y >= CHUNK_EDGE || z < 0 || z >= CHUNK_EDGE) {
            throw new IndexOutOfBoundsException("voxel out of chunk: " + x + "," + y + "," + z);
        }
        return (y * CHUNK_EDGE + z) * CHUNK_EDGE + x;
    }

    public int voxel(int x, int y, int z) {
        return voxels[linearIndex(x, y, z)] & 0xFF;
    }

    public void setVoxel(int x, int y, int z, int type) {
        voxels[linearIndex(x, y, z)] = (byte) type;
    }

    public int surfaceY(int x, int z) {
        if (x < 0 || x >= CHUNK_EDGE || z < 0 || z >= CHUNK_EDGE) {
            throw new IndexOutOfBoundsException("xz out of chunk");
        }
        return surfaceY[z * CHUNK_EDGE + x] & 0xFFFF;
    }

    public void setSurfaceY(int x, int z, int y) {
        if (x < 0 || x >= CHUNK_EDGE || z < 0 || z >= CHUNK_EDGE) {
            throw new IndexOutOfBoundsException("xz out of chunk");
        }
        if (y < 0) y = 0; if (y > CHUNK_EDGE) y = CHUNK_EDGE;
        surfaceY[z * CHUNK_EDGE + x] = (short) y;
    }

    public boolean isSolid(int x, int y, int z) {
        return VoxelType.isSolid(voxel(x, y, z));
    }

    public boolean isAir(int x, int y, int z) {
        return voxel(x, y, z) == VoxelType.AIR;
    }

    public boolean isFluid(int x, int y, int z) {
        return VoxelType.isFluid(voxel(x, y, z));
    }

    /** Approximate resident byte size of this chunk in RAM. */
    public long residentBytes() {
        return VOXELS_PER_CHUNK + (long) surfaceY.length * 2L + 32L;
    }

    public void fillBox(int x0, int y0, int z0, int x1, int y1, int z1, int type) {
        for (int y = y0; y < y1; y++) {
            for (int z = z0; z < z1; z++) {
                for (int x = x0; x < x1; x++) {
                    if (x < 0 || x >= CHUNK_EDGE || y < 0 || y >= CHUNK_EDGE || z < 0 || z >= CHUNK_EDGE) continue;
                    setVoxel(x, y, z, type);
                }
            }
        }
    }

    public static long estimateBytes() {
        return VOXELS_PER_CHUNK + 16 * 16 * 2L + 32L;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Chunk c)) return false;
        return chunkX == c.chunkX && chunkY == c.chunkY && chunkZ == c.chunkZ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(chunkX, chunkY, chunkZ);
    }
}
