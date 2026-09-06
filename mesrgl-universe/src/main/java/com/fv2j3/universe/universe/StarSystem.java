package com.fv2j3.universe.universe;

import com.fv2j3.universe.identifiers.UniverseId;

import java.util.List;
import java.util.Objects;

/**
 * Star system: contains one or more stars, planets, moons, asteroid belts, stations.
 */
public record StarSystem(
        UniverseId id,
        long seed,
        List<UniverseId> starIds,
        List<UniverseId> planetIds,
        List<UniverseId> moonIds,
        List<UniverseId> asteroidBeltIds,
        List<UniverseId> stationIds,
        double positionLightYearsX,
        double positionLightYearsY,
        double positionLightYearsZ
) {
    public StarSystem {
        Objects.requireNonNull(id, "id");
        starIds = starIds == null ? List.of() : List.copyOf(starIds);
        planetIds = planetIds == null ? List.of() : List.copyOf(planetIds);
        moonIds = moonIds == null ? List.of() : List.copyOf(moonIds);
        asteroidBeltIds = asteroidBeltIds == null ? List.of() : List.copyOf(asteroidBeltIds);
        stationIds = stationIds == null ? List.of() : List.copyOf(stationIds);
        if (!Double.isFinite(positionLightYearsX)
                || !Double.isFinite(positionLightYearsY)
                || !Double.isFinite(positionLightYearsZ)) {
            throw new IllegalArgumentException("position must be finite");
        }
    }

    public int bodyCount() { return starIds.size() + planetIds.size() + moonIds.size(); }
}