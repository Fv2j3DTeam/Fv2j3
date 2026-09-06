package com.fv2j3.universe.environment;

import com.fv2j3.universe.math.SimplexNoise;
import com.fv2j3.universe.seeds.SeedDerivation;
import com.fv2j3.universe.universe.Planet;

import java.util.Objects;

/**
 * Wind field (§24). Deterministic for a given planet + position + time.
 * Combines a planetary baseline (latitude-driven trade winds) with turbulent noise.
 * Sources (storms, emitters) can override.
 */
public final class WindField {

    public static final class Sample {
        public final double vx, vy, vz; // m/s
        public final double magnitude; // m/s
        public Sample(double vx, double vy, double vz) {
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.magnitude = Math.sqrt(vx*vx + vy*vy + vz*vz);
        }
    }

    public Sample sample(Planet planet, double latitude, double longitude, double altitude, double timeSeconds) {
        Objects.requireNonNull(planet, "planet");
        long seed = SeedDerivation.deriveSubSeed(planet.seed(), "wind".hashCode());
        SimplexNoise noise = new SimplexNoise(seed);

        // Baseline: trade winds ~ cos(3*lat), westerlies ~ -cos(lat)
        double baseSpeed = 5.0 + 5.0 * Math.cos(latitude);
        double angle = Math.cos(latitude) * 0.5 + 0.5;
        double baseVx = -Math.sin(longitude) * baseSpeed * angle;
        double baseVz = -Math.cos(longitude) * baseSpeed * angle;
        double baseVy = 0.5 * Math.sin(latitude * 2.0);

        // Turbulence
        double turbScale = 0.0005;
        double tx = noise.noise2D(longitude * 50.0 + timeSeconds * 0.01, latitude * 50.0);
        double ty = noise.noise2D(longitude * 50.0 + 100, latitude * 50.0 + timeSeconds * 0.01);
        double tz = noise.noise2D(longitude * 50.0, latitude * 50.0 + 200);
        double turbAmp = 4.0 * Math.exp(-altitude / 15000.0);
        double vx = baseVx + tx * turbAmp;
        double vy = baseVy + ty * turbAmp * 0.5;
        double vz = baseVz + tz * turbAmp;

        // Altitude-dependent wind shear
        vx *= 1.0 + altitude / 10000.0;
        vz *= 1.0 + altitude / 10000.0;
        return new Sample(vx, vy, vz);
    }
}
