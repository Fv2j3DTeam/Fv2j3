package com.fv2j3.universe.universe;

import com.fv2j3.universe.identifiers.UniverseId;

import java.util.Objects;

/**
 * Planet metadata: radius, mass, gravity, axial tilt, orbital params, atmosphere,
 * climate baseline. Correlated rather than fully independent.
 */
public record Planet(
        UniverseId id,
        long seed,
        Archetype archetype,
        double radiusMeters,
        double massKg,
        double gravityMs2,
        double rotationPeriodHours,
        double axialTiltDeg,
        double orbitalPeriodDays,
        double semiMajorAxisAu,
        Atmosphere atmosphere,
        double baselineTemperatureK,
        double oceanCoverageFraction,
        double waterMassKg,
        double weatherArchetype,
        boolean hasMagneticField,
        boolean hasRings,
        int moonCount
) {
    public Planet {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(archetype, "archetype");
        Objects.requireNonNull(atmosphere, "atmosphere");
        if (!Double.isFinite(radiusMeters) || radiusMeters <= 0) throw new IllegalArgumentException("radius");
        if (!Double.isFinite(massKg) || massKg <= 0) throw new IllegalArgumentException("mass");
        if (!Double.isFinite(gravityMs2) || gravityMs2 <= 0) throw new IllegalArgumentException("gravity");
        if (!Double.isFinite(axialTiltDeg)) throw new IllegalArgumentException("tilt");
        if (!Double.isFinite(baselineTemperatureK)) throw new IllegalArgumentException("baseline temperature");
        if (oceanCoverageFraction < 0 || oceanCoverageFraction > 1) throw new IllegalArgumentException("ocean coverage");
    }

    public enum Archetype {
        EARTH_LIKE, OCEAN_WORLD, DESERT, FROZEN, VOLCANIC, GAS_GIANT,
        ICE_GIANT, ROCKY_BARREN, LAVA, TOXIC, SUPER_EARTH, SUB_EARTH,
        OCEAN_FROZEN, SWAMP, SAVANNA, TAIGA, TUNDRA, CUSTOM
    }
}