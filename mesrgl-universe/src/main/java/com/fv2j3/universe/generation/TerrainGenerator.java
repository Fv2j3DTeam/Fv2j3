package com.fv2j3.universe.generation;

import com.fv2j3.universe.identifiers.ChunkCoord;
import com.fv2j3.universe.identifiers.UniverseId;
import com.fv2j3.universe.math.NoiseComposition;
import com.fv2j3.universe.math.SimplexNoise;
import com.fv2j3.universe.seeds.SeedDerivation;
import com.fv2j3.universe.terrain.Chunk;
import com.fv2j3.universe.terrain.VoxelType;
import com.fv2j3.universe.universe.Planet;

/**
 * Terrain generator (§11). Hierarchical: produces a chunk's worth of voxels
 * using a fractal noise heightmap with biome-dependent material selection.
 * Deterministic and independent of generation order.
 */
public final class TerrainGenerator {

    public static final String STAGE = "terrain";

    public Chunk generate(Planet planet, ChunkCoord coord) {
        long seed = SeedDerivation.deriveSubSeed(planet.seed(), coord.x() * 73856093L ^ coord.y() * 19349663L ^ coord.z() * 83492791L);
        SimplexNoise noise = new SimplexNoise(seed);
        SimplexNoise material = new SimplexNoise(seed ^ 0x9E3779B97F4A7C15L);
        Chunk chunk = new Chunk((int) coord.x(), (int) coord.y(), (int) coord.z());

        // Generate heightmap + biome materials for each xz column.
        for (int z = 0; z < Chunk.CHUNK_EDGE; z++) {
            for (int x = 0; x < Chunk.CHUNK_EDGE; x++) {
                int wx = (int) coord.x() * Chunk.CHUNK_EDGE + x;
                int wz = (int) coord.z() * Chunk.CHUNK_EDGE + z;
                double h = heightAt(noise, wx, wz, planet);
                int surface = clampSurface(h);
                chunk.setSurfaceY(x, z, surface);
                // Fill column
                for (int y = 0; y < Chunk.CHUNK_EDGE; y++) {
                    int wy = (int) coord.y() * Chunk.CHUNK_EDGE + y;
                    if (wy < surface) {
                        int type = materialAt(material, wx, wy, wz, surface, planet);
                        chunk.setVoxel(x, y, z, type);
                    } else if (wy < Chunk.CHUNK_EDGE && planet.oceanCoverageFraction() > 0.3 && wy < oceanLevel(planet)) {
                        chunk.setVoxel(x, y, z, VoxelType.WATER);
                    } else {
                        chunk.setVoxel(x, y, z, VoxelType.AIR);
                    }
                }
            }
        }
        return chunk;
    }

    public double heightAt(SimplexNoise noise, int wx, int wz, Planet planet) {
        double f = noise.fractal2D(wx * 0.01, wz * 0.01, 5, 2.0, 0.5);
        double ridged = NoiseComposition.ridged2D(noise, wx * 0.005, wz * 0.005, 4, 2.0, 0.5);
        double archMod = switch (planet.archetype()) {
            case EARTH_LIKE, OCEAN_WORLD -> 0.0;
            case DESERT -> 0.05;
            case FROZEN, OCEAN_FROZEN, TUNDRA, TAIGA -> 0.0;
            case VOLCANIC, LAVA -> 0.2;
            case ROCKY_BARREN -> 0.0;
            case GAS_GIANT, ICE_GIANT -> 0.0;
            default -> 0.0;
        };
        return (f * 0.7 + ridged * 0.5) + archMod;
    }

    private int oceanLevel(Planet planet) {
        if (planet.oceanCoverageFraction() > 0.5) return 6;
        return 0;
    }

    public int materialAt(SimplexNoise material, int wx, int wy, int wz, int surface, Planet planet) {
        if (wy == 0) return VoxelType.BEDROCK;
        double m = material.noise2D(wx * 0.03, wz * 0.03);
        if (wy > surface - 2) {
            // Surface material
            return switch (planet.archetype()) {
                case DESERT -> VoxelType.SAND;
                case FROZEN, OCEAN_FROZEN, TUNDRA -> wy > surface - 1 ? VoxelType.SNOW : VoxelType.DIRT;
                case VOLCANIC, LAVA -> VoxelType.OBSIDIAN;
                case EARTH_LIKE, OCEAN_WORLD, SWAMP, SAVANNA, TAIGA -> VoxelType.GRASS;
                case ROCKY_BARREN -> VoxelType.STONE;
                case GAS_GIANT, ICE_GIANT -> VoxelType.ICE;
                default -> VoxelType.STONE;
            };
        }
        if (wy > surface - 5) {
            return m > 0.3 ? VoxelType.DIRT : VoxelType.STONE;
        }
        if (m > 0.6) return VoxelType.STONE;
        if (m > 0.4) return VoxelType.SANDSTONE;
        if (m < -0.4) return VoxelType.GRAVEL;
        if (m < -0.2) return VoxelType.CLAY;
        return VoxelType.STONE;
    }

    private int clampSurface(double h) {
        int s = (int) Math.round(Chunk.CHUNK_EDGE * 0.4 + h * Chunk.CHUNK_EDGE * 0.3);
        if (s < 0) s = 0;
        if (s > Chunk.CHUNK_EDGE) s = Chunk.CHUNK_EDGE;
        return s;
    }
}
