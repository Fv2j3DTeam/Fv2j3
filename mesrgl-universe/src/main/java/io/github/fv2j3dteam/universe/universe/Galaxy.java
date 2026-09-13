package io.github.fv2j3dteam.universe.universe;

import io.github.fv2j3dteam.universe.identifiers.UniverseId;

import java.util.List;
import java.util.Objects;

/**
 * Logical galaxy metadata. Cheap to materialise; a galaxy may have many
 * star systems but only their metadata is required up front.
 */
public record Galaxy(
        UniverseId id,
        long seed,
        Shape shape,
        double radiusLightYears,
        int coreArms,
        double metallicityBaseline,
        List<UniverseId> starSystemIds
) {
    public Galaxy {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(shape, "shape");
        if (!Double.isFinite(radiusLightYears) || radiusLightYears <= 0) {
            throw new IllegalArgumentException("radiusLightYears must be positive and finite");
        }
        if (coreArms < 0 || coreArms > 32) throw new IllegalArgumentException("coreArms out of range");
        if (!Double.isFinite(metallicityBaseline)) throw new IllegalArgumentException("metallicity must be finite");
        starSystemIds = starSystemIds == null ? List.of() : List.copyOf(starSystemIds);
    }

    public enum Shape {
        SPIRAL, ELLIPTICAL, IRREGULAR, BARRED_SPIRAL, LENTICULAR, CLUSTER, CUSTOM
    }

    public int systemCount() { return starSystemIds.size(); }

    public boolean hasSystem(UniverseId sid) {
        return starSystemIds.contains(sid);
    }
}