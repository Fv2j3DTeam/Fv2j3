package com.fv2j3.universe.generation;

import com.fv2j3.universe.identifiers.ChunkCoord;
import com.fv2j3.universe.identifiers.UniverseId;
import com.fv2j3.universe.math.NoiseComposition;
import com.fv2j3.universe.math.SimplexNoise;
import com.fv2j3.universe.math.XorShift64;
import com.fv2j3.universe.seeds.SeedDerivation;
import com.fv2j3.universe.universe.Planet;

import java.util.Objects;

/**
 * Climate profile (§10). Generates a temperature/humidity/pressure baseline at a
 * point on a planet's surface using analytic + noise-based heuristics. Not a full
 * GCM — but values are spatially coherent and respect the planet's parameters.
 */
public final class ClimateGenerator {

    public static final class ClimateSample {
        public final double temperatureK;
        public final double humidity; // 0..1
        public final double pressurePa;
        public final double latitude; // -pi/2..pi/2
        public final double altitude; // metres

        public ClimateSample(double temperatureK, double humidity, double pressurePa, double latitude, double altitude) {
            this.temperatureK = temperatureK;
            this.humidity = humidity;
            this.pressurePa = pressurePa;
            this.latitude = latitude;
            this.altitude = altitude;
        }
    }

    public ClimateSample sample(Planet planet, ChunkCoord chunk, double localX, double localY, double localZ) {
        Objects.requireNonNull(planet, "planet");
        Objects.requireNonNull(chunk, "chunk");
        // Spherical-ish mapping: chunk-coord-driven latitude/longitude on a sphere of the planet's radius.
        double latScale = Math.PI; // chunks span a hemisphere
        double latitude = (chunk.y() / 64.0) * latScale; // -pi/2..pi/2
        double longitude = Math.atan2(localZ, localX);
        double altitude = (NoiseComposition.billow2D(noiseFor(planet, "altitude"), localX * 0.001, localZ * 0.001, 4, 2.0, 0.5)
                + 1.0) * 1500.0; // ~0..3000 m above sea level
        double temp = computeTemperature(planet, latitude, altitude);
        double humidity = computeHumidity(planet, latitude, altitude, temp);
        double pressure = computePressure(planet, altitude);
        return new ClimateSample(temp, humidity, pressure, latitude, altitude);
    }

    public double computeTemperature(Planet planet, double latitude, double altitude) {
        double base = planet.baselineTemperatureK();
        double latFactor = Math.cos(latitude); // warm equator, cool poles
        double tiltFactor = 1.0 + 0.1 * Math.sin(latitude) * Math.cos(planet.axialTiltDeg() * Math.PI / 180.0);
        double altitudeLapse = Math.exp(-altitude / 8500.0); // standard atmosphere
        return base * latFactor * tiltFactor * altitudeLapse;
    }

    public double computeHumidity(Planet planet, double latitude, double altitude, double temperatureK) {
        double oceanInfluence = planet.oceanCoverageFraction();
        double tempHumidityCurve = Math.max(0, 1.0 - Math.abs(temperatureK - 300.0) / 60.0);
        double latHumidity = 0.5 + 0.5 * Math.cos(latitude * 1.5);
        double h = oceanInfluence * 0.6 + tempHumidityCurve * 0.3 + latHumidity * 0.1;
        return Math.max(0.0, Math.min(1.0, h));
    }

    public double computePressure(Planet planet, double altitude) {
        double surface = planet.atmosphere().pressurePa();
        if (surface <= 0) return 0.0;
        return surface * Math.exp(-altitude / 8500.0);
    }

    private SimplexNoise noiseFor(Planet planet, String purpose) {
        UniverseId id = planet.id();
        long seed = SeedDerivation.deriveSubSeed(planet.seed(), purpose.hashCode());
        return new SimplexNoise(seed);
    }
}
