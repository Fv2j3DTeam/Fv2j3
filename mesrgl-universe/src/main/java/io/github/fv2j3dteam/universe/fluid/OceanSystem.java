package io.github.fv2j3dteam.universe.fluid;

import io.github.fv2j3dteam.universe.identifiers.ChunkCoord;
import io.github.fv2j3dteam.universe.universe.Planet;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Ocean system (§39). Per-chunk ocean surface. Uses large-scale wave
 * parameters computed from wind + fetch + depth (§40). LOD-cheap: distant
 * oceans are statistical.
 */
public final class OceanSystem {

    public static final class WaveParams {
        public final double amplitude; // metres
        public final double wavelength; // metres
        public final double phase; // radians
        public WaveParams(double a, double w, double p) {
            this.amplitude = a; this.wavelength = w; this.phase = p;
        }
    }

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<ChunkCoord, WaveParams> params = new LinkedHashMap<>();
    private double globalWindMs = 5.0;
    private double globalWindDir = 0.0;

    public void setGlobalWind(double speedMs, double dirRad) {
        lock.writeLock().lock();
        try {
            this.globalWindMs = Math.max(0, speedMs);
            this.globalWindDir = dirRad;
        } finally { lock.writeLock().unlock(); }
    }

    public WaveParams paramsFor(ChunkCoord coord, Planet planet, double depth) {
        // Wavelength grows with wind speed; amplitude grows with sqrt(wind * fetch)
        double wavelength = 0.5 + 2.0 * globalWindMs;
        double amplitude = 0.05 + 0.1 * Math.sqrt(globalWindMs) * (1.0 - Math.min(1.0, depth / 200.0));
        double phase = (coord.x() * 0.13 + coord.z() * 0.17 + globalWindDir);
        WaveParams wp = new WaveParams(amplitude, wavelength, phase);
        lock.writeLock().lock();
        try { params.put(coord, wp); } finally { lock.writeLock().unlock(); }
        return wp;
    }

    public double surfaceHeightAt(ChunkCoord coord, double localX, double localZ, double timeSeconds) {
        lock.readLock().lock();
        WaveParams wp = params.get(coord);
        if (wp == null) { lock.readLock().unlock(); return 0; }
        lock.readLock().unlock();
        double k = 2.0 * Math.PI / wp.wavelength;
        double theta = k * (localX * Math.cos(0.5) + localZ * Math.sin(0.5)) - 0.5 * timeSeconds + wp.phase;
        return wp.amplitude * Math.sin(theta);
    }

    public long residentBytes() {
        lock.readLock().lock();
        try { return (long) params.size() * 32L; } finally { lock.readLock().unlock(); }
    }

    public void unload(ChunkCoord coord) {
        lock.writeLock().lock();
        try { params.remove(coord); } finally { lock.writeLock().unlock(); }
    }
}
