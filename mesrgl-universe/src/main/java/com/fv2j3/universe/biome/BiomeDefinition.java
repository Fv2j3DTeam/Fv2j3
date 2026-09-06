package com.fv2j3.universe.biome;

import com.fv2j3.universe.identifiers.UniverseId;
import com.fv2j3.universe.math.XorShift64;
import com.fv2j3.universe.seeds.SeedDerivation;
import com.fv2j3.universe.universe.Planet;

import java.util.Objects;

/**
 * Biome definition (§12). Externally extensible. Selection uses environmental
 * inputs (temperature, humidity, altitude, latitude, ocean proximity) rather
 * than random selection. Biomes are spatially coherent via deterministic hashing.
 */
public final class BiomeDefinition {
    private final String id;
    private final double minTempK;
    private final double maxTempK;
    private final double minHumidity;
    private final double maxHumidity;
    private final double minAltitude;
    private final double maxAltitude;
    private final boolean isOcean;
    private final double vegetationDensity;
    private final double baseColorR;
    private final double baseColorG;
    private final double baseColorB;

    public BiomeDefinition(String id,
                           double minTempK, double maxTempK,
                           double minHumidity, double maxHumidity,
                           double minAltitude, double maxAltitude,
                           boolean isOcean,
                           double vegetationDensity,
                           double r, double g, double b) {
        this.id = Objects.requireNonNull(id, "id");
        this.minTempK = minTempK;
        this.maxTempK = maxTempK;
        this.minHumidity = minHumidity;
        this.maxHumidity = maxHumidity;
        this.minAltitude = minAltitude;
        this.maxAltitude = maxAltitude;
        this.isOcean = isOcean;
        this.vegetationDensity = vegetationDensity;
        this.baseColorR = r;
        this.baseColorG = g;
        this.baseColorB = b;
    }

    public String id() { return id; }
    public double minTempK() { return minTempK; }
    public double maxTempK() { return maxTempK; }
    public double minHumidity() { return minHumidity; }
    public double maxHumidity() { return maxHumidity; }
    public double minAltitude() { return minAltitude; }
    public double maxAltitude() { return maxAltitude; }
    public boolean isOcean() { return isOcean; }
    public double vegetationDensity() { return vegetationDensity; }
    public double baseColorR() { return baseColorR; }
    public double baseColorG() { return baseColorG; }
    public double baseColorB() { return baseColorB; }

    public boolean matches(double tempK, double humidity, double altitude) {
        if (tempK < minTempK || tempK > maxTempK) return false;
        if (humidity < minHumidity || humidity > maxHumidity) return false;
        if (altitude < minAltitude || altitude > maxAltitude) return false;
        return true;
    }

    public static BiomeDefinition ocean() {
        return new BiomeDefinition("ocean", 270, 305, 0.5, 1.0, -100, 50, true, 0.0, 0.05, 0.2, 0.6);
    }
    public static BiomeDefinition desert() {
        return new BiomeDefinition("desert", 280, 330, 0.0, 0.4, 0, 1500, false, 0.05, 0.85, 0.75, 0.45);
    }
    public static BiomeDefinition tundra() {
        return new BiomeDefinition("tundra", 200, 273, 0.2, 1.0, 0, 1500, false, 0.1, 0.7, 0.75, 0.8);
    }
    public static BiomeDefinition forest() {
        return new BiomeDefinition("forest", 270, 305, 0.5, 1.0, 0, 2000, false, 0.9, 0.15, 0.45, 0.15);
    }
    public static BiomeDefinition grassland() {
        return new BiomeDefinition("grassland", 280, 305, 0.3, 0.7, 0, 1500, false, 0.5, 0.4, 0.65, 0.25);
    }
    public static BiomeDefinition mountain() {
        return new BiomeDefinition("mountain", 200, 290, 0.0, 1.0, 1500, 6000, false, 0.05, 0.5, 0.45, 0.4);
    }
    public static BiomeDefinition volcanic() {
        return new BiomeDefinition("volcanic", 350, 1200, 0.0, 0.4, 0, 4000, false, 0.0, 0.2, 0.1, 0.1);
    }
    public static BiomeDefinition swamp() {
        return new BiomeDefinition("swamp", 285, 305, 0.7, 1.0, -10, 200, false, 0.7, 0.25, 0.35, 0.2);
    }
    public static BiomeDefinition taiga() {
        return new BiomeDefinition("taiga", 250, 285, 0.3, 0.7, 0, 1500, false, 0.6, 0.2, 0.35, 0.2);
    }
    public static BiomeDefinition ice() {
        return new BiomeDefinition("ice", 150, 273, 0.0, 1.0, 0, 3000, false, 0.0, 0.95, 0.97, 1.0);
    }
}
