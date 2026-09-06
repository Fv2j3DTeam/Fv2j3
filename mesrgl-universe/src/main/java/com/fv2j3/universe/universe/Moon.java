package com.fv2j3.universe.universe;

import com.fv2j3.universe.identifiers.UniverseId;

import java.util.Objects;

/**
 * Moon metadata. Like planets but typically smaller and bound to a parent body.
 */
public record Moon(
        UniverseId id,
        long seed,
        double radiusMeters,
        double massKg,
        double gravityMs2,
        double semiMajorAxisKm,
        double orbitalPeriodDays,
        Atmosphere atmosphere,
        boolean tidallyLocked
) {
    public Moon {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(atmosphere, "atmosphere");
        if (!Double.isFinite(radiusMeters) || radiusMeters <= 0) throw new IllegalArgumentException("radius");
        if (!Double.isFinite(massKg) || massKg <= 0) throw new IllegalArgumentException("mass");
        if (!Double.isFinite(gravityMs2) || gravityMs2 <= 0) throw new IllegalArgumentException("gravity");
        if (!Double.isFinite(semiMajorAxisKm) || semiMajorAxisKm <= 0) throw new IllegalArgumentException("orbit");
    }
}