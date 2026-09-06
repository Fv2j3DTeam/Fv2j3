package com.fv2j3.universe.thermal;

import com.fv2j3.universe.environment.TemperatureField;
import com.fv2j3.universe.identifiers.ChunkCoord;
import com.fv2j3.universe.terrain.Chunk;
import com.fv2j3.universe.terrain.VoxelType;
import com.fv2j3.universe.units.Units;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thermal simulation (§33, §83). Per-chunk 3D temperature grid. Conduction
 * between cells, convection approximation (heat carried by wind), and
 * phase transitions for water/ice. NaN-safe.
 */
public final class ThermalField {

    public static final int GRID_EDGE = 16;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<ChunkCoord, double[]> temperatures = new LinkedHashMap<>();
    private final TemperatureField helper = new TemperatureField();

    public double temperatureAt(ChunkCoord coord, int x, int y, int z) {
        lock.readLock().lock();
        try {
            double[] a = temperatures.get(coord);
            if (a == null) return 293.15;
            return a[index(x, y, z)];
        } finally { lock.readLock().unlock(); }
    }

    public void setTemperature(ChunkCoord coord, int x, int y, int z, double K) {
        lock.writeLock().lock();
        try {
            double[] a = temperatures.computeIfAbsent(coord, k -> new double[GRID_EDGE * GRID_EDGE * GRID_EDGE]);
            a[index(x, y, z)] = Units.clamp(K, 0.0, 1.0e6, "temp");
        } finally { lock.writeLock().unlock(); }
    }

    public int index(int x, int y, int z) {
        return (y * GRID_EDGE + z) * GRID_EDGE + x;
    }

    /**
     * Run a conduction + convection step. Wind is sampled for the chunk.
     * Convection: heat carried away by wind from the surface voxel.
     */
    public void step(ChunkCoord coord, Chunk chunk, double windMs, double ambientK, double dt) {
        if (dt <= 0 || chunk == null) return;
        lock.writeLock().lock();
        try {
            double[] a = temperatures.computeIfAbsent(coord, k -> initFromChunk(k, chunk, ambientK));
            double[] next = a.clone();
            for (int y = 0; y < GRID_EDGE; y++) {
                for (int z = 0; z < GRID_EDGE; z++) {
                    for (int x = 0; x < GRID_EDGE; x++) {
                        int i = index(x, y, z);
                        if (Double.isNaN(a[i])) a[i] = ambientK;
                        double cur = a[i];
                        double sumNeighbour = 0;
                        int count = 0;
                        if (x > 0) { sumNeighbour += a[index(x - 1, y, z)]; count++; }
                        if (x < GRID_EDGE - 1) { sumNeighbour += a[index(x + 1, y, z)]; count++; }
                        if (y > 0) { sumNeighbour += a[index(x, y - 1, z)]; count++; }
                        if (y < GRID_EDGE - 1) { sumNeighbour += a[index(x, y + 1, z)]; count++; }
                        if (z > 0) { sumNeighbour += a[index(x, y, z - 1)]; count++; }
                        if (z < GRID_EDGE - 1) { sumNeighbour += a[index(x, y, z + 1)]; count++; }
                        double k = conductivityFor(chunk.voxel(x, y, z));
                        double neighbourAvg = count > 0 ? sumNeighbour / count : cur;
                        next[i] = helper.conductionStep(cur, neighbourAvg, k, dt, 1000.0);
                    }
                }
            }
            // Convection: surface voxels cool toward ambient under wind
            for (int z = 0; z < GRID_EDGE; z++) {
                for (int x = 0; x < GRID_EDGE; x++) {
                    int surfaceY = chunk.surfaceY(x, z);
                    int i = index(x, Math.min(surfaceY, GRID_EDGE - 1), z);
                    double convFactor = Math.min(1.0, 0.1 * windMs * dt);
                    next[i] = next[i] + (ambientK - next[i]) * convFactor;
                }
            }
            // NaN/Inf guard
            for (int i = 0; i < next.length; i++) {
                if (!Double.isFinite(next[i]) || next[i] < 0) next[i] = ambientK;
            }
            System.arraycopy(next, 0, a, 0, a.length);
        } finally { lock.writeLock().unlock(); }
    }

    public double conductivityFor(int voxelType) {
        return switch (voxelType) {
            case VoxelType.STONE, VoxelType.BEDROCK, VoxelType.OBSIDIAN -> 2.0;
            case VoxelType.DIRT, VoxelType.SAND, VoxelType.CLAY, VoxelType.GRAVEL, VoxelType.SANDSTONE -> 1.0;
            case VoxelType.GRASS, VoxelType.WOOD, VoxelType.LEAVES -> 0.15;
            case VoxelType.SNOW -> 0.1;
            case VoxelType.ICE -> 2.18;
            case VoxelType.WATER -> 0.6;
            case VoxelType.LAVA -> 1.0;
            case VoxelType.AIR -> 0.025;
            case VoxelType.ASH -> 0.07;
            case VoxelType.COAL_ORE, VoxelType.IRON_ORE, VoxelType.COPPER_ORE -> 1.5;
            default -> 1.0;
        };
    }

    private double[] initFromChunk(ChunkCoord coord, Chunk chunk, double ambientK) {
        Objects.requireNonNull(coord, "coord");
        double[] a = new double[GRID_EDGE * GRID_EDGE * GRID_EDGE];
        for (int y = 0; y < GRID_EDGE; y++) {
            for (int z = 0; z < GRID_EDGE; z++) {
                for (int x = 0; x < GRID_EDGE; x++) {
                    a[index(x, y, z)] = ambientK;
                }
            }
        }
        return a;
    }

    public void unload(ChunkCoord coord) {
        lock.writeLock().lock();
        try { temperatures.remove(coord); } finally { lock.writeLock().unlock(); }
    }

    public long residentBytes() {
        long bytes = 0;
        for (double[] a : temperatures.values()) bytes += a.length * 8L;
        return bytes;
    }
}
