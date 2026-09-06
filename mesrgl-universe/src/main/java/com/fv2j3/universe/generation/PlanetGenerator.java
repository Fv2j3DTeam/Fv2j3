package com.fv2j3.universe.generation;

import com.fv2j3.universe.identifiers.UniverseId;
import com.fv2j3.universe.math.XorShift64;
import com.fv2j3.universe.seeds.SeedDerivation;
import com.fv2j3.universe.universe.Atmosphere;
import com.fv2j3.universe.universe.Planet;

import java.util.Objects;

/** Planet generator (§9). Properties are correlated, not independent. */
public final class PlanetGenerator {

    public static final String STAGE = "planet";

    public Planet generate(UniverseId universeId, long universeRootSeed, long galaxyIndex, long systemIndex, long bodyIndex) {
        UniverseId pid = universeId.withGalaxy(galaxyIndex).withSystem(systemIndex).withBody(bodyIndex);
        long seed = SeedDerivation.deriveSeed(universeRootSeed, pid);
        XorShift64 rng = new XorShift64(seed);

        Planet.Archetype archetype = pickArchetype(rng);
        double radius = pickRadius(rng, archetype);
        double mass = pickMass(rng, archetype, radius);
        double gravity = 6.67430e-11 * mass / (radius * radius);
        double rotPeriod = rng.nextDouble(8.0, 240.0);
        double tilt = rng.nextDouble(0.0, 45.0);
        double orbital = pickOrbitalPeriod(rng, bodyIndex);
        double semiMajor = 0.3 * Math.pow(orbital / 365.25, 2.0/3.0); // Kepler-ish
        Atmosphere atmosphere = pickAtmosphere(rng, archetype, radius, mass);
        double baseTemp = pickBaselineTemperatureK(rng, archetype, atmosphere);
        double oceanCoverage = pickOceanCoverage(rng, archetype, baseTemp);
        double waterMass = oceanCoverage * 4.0 * Math.PI * radius * radius * 1000.0; // ~1km mean
        double weatherArchetype = rng.nextDouble();
        boolean magneticField = mass > 1e24 && rng.nextDouble() < 0.7;
        boolean rings = rng.nextDouble() < 0.05;
        int moonCount = rng.nextInt(0, 4);

        return new Planet(pid, seed, archetype, radius, mass, gravity, rotPeriod, tilt,
                orbital, semiMajor, atmosphere, baseTemp, oceanCoverage, waterMass,
                weatherArchetype, magneticField, rings, moonCount);
    }

    public Planet.Archetype pickArchetype(XorShift64 rng) {
        double r = rng.nextDouble();
        if (r < 0.18) return Planet.Archetype.EARTH_LIKE;
        if (r < 0.28) return Planet.Archetype.OCEAN_WORLD;
        if (r < 0.40) return Planet.Archetype.DESERT;
        if (r < 0.50) return Planet.Archetype.FROZEN;
        if (r < 0.55) return Planet.Archetype.VOLCANIC;
        if (r < 0.62) return Planet.Archetype.LAVA;
        if (r < 0.66) return Planet.Archetype.ICE_GIANT;
        if (r < 0.74) return Planet.Archetype.GAS_GIANT;
        if (r < 0.82) return Planet.Archetype.ROCKY_BARREN;
        if (r < 0.85) return Planet.Archetype.TOXIC;
        if (r < 0.88) return Planet.Archetype.SUPER_EARTH;
        if (r < 0.91) return Planet.Archetype.SUB_EARTH;
        if (r < 0.94) return Planet.Archetype.OCEAN_FROZEN;
        if (r < 0.96) return Planet.Archetype.SWAMP;
        if (r < 0.98) return Planet.Archetype.SAVANNA;
        if (r < 0.99) return Planet.Archetype.TAIGA;
        if (r < 0.995) return Planet.Archetype.TUNDRA;
        return Planet.Archetype.CUSTOM;
    }

    public double pickRadius(XorShift64 rng, Planet.Archetype arch) {
        return switch (arch) {
            case GAS_GIANT -> rng.nextDouble(5e7, 1.0e8);
            case ICE_GIANT -> rng.nextDouble(2.5e7, 6e7);
            case SUPER_EARTH -> rng.nextDouble(7e6, 1.5e7);
            case EARTH_LIKE, OCEAN_WORLD, DESERT, FROZEN, VOLCANIC, LAVA, ROCKY_BARREN, TOXIC, OCEAN_FROZEN, SWAMP, SAVANNA, TAIGA, TUNDRA -> rng.nextDouble(2e6, 7e6);
            case SUB_EARTH -> rng.nextDouble(1.5e6, 4e6);
            case CUSTOM -> rng.nextDouble(2e6, 5e7);
        };
    }

    public double pickMass(XorShift64 rng, Planet.Archetype arch, double radius) {
        double density = switch (arch) {
            case GAS_GIANT -> rng.nextDouble(600, 1500);
            case ICE_GIANT -> rng.nextDouble(1000, 2500);
            case VOLCANIC, LAVA -> rng.nextDouble(3500, 5500);
            case ROCKY_BARREN -> rng.nextDouble(3000, 4000);
            case EARTH_LIKE, OCEAN_WORLD, DESERT, FROZEN, SUPER_EARTH, SUB_EARTH, OCEAN_FROZEN, SWAMP, SAVANNA, TAIGA, TUNDRA, TOXIC, CUSTOM -> rng.nextDouble(3500, 5500);
        };
        double volume = 4.0 / 3.0 * Math.PI * radius * radius * radius;
        return density * volume;
    }

    public double pickOrbitalPeriod(XorShift64 rng, long bodyIndex) {
        // Planets further out have longer periods.
        double base = 5.0 + 50.0 * bodyIndex;
        return base * rng.nextDouble(0.8, 1.2);
    }

    public Atmosphere pickAtmosphere(XorShift64 rng, Planet.Archetype arch, double radius, double mass) {
        if (arch == Planet.Archetype.ROCKY_BARREN || arch == Planet.Archetype.CUSTOM && mass < 1e23) {
            return Atmosphere.vacuum();
        }
        double pressure = switch (arch) {
            case EARTH_LIKE, OCEAN_WORLD -> rng.nextDouble(80000, 110000);
            case SUPER_EARTH, SUB_EARTH -> rng.nextDouble(40000, 250000);
            case DESERT, SAVANNA -> rng.nextDouble(60000, 105000);
            case FROZEN, TUNDRA, OCEAN_FROZEN, TAIGA -> rng.nextDouble(50000, 105000);
            case SWAMP -> rng.nextDouble(90000, 130000);
            case VOLCANIC, LAVA -> rng.nextDouble(50000, 200000);
            case TOXIC -> rng.nextDouble(150000, 400000);
            case GAS_GIANT, ICE_GIANT -> rng.nextDouble(50000, 1.0e6);
            default -> rng.nextDouble(1000, 5000);
        };
        double o2, n2, co2, h2o, other;
        switch (arch) {
            case EARTH_LIKE, OCEAN_WORLD, SAVANNA, TAIGA -> {
                o2 = rng.nextDouble(0.18, 0.24); n2 = 0.78 - o2; co2 = rng.nextDouble(0.0003, 0.001); h2o = rng.nextDouble(0.0, 0.04); other = 1.0 - (o2 + n2 + co2 + h2o);
            }
            case DESERT, FROZEN, OCEAN_FROZEN, TUNDRA -> {
                o2 = rng.nextDouble(0.18, 0.24); n2 = 0.78 - o2; co2 = rng.nextDouble(0.0003, 0.005); h2o = rng.nextDouble(0.0, 0.01); other = 1.0 - (o2 + n2 + co2 + h2o);
            }
            case SWAMP -> {
                o2 = rng.nextDouble(0.18, 0.22); n2 = 0.78 - o2; co2 = rng.nextDouble(0.005, 0.05); h2o = rng.nextDouble(0.04, 0.10); other = 1.0 - (o2 + n2 + co2 + h2o);
            }
            case VOLCANIC, LAVA -> {
                o2 = rng.nextDouble(0.0, 0.05); n2 = rng.nextDouble(0.0, 0.4); co2 = rng.nextDouble(0.5, 0.95); h2o = rng.nextDouble(0.0, 0.05); other = 1.0 - (o2 + n2 + co2 + h2o);
            }
            case TOXIC -> {
                o2 = rng.nextDouble(0.0, 0.05); n2 = 0.0; co2 = rng.nextDouble(0.1, 0.5); h2o = rng.nextDouble(0.0, 0.1); other = 1.0 - (o2 + n2 + co2 + h2o);
            }
            case GAS_GIANT, ICE_GIANT -> {
                o2 = 0.0; n2 = 0.0; co2 = rng.nextDouble(0.0, 0.05); h2o = rng.nextDouble(0.0, 0.05); other = 1.0 - (co2 + h2o);
            }
            default -> {
                o2 = 0.0; n2 = 0.0; co2 = 0.0; h2o = 0.0; other = 1.0;
            }
        }
        // Clamp each non-negative first, then compute other as residual.
        if (o2 < 0) o2 = 0; if (n2 < 0) n2 = 0; if (co2 < 0) co2 = 0; if (h2o < 0) h2o = 0;
        double rest = 1.0 - (o2 + n2 + co2 + h2o);
        if (rest < 0) {
            // Distribute the deficit proportionally to the largest components.
            double total = o2 + n2 + co2 + h2o;
            if (total > 0) {
                double scale = 0.99 / total;
                o2 *= scale; n2 *= scale; co2 *= scale; h2o *= scale;
                rest = 0.01;
            } else {
                o2 = 0; n2 = 0; co2 = 0; h2o = 0; rest = 1.0;
            }
        }
        double sum = o2 + n2 + co2 + h2o + rest;
        if (sum <= 0) { o2 = 0; n2 = 0; co2 = 0; h2o = 0; rest = 1.0; sum = 1.0; }
        // Final clamp to [0,1]
        if (o2 < 0) o2 = 0; if (o2 > 1) o2 = 1;
        if (n2 < 0) n2 = 0; if (n2 > 1) n2 = 1;
        if (co2 < 0) co2 = 0; if (co2 > 1) co2 = 1;
        if (h2o < 0) h2o = 0; if (h2o > 1) h2o = 1;
        if (rest < 0) rest = 0; if (rest > 1) rest = 1;
        o2 /= sum; n2 /= sum; co2 /= sum; h2o /= sum; rest /= sum;
        Atmosphere.Composition comp = pickComposition(o2, n2, co2);
        boolean breathable = (o2 >= 0.16) && (n2 >= 0.6) && (co2 < 0.05) && (pressure >= 50000) && (pressure <= 130000);
        return new Atmosphere(pressure, o2, n2, co2, h2o, rest, comp, breathable);
    }

