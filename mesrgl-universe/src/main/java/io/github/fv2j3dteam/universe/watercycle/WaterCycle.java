package io.github.fv2j3dteam.universe.watercycle;

import io.github.fv2j3dteam.universe.environment.HumidityField;
import io.github.fv2j3dteam.universe.identifiers.ChunkCoord;
import io.github.fv2j3dteam.universe.terrain.Chunk;
import io.github.fv2j3dteam.universe.terrain.VoxelType;
import io.github.fv2j3dteam.universe.weather.PrecipitationSystem;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Water cycle (§27, §45). Per-chunk coupling:
 *  Ocean/Water voxels → evaporation → humidity increase
 *  Humidity + pressure + temperature → condensation → precipitation
 *  Precipitation → snow depth (cold) or surface water (warm)
 *
 * The system reads the planet's surface temperature (via the
 * ThermalField), the planet's humidity, and the active weather
 * phase, and produces evaporation / condensation deltas plus
 * precipitation events.
 */
public final class WaterCycle {

    private final HumidityField humidityHelper = new HumidityField();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final java.util.Map<ChunkCoord, float[]> humidityField = new java.util.HashMap<>();
    private final PrecipitationSystem precipitation;

    public WaterCycle(PrecipitationSystem precipitation) {
        this.precipitation = Objects.requireNonNull(precipitation, "precipitation");
    }

    public float humidityAt(ChunkCoord coord, int x, int y, int z) {
        lock.readLock().lock();
        try {
            float[] a = humidityField.get(coord);
            if (a == null) return 0f;
            return a[index(x, y, z)];
        } finally { lock.readLock().unlock(); }
    }

    public void setHumidity(ChunkCoord coord, int x, int y, int z, float h) {
        lock.writeLock().lock();
        try {
            float[] a = humidityField.computeIfAbsent(coord, k -> new float[16 * 16 * 16]);
            a[index(x, y, z)] = Math.max(0, Math.min(1, h));
        } finally { lock.writeLock().unlock(); }
    }

    private int index(int x, int y, int z) {
        return (y * 16 + z) * 16 + x;
    }

    /**
     * Step the water cycle for one chunk for one time-step. Returns the
     * number of droplets emitted (precipitation).
     */
    public int step(ChunkCoord coord, Chunk chunk,
                    io.github.fv2j3dteam.universe.thermal.ThermalField thermal,
                    io.github.fv2j3dteam.universe.universe.Planet planet,
                    double ambientPressurePa,
                    double dt) {
        if (dt <= 0 || chunk == null || planet == null) return 0;
        if (planet.oceanCoverageFraction() < 0.1 && chunk.voxel(8, 8, 8) != VoxelType.WATER) {
            return 0; // dry land, no cycle
        }
        int emitted = 0;
        for (int z = 0; z < Chunk.CHUNK_EDGE; z++) {
            for (int x = 0; x < Chunk.CHUNK_EDGE; x++) {
                int surfaceY = chunk.surfaceY(x, z);
                int wx = (int) coord.x() * 16 + x;
                int wz = (int) coord.z() * 16 + z;
                int wy = (int) coord.y() * 16 + surfaceY;
                if (surfaceY <= 0) continue;
                double T = thermal.temperatureAt(coord, x, Math.max(0, surfaceY - 1), z);
                float h = humidityAt(coord, x, Math.max(0, surfaceY - 1), z);
                // Evaporation rate (proportional to wind, deficit, temperature)
                double windMs = 3.0; // approximation; real wind would come from WindField per chunk
                double deficit = Math.max(0, 1.0 - h);
                double evap = humidityHelper.evaporate(1.0, windMs, deficit, T, dt);
                // Humidity increase
                float newH = (float) Math.min(1.0, h + evap / 2000.0);
                setHumidity(coord, x, surfaceY, z, newH);
                // Condensation → precipitation when humidity near saturation
                if (newH > 0.92 && T < 283.0) {
                    // Emit a rain droplet from the surface
                    int ny = Math.min(15, surfaceY);
                    precipitation.emit(coord, x, ny, z, 0, 0, 0, 0.001);
                    emitted++;
                    setHumidity(coord, x, surfaceY, z, (float) (newH * 0.7));
                }
            }
        }
        return emitted;
    }

    public void unload(ChunkCoord coord) {
        lock.writeLock().lock();
        try { humidityField.remove(coord); } finally { lock.writeLock().unlock(); }
    }

    public long residentBytes() {
        long b = 0;
        for (float[] a : humidityField.values()) b += a.length * 4L;
        return b;
    }
}
