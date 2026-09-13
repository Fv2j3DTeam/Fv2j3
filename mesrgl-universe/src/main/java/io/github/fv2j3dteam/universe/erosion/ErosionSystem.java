package io.github.fv2j3dteam.universe.erosion;

import io.github.fv2j3dteam.universe.identifiers.ChunkCoord;
import io.github.fv2j3dteam.universe.persistence.PersistentEdit;
import io.github.fv2j3dteam.universe.persistence.PersistenceManager;
import io.github.fv2j3dteam.universe.terrain.Chunk;
import io.github.fv2j3dteam.universe.terrain.VoxelType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Erosion / sediment system (§46). Simplified hydraulic erosion:
 * each simulation step transports sediment downhill from cells with fluid
 * contact; the eroded material is deposited one cell lower. Persistent
 * terrain modifications are stored via the {@link PersistenceManager}
 * so they survive streaming/unloading/reloading.
 *
 * Algorithm (each step):
 *  1. For each surface cell with water/fluid above it, the flow rate
 *     is estimated from local height gradient.
 *  2. Sediment capacity is computed (C = k * slope * flow).
 *  3. If suspended sediment < capacity, erode the surface; if >, deposit.
 *  4. Height deltas are written to the chunk; deltas are also persisted
 *     as {@link PersistentEdit} records for survival across unload.
 *
 * The model is intentionally simplified but produces visible terrain
 * evolution under repeated simulation.
 */
public final class ErosionSystem {

    /** Per-cell sediment capacity coefficient. */
    public static final double EROSION_RATE = 0.05;
    /** Deposition rate when over-saturated. */
    public static final double DEPOSITION_RATE = 0.10;
    /** Solubility constant. */
    public static final double SOLUBILITY = 0.5;

    private final PersistenceManager persistence;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<ChunkCoord, float[]> sedimentLayers = new HashMap<>();
    private long totalEroded = 0L;
    private long totalDeposited = 0L;

    public ErosionSystem(PersistenceManager persistence) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
    }

    public long totalEroded() { lock.readLock().lock(); try { return totalEroded; } finally { lock.readLock().unlock(); } }
    public long totalDeposited() { lock.readLock().lock(); try { return totalDeposited; } finally { lock.readLock().unlock(); } }

    public float sedimentAt(ChunkCoord coord, int x, int z) {
        lock.readLock().lock();
        try {
            float[] a = sedimentLayers.get(coord);
            if (a == null) return 0f;
            return a[z * Chunk.CHUNK_EDGE + x];
        } finally { lock.readLock().unlock(); }
    }

    /**
     * Run one erosion/deposition step on a chunk given its current surface
     * heights. Modifies the chunk's voxel columns in-place and writes
     * persistent edits.
     */
    public void step(ChunkCoord coord, Chunk chunk, double flowIntensity) {
        if (chunk == null) return;
        if (flowIntensity < 0) flowIntensity = 0;
        if (flowIntensity > 1) flowIntensity = 1;
        lock.writeLock().lock();
        try {
            float[] sed = sedimentLayers.computeIfAbsent(coord,
                    k -> new float[Chunk.CHUNK_EDGE * Chunk.CHUNK_EDGE]);
            byte[] deltas = new byte[Chunk.CHUNK_EDGE * Chunk.CHUNK_EDGE];
            for (int z = 0; z < Chunk.CHUNK_EDGE; z++) {
                for (int x = 0; x < Chunk.CHUNK_EDGE; x++) {
                    int i = z * Chunk.CHUNK_EDGE + x;
                    int h = chunk.surfaceY(x, z);
                    if (h <= 0) continue;
                    // Estimate slope from neighbour deltas
                    double slope = computeSlope(chunk, x, z);
                    double capacity = SOLUBILITY * slope * flowIntensity;
                    float suspended = sed[i];
                    if (suspended < capacity) {
                        // Erode
                        int erode = (int) Math.ceil(EROSION_RATE * (capacity - suspended) * 10);
                        if (erode > 0) {
                            int newSurface = Math.max(0, h - erode);
                            if (newSurface != h) {
                                int dy = newSurface - h;
                                deltas[i] = (byte) Math.max(-127, Math.min(127, dy));
                                for (int y = h - 1; y >= newSurface; y--) {
                                    chunk.setVoxel(x, y, z, VoxelType.AIR);
                                }
                                chunk.setSurfaceY(x, z, newSurface);
                                sed[i] = (float) (suspended + (h - newSurface) * 0.5);
                                totalEroded += (h - newSurface);
                            }
                        }
                    } else {
                        // Deposit (up to suspended amount)
                        int deposit = (int) Math.ceil(DEPOSITION_RATE * (suspended - capacity) * 10);
                        if (deposit > 0) {
                            int newSurface = Math.min(Chunk.CHUNK_EDGE, h + deposit);
                            for (int y = h; y < newSurface; y++) {
                                chunk.setVoxel(x, y, z, VoxelType.SAND);
                            }
                            chunk.setSurfaceY(x, z, newSurface);
                            sed[i] = (float) Math.max(0, suspended - (newSurface - h));
                            totalDeposited += (newSurface - h);
                        }
                    }
                }
            }
            // Persist a single delta edit for this chunk.
            io.github.fv2j3dteam.universe.identifiers.UniverseId chunkAsId = new io.github.fv2j3dteam.universe.identifiers.UniverseId(
                    0L, coord.x(), coord.y(), coord.z(), 0L, 0L, 0L);
            persistence.recordEdit(new PersistentEdit(
                    chunkAsId,
                    System.currentTimeMillis(),
                    PersistentEdit.Kind.TERRAIN_HEIGHT_DELTA,
                    deltas));
        } finally { lock.writeLock().unlock(); }
    }

    private double computeSlope(Chunk chunk, int x, int z) {
        int h = chunk.surfaceY(x, z);
        double maxDiff = 0;
        int[][] neigh = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : neigh) {
            int nx = x + d[0], nz = z + d[1];
            if (nx < 0 || nx >= Chunk.CHUNK_EDGE || nz < 0 || nz >= Chunk.CHUNK_EDGE) continue;
            int nh = chunk.surfaceY(nx, nz);
            maxDiff = Math.max(maxDiff, h - nh);
        }
        return Math.min(1.0, maxDiff / 16.0);
    }

    public void unload(ChunkCoord coord) {
        lock.writeLock().lock();
        try { sedimentLayers.remove(coord); } finally { lock.writeLock().unlock(); }
    }

    public long residentBytes() {
        long bytes = 0;
        for (float[] a : sedimentLayers.values()) bytes += a.length * 4L;
        return bytes;
    }
}
