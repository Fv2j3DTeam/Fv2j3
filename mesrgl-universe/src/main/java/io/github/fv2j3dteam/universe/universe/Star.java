package io.github.fv2j3dteam.universe.universe;

import io.github.fv2j3dteam.universe.identifiers.UniverseId;

import java.util.Objects;

/**
 * Star metadata: type, mass, radius, temperature, luminosity, age, metallicity.
 * Values are kept in canonical (deterministic) physical-ish units.
 */
public record Star(
        UniverseId id,
        long seed,
        SpectralClass spectralClass,
        double massSolar,
        double radiusSolar,
        double temperatureK,
        double luminositySolar,
        double ageGyr,
        double metallicity,
        boolean isMainSequence
) {
    public Star {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(spectralClass, "spectralClass");
        if (!Double.isFinite(massSolar) || massSolar <= 0) throw new IllegalArgumentException("massSolar");
        if (!Double.isFinite(radiusSolar) || radiusSolar <= 0) throw new IllegalArgumentException("radiusSolar");
        if (!Double.isFinite(temperatureK) || temperatureK <= 0) throw new IllegalArgumentException("temperatureK");
        if (!Double.isFinite(luminositySolar) || luminositySolar <= 0) throw new IllegalArgumentException("luminositySolar");
        if (!Double.isFinite(ageGyr) || ageGyr < 0) throw new IllegalArgumentException("ageGyr");
        if (!Double.isFinite(metallicity)) throw new IllegalArgumentException("metallicity");
    }

    public enum SpectralClass {
        O, B, A, F, G, K, M, // main sequence
        WHITE_DWARF, NEUTRON, BLACK_HOLE, BROWN_DWARF, GIANT, SUPERGIANT
    }
}