package com.fv2j3.universe.universe;

import com.fv2j3.universe.identifiers.UniverseId;

import java.util.Objects;

/** Asteroid belt metadata; cheap to materialise. */
public record AsteroidBelt(
        UniverseId id,
        long seed,
        double innerRadiusAu,
        double outerRadiusAu,
        double massKg,
        double metallicity,
        boolean hasStations
) {
    public AsteroidBelt {
        Objects.requireNonNull(id, "id");
        if (!Double.isFinite(innerRadiusAu) || innerRadiusAu <= 0) throw new IllegalArgumentException("inner");
        if (!Double.isFinite(outerRadiusAu) || outerRadiusAu < innerRadiusAu) throw new IllegalArgumentException("outer");
        if (!Double.isFinite(massKg) || massKg <= 0) throw new IllegalArgumentException("mass");
    }
}
