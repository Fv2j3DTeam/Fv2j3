package io.github.fv2j3dteam.universe.universe;

import io.github.fv2j3dteam.universe.identifiers.UniverseId;

import java.util.Objects;

/** Space station / POI metadata. */
public record Station(
        UniverseId id,
        long seed,
        Kind kind,
        String faction,
        double orbitalRadiusAu,
        boolean hasShops,
        boolean hasDocks,
        int population
) {
    public enum Kind { ORBITAL, GROUND, ANOMALY, RUIN, BEACON, RESEARCH, MINING, TRADE, PIRATE, DERELICT, CUSTOM }
    public Station {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(faction, "faction");
        if (!Double.isFinite(orbitalRadiusAu)) throw new IllegalArgumentException("orbit");
    }
}