    private Atmosphere.Composition pickComposition(double o2, double n2, double co2) {
        if (o2 + n2 + co2 < 0.01) return Atmosphere.Composition.NONE;
        if (n2 > 0.5 && o2 < 0.05) return Atmosphere.Composition.NITROGEN;
        if (co2 > 0.4) return Atmosphere.Composition.CO2_DOMINANT;
        return Atmosphere.Composition.BREATHABLE;
    }

    public double pickBaselineTemperatureK(XorShift64 rng, Planet.Archetype arch, Atmosphere atmo) {
        double base = switch (arch) {
            case EARTH_LIKE -> 288.0 + rng.nextDouble(-15, 25);
            case OCEAN_WORLD, OCEAN_FROZEN, SWAMP, SAVANNA -> 290.0 + rng.nextDouble(-10, 15);
            case DESERT, VOLCANIC, LAVA -> 320.0 + rng.nextDouble(-20, 200);
            case FROZEN, TUNDRA, TAIGA -> 230.0 + rng.nextDouble(-30, 30);
            case ROCKY_BARREN, CUSTOM -> 200.0 + rng.nextDouble(-50, 50);
            case TOXIC -> 280.0 + rng.nextDouble(-20, 50);
            case GAS_GIANT, ICE_GIANT -> 120.0 + rng.nextDouble(-30, 30);
            case SUPER_EARTH -> 270.0 + rng.nextDouble(-30, 60);
            case SUB_EARTH -> 250.0 + rng.nextDouble(-40, 30);
        };
        // Greenhouse adjustment for CO2-heavy atmospheres
        if (atmo != null && atmo.carbonDioxideFraction() > 0.05) {
            base += 20.0 * Math.min(8.0, atmo.carbonDioxideFraction() * 10.0);
        }
        return base;
    }

    public double pickOceanCoverage(XorShift64 rng, Planet.Archetype arch, double baseTempK) {
        if (arch == Planet.Archetype.ROCKY_BARREN) return 0.0;
        if (arch == Planet.Archetype.GAS_GIANT || arch == Planet.Archetype.ICE_GIANT) return 0.0;
        if (arch == Planet.Archetype.FROZEN || arch == Planet.Archetype.OCEAN_FROZEN) return rng.nextDouble(0.6, 0.95);
        if (arch == Planet.Archetype.DESERT) return rng.nextDouble(0.0, 0.1);
        if (arch == Planet.Archetype.LAVA || arch == Planet.Archetype.VOLCANIC) return rng.nextDouble(0.0, 0.05);
        if (arch == Planet.Archetype.TOXIC) return rng.nextDouble(0.0, 0.4);
        if (baseTempK < 273.0) return rng.nextDouble(0.2, 0.6);
        return rng.nextDouble(0.3, 0.9);
    }

    public Planet generateForUnified(UniverseId id, long seed, Planet.Archetype archetype) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(archetype, "archetype");
        XorShift64 rng = new XorShift64(seed ^ archetype.hashCode());
        double radius = pickRadius(rng, archetype);
        double mass = pickMass(rng, archetype, radius);
        double gravity = 6.67430e-11 * mass / (radius * radius);
        double rotPeriod = rng.nextDouble(8.0, 240.0);
        double tilt = rng.nextDouble(0.0, 45.0);
        Atmosphere atmosphere = pickAtmosphere(rng, archetype, radius, mass);
        double baseTemp = pickBaselineTemperatureK(rng, archetype, atmosphere);
        double oceanCoverage = pickOceanCoverage(rng, archetype, baseTemp);
        double waterMass = oceanCoverage * 4.0 * Math.PI * radius * radius * 1000.0;
        double weatherArchetype = rng.nextDouble();
        boolean magneticField = mass > 1e24 && rng.nextDouble() < 0.7;
        boolean rings = rng.nextDouble() < 0.05;
        int moonCount = rng.nextInt(0, 4);
        return new Planet(id, seed, archetype, radius, mass, gravity, rotPeriod, tilt,
                365.25, 1.0, atmosphere, baseTemp, oceanCoverage, waterMass,
                weatherArchetype, magneticField, rings, moonCount);
    }
}
