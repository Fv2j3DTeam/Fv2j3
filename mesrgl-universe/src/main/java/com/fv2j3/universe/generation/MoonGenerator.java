package com.fv2j3.universe.generation;

import com.fv2j3.universe.identifiers.UniverseId;
import com.fv2j3.universe.math.XorShift64;
import com.fv2j3.universe.seeds.SeedDerivation;
import com.fv2j3.universe.universe.Atmosphere;
import com.fv2j3.universe.universe.Moon;

/** Moon generator. Reuses Planet density / atmosphere heuristics. */
public final class MoonGenerator {
    public static final String STAGE = "moon";

    public Moon generate(UniverseId parentPlanetId, long universeRootSeed, long moonLocalId) {
        UniverseId id = parentPlanetId.withBody(moonLocalId);
        long seed = SeedDerivation.deriveSeed(universeRootSeed, id);
        XorShift64 rng = new XorShift64(seed);
        double radius = rng.nextDouble(5e5, 3e6);
        double density = rng.nextDouble(1500, 4000);
        double volume = 4.0/3.0 * Math.PI * radius * radius * radius;
        double mass = density * volume;
        double gravity = 6.67430e-11 * mass / (radius * radius);
        double semiMajor = rng.nextDouble(2e5, 1.5e6);
        double orbital = rng.nextDouble(0.5, 60.0);
        Atmosphere atmosphere;
        if (radius < 1.5e6 && rng.nextDouble() < 0.05) {
            atmosphere = new Atmosphere(rng.nextDouble(100, 10000), 0.0, 0.0, 0.0, 0.0, 1.0,
                    Atmosphere.Composition.NONE, false);
        } else {
            atmosphere = Atmosphere.vacuum();
        }
        boolean tidallyLocked = rng.nextDouble() < 0.6;
        return new Moon(id, seed, radius, mass, gravity, semiMajor, orbital, atmosphere, tidallyLocked);
    }
}
