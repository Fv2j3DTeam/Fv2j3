package com.fv2j3.universe.generation;

import com.fv2j3.universe.identifiers.UniverseId;
import com.fv2j3.universe.math.XorShift64;
import com.fv2j3.universe.seeds.SeedDerivation;
import com.fv2j3.universe.universe.Star;

/** Star generator (§8). Mass/radius/temp/luminosity are correlated via spectral class. */
public final class StarGenerator {

    public static final String STAGE = "star";

    public Star generate(UniverseId universeId, long universeRootSeed, long galaxyIndex, long systemIndex, long starLocal) {
        UniverseId starId = universeId.withGalaxy(galaxyIndex).withSystem(systemIndex).withBody(starLocal);
        long seed = SeedDerivation.deriveSeed(universeRootSeed, starId);
        XorShift64 rng = new XorShift64(seed);

        double r = rng.nextDouble();
        Star.SpectralClass klass;
        double mass, tempK, lum, lifeGyr, radius;
        if (r < 0.00003) { klass = Star.SpectralClass.BLACK_HOLE; mass = rng.nextDouble(5, 30); radius = 1e-6; tempK = 0; lum = 0; lifeGyr = 1e10; }
        else if (r < 0.0003) { klass = Star.SpectralClass.NEUTRON; mass = rng.nextDouble(1.1, 2.5); radius = 1e-5; tempK = 1e6; lum = rng.nextDouble(0.001, 0.1); lifeGyr = 1e10; }
        else if (r < 0.04) { klass = Star.SpectralClass.WHITE_DWARF; mass = rng.nextDouble(0.5, 1.4); radius = 0.01; tempK = rng.nextDouble(8000, 30000); lum = rng.nextDouble(0.001, 0.1); lifeGyr = rng.nextDouble(1, 10); }
        else if (r < 0.06) { klass = Star.SpectralClass.BROWN_DWARF; mass = rng.nextDouble(0.012, 0.08); radius = rng.nextDouble(0.1, 0.2); tempK = rng.nextDouble(800, 2400); lum = rng.nextDouble(0.0001, 0.001); lifeGyr = 1e10; }
        else if (r < 0.10) { klass = Star.SpectralClass.O; mass = rng.nextDouble(16, 90); radius = rng.nextDouble(6, 15); tempK = rng.nextDouble(30000, 50000); lum = rng.nextDouble(30000, 1000000); lifeGyr = rng.nextDouble(0.001, 0.01); }
        else if (r < 0.16) { klass = Star.SpectralClass.B; mass = rng.nextDouble(2.1, 16); radius = rng.nextDouble(1.8, 6); tempK = rng.nextDouble(10000, 30000); lum = rng.nextDouble(25, 30000); lifeGyr = rng.nextDouble(0.01, 0.5); }
        else if (r < 0.22) { klass = Star.SpectralClass.A; mass = rng.nextDouble(1.4, 2.1); radius = rng.nextDouble(1.4, 1.8); tempK = rng.nextDouble(7500, 10000); lum = rng.nextDouble(5, 25); lifeGyr = rng.nextDouble(0.5, 2.0); }
        else if (r < 0.30) { klass = Star.SpectralClass.F; mass = rng.nextDouble(1.04, 1.4); radius = rng.nextDouble(1.15, 1.4); tempK = rng.nextDouble(6000, 7500); lum = rng.nextDouble(1.5, 5); lifeGyr = rng.nextDouble(2.0, 4.0); }
        else if (r < 0.40) { klass = Star.SpectralClass.G; mass = rng.nextDouble(0.8, 1.04); radius = rng.nextDouble(0.85, 1.15); tempK = rng.nextDouble(5200, 6000); lum = rng.nextDouble(0.6, 1.5); lifeGyr = rng.nextDouble(5.0, 12.0); }
        else if (r < 0.52) { klass = Star.SpectralClass.K; mass = rng.nextDouble(0.45, 0.8); radius = rng.nextDouble(0.6, 0.85); tempK = rng.nextDouble(3700, 5200); lum = rng.nextDouble(0.08, 0.6); lifeGyr = rng.nextDouble(15, 30); }
        else if (r < 0.70) { klass = Star.SpectralClass.M; mass = rng.nextDouble(0.08, 0.45); radius = rng.nextDouble(0.1, 0.6); tempK = rng.nextDouble(2400, 3700); lum = rng.nextDouble(0.001, 0.08); lifeGyr = rng.nextDouble(50, 500); }
        else if (r < 0.85) { klass = Star.SpectralClass.GIANT; mass = rng.nextDouble(0.8, 8); radius = rng.nextDouble(10, 100); tempK = rng.nextDouble(3500, 5500); lum = rng.nextDouble(50, 2000); lifeGyr = rng.nextDouble(0.1, 2); }
        else { klass = Star.SpectralClass.SUPERGIANT; mass = rng.nextDouble(8, 70); radius = rng.nextDouble(100, 1500); tempK = rng.nextDouble(3000, 12000); lum = rng.nextDouble(1000, 1000000); lifeGyr = rng.nextDouble(0.001, 0.05); }

        double age = rng.nextDouble(0.05, lifeGyr);
        double metallicity = rng.nextDouble(-1, 0.5);
        boolean isMainSequence = klass.ordinal() <= Star.SpectralClass.M.ordinal();

        return new Star(starId, seed, klass, mass, radius, tempK, lum, age, metallicity, isMainSequence);
    }
}
