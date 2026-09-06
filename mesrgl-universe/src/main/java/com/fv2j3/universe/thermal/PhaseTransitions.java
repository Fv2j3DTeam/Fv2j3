package com.fv2j3.universe.thermal;

import com.fv2j3.universe.identifiers.ChunkCoord;
import com.fv2j3.universe.terrain.Chunk;
import com.fv2j3.universe.terrain.VoxelType;

/**
 * Phase transition rules (§44). Used after a thermal step to convert voxels
 * when their temperature crosses a threshold. Uses hysteresis to avoid
 * destructive oscillation around the threshold.
 */
public final class PhaseTransitions {

    public static final double WATER_MELT_K = 273.15;
    public static final double WATER_BOIL_K = 373.15;
    public static final double LAVA_SOLIDIFY_K = 973.15;

    /** Apply phase transitions to a chunk based on the thermal grid. */
    public void apply(ChunkCoord coord, Chunk chunk, ThermalField thermal) {
        if (chunk == null) return;
        for (int y = 0; y < Chunk.CHUNK_EDGE; y++) {
            for (int z = 0; z < Chunk.CHUNK_EDGE; z++) {
                for (int x = 0; x < Chunk.CHUNK_EDGE; x++) {
                    double T = thermal.temperatureAt(coord, x, y, z);
                    int v = chunk.voxel(x, y, z);
                    if (v == VoxelType.WATER) {
                        if (T < WATER_MELT_K - 1.0) chunk.setVoxel(x, y, z, VoxelType.ICE);
                        // Steam generation would spawn a steam smoke source here
                    } else if (v == VoxelType.ICE) {
                        if (T > WATER_MELT_K + 1.0) chunk.setVoxel(x, y, z, VoxelType.WATER);
                    } else if (v == VoxelType.LAVA) {
                        if (T < LAVA_SOLIDIFY_K - 5.0) chunk.setVoxel(x, y, z, VoxelType.OBSIDIAN);
                    } else if (v == VoxelType.SNOW) {
                        if (T > WATER_MELT_K + 1.0) chunk.setVoxel(x, y, z, VoxelType.AIR);
                    }
                }
            }
        }
    }
}
