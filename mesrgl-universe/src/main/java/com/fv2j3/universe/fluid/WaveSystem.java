package com.fv2j3.universe.fluid;

import com.fv2j3.universe.identifiers.ChunkCoord;
import com.fv2j3.universe.universe.Planet;

import java.util.Objects;

/**
 * Wave system (§40). Thin wrapper around {@link OceanSystem} for use by
 * gameplay and rendering. Wave behaviour is parameterised by wind speed,
 * direction, fetch, depth, and obstacles (§40).
 */
public final class WaveSystem {

    private final OceanSystem ocean;

    public WaveSystem(OceanSystem ocean) {
        this.ocean = Objects.requireNonNull(ocean, "ocean");
    }

    public OceanSystem ocean() { return ocean; }

    public double sampleHeight(ChunkCoord coord, double localX, double localZ, double timeSeconds) {
        return ocean.surfaceHeightAt(coord, localX, localZ, timeSeconds);
    }

    public OceanSystem.WaveParams parametersFor(ChunkCoord coord, Planet planet, double depth) {
        return ocean.paramsFor(coord, planet, depth);
    }
}
