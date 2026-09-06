package com.fv2j3.universe.resources;

import java.util.Objects;

/**
 * Resource definition (§13). Externally extensible.
 */
public final class ResourceDefinition {
    private final String id;
    private final double baseDensity; // kg/m^3
    private final double rarity; // 0..1
    private final double minTempK;
    private final double maxTempK;
    private final double minDepth;
    private final double maxDepth;
    private final boolean biomeConstraint;
    private final String preferredBiome;
    private final boolean regenerates;
    private final double regenRateKgPerSec;

    public ResourceDefinition(String id,
                              double baseDensity,
                              double rarity,
                              double minTempK, double maxTempK,
                              double minDepth, double maxDepth,
                              boolean biomeConstraint, String preferredBiome,
                              boolean regenerates, double regenRateKgPerSec) {
        this.id = Objects.requireNonNull(id, "id");
        if (baseDensity < 0) throw new IllegalArgumentException("density");
        if (rarity < 0 || rarity > 1) throw new IllegalArgumentException("rarity");
        this.baseDensity = baseDensity;
        this.rarity = rarity;
        this.minTempK = minTempK;
        this.maxTempK = maxTempK;
        this.minDepth = minDepth;
        this.maxDepth = maxDepth;
        this.biomeConstraint = biomeConstraint;
        this.preferredBiome = preferredBiome == null ? "" : preferredBiome;
        this.regenerates = regenerates;
        this.regenRateKgPerSec = Math.max(0, regenRateKgPerSec);
    }

    public String id() { return id; }
    public double baseDensity() { return baseDensity; }
    public double rarity() { return rarity; }
    public double minTempK() { return minTempK; }
    public double maxTempK() { return maxTempK; }
    public double minDepth() { return minDepth; }
    public double maxDepth() { return maxDepth; }
    public boolean biomeConstraint() { return biomeConstraint; }
    public String preferredBiome() { return preferredBiome; }
    public boolean regenerates() { return regenerates; }
    public double regenRateKgPerSec() { return regenRateKgPerSec; }

    public boolean matches(double temperatureK, double depth, String biomeId) {
        if (temperatureK < minTempK || temperatureK > maxTempK) return false;
        if (depth < minDepth || depth > maxDepth) return false;
        if (biomeConstraint && !preferredBiome.isEmpty() && !preferredBiome.equals(biomeId)) return false;
        return true;
    }

    public static ResourceDefinition iron() {
        return new ResourceDefinition("iron", 7800, 0.2, 0, 1500, 0, 1000, false, "", false, 0);
    }
    public static ResourceDefinition copper() {
        return new ResourceDefinition("copper", 8960, 0.3, 0, 1500, 0, 800, false, "", false, 0);
    }
    public static ResourceDefinition water() {
        return new ResourceDefinition("water", 1000, 0.05, 250, 350, -100, 5, true, "ocean", true, 0.01);
    }
    public static ResourceDefinition coal() {
        return new ResourceDefinition("coal", 1300, 0.4, 0, 1000, 0, 200, true, "forest", false, 0);
    }
}
