package com.fv2j3.universe.lod;

/**
 * Hierarchical LOD tiers (§19, §59).
 * Tier 0: global climate statistics
 * Tier 1: regional weather/coarse environment
 * Tier 2: chunk-level environmental fields
 * Tier 3: detailed local simulation (fluids, smoke, fire, particles)
 */
public enum LOD {
    GLOBAL_CLIMATE(0, 4096.0),
    REGIONAL(1, 1024.0),
    CHUNK(2, 64.0),
    DETAILED(3, 1.0);

    private final int tier;
    private final double cellSizeMetres;

    LOD(int tier, double cellSizeMetres) {
        this.tier = tier;
        this.cellSizeMetres = cellSizeMetres;
    }

    public int tier() { return tier; }
    public double cellSizeMetres() { return cellSizeMetres; }

    public static LOD forDistance(double distanceMetres) {
        if (distanceMetres > 5000.0) return GLOBAL_CLIMATE;
        if (distanceMetres > 1000.0) return REGIONAL;
        if (distanceMetres > 128.0) return CHUNK;
        return DETAILED;
    }

    public LOD coarser() {
        return switch (this) {
            case DETAILED -> CHUNK;
            case CHUNK -> REGIONAL;
            case REGIONAL -> GLOBAL_CLIMATE;
            case GLOBAL_CLIMATE -> GLOBAL_CLIMATE;
        };
    }

    public LOD finer() {
        return switch (this) {
            case GLOBAL_CLIMATE -> REGIONAL;
            case REGIONAL -> CHUNK;
            case CHUNK -> DETAILED;
            case DETAILED -> DETAILED;
        };
    }
}
