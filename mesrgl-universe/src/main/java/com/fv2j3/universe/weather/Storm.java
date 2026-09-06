package com.fv2j3.universe.weather;

import com.fv2j3.universe.identifiers.UniverseId;
import com.fv2j3.universe.math.XorShift64;
import com.fv2j3.universe.seeds.SeedDerivation;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Storm entity (§28). Has center, radius, intensity, lifecycle. Multiple
 * storms may coexist. Storm movement is deterministic per (id, time).
 */
public final class Storm {

    public enum State { FORMING, ACTIVE, DISSIPATING, DEAD }

    private final long id;
    private final UniverseId planetId;
    private final long seed;
    private final AtomicLong internalId = new AtomicLong();
    private final double baseIntensity;
    private State state = State.FORMING;
    private double centerLat;
    private double centerLon;
    private double radiusM;
    private double ageSeconds;
    private double pressureDeficitPa;
    private double maxWindMs;
    private String precipitationType = "rain";

    public Storm(UniverseId planetId, double centerLat, double centerLon, double radiusM, double intensity) {
        this(planetId, 0L, centerLat, centerLon, radiusM, intensity);
    }

    public Storm(UniverseId planetId, long id, double centerLat, double centerLon, double radiusM, double intensity) {
        this.id = id != 0L ? id : internalId.incrementAndGet();
        this.planetId = Objects.requireNonNull(planetId, "planetId");
        this.seed = SeedDerivation.deriveSubSeed(planetId.stableHash(), "storm".hashCode());
        this.centerLat = centerLat;
        this.centerLon = centerLon;
        this.radiusM = radiusM;
        this.baseIntensity = Math.max(0, Math.min(1, intensity));
        this.pressureDeficitPa = baseIntensity * 5000;
        this.maxWindMs = 5 + baseIntensity * 50;
    }

    public long id() { return id; }
    public UniverseId planetId() { return planetId; }
    public State state() { return state; }
    public double centerLat() { return centerLat; }
    public double centerLon() { return centerLon; }
    public double radiusM() { return radiusM; }
    public double baseIntensity() { return baseIntensity; }
    public double pressureDeficitPa() { return pressureDeficitPa; }
    public double maxWindMs() { return maxWindMs; }
    public String precipitationType() { return precipitationType; }
    public double ageSeconds() { return ageSeconds; }

    public void setState(State s) { this.state = s; }
    public void setPrecipitationType(String type) { this.precipitationType = type; }

    public void step(double dt) {
        if (dt <= 0) return;
        ageSeconds += dt;
        // Move storm deterministically
        XorShift64 r = new XorShift64(seed + (long) ageSeconds);
        double dLon = (r.nextDouble() - 0.5) * 0.001 * dt;
        double dLat = (r.nextDouble() - 0.5) * 0.0005 * dt;
        centerLon = (centerLon + dLon + 3.14159) % (2 * 3.14159);
        centerLat = Math.max(-1.5, Math.min(1.5, centerLat + dLat));
        // Lifecycle
        if (state == State.FORMING && ageSeconds > 600) state = State.ACTIVE;
        if (state == State.ACTIVE && ageSeconds > 3600 * 4) state = State.DISSIPATING;
        if (state == State.DISSIPATING && ageSeconds > 3600 * 6) state = State.DEAD;
        if (state == State.DISSIPATING) {
            pressureDeficitPa *= 0.999;
            maxWindMs *= 0.999;
        }
    }

    public double intensityAt(double lat, double lon) {
        double dLat = lat - centerLat;
        double dLon = lon - centerLon;
        double d = Math.sqrt(dLat * dLat + dLon * dLon);
        if (d > radiusM) return 0;
        double t = 1.0 - d / radiusM;
        return baseIntensity * t * t;
    }
}
