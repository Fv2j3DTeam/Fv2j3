package com.fv2j3.universe.generation;

import com.fv2j3.universe.identifiers.UniverseId;
import com.fv2j3.universe.math.XorShift64;
import com.fv2j3.universe.seeds.SeedDerivation;
import com.fv2j3.universe.universe.Galaxy;

import java.util.ArrayList;
import java.util.List;

/** Galaxy generator (§7). Configurable shape and density. */
public final class GalaxyGenerator {
    public static final String STAGE = "galaxy";

    public Galaxy generate(UniverseId universeId, long universeRootSeed, long galaxyIndex) {
        UniverseId gid = universeId.withGalaxy(galaxyIndex);
        long seed = SeedDerivation.deriveSeed(universeRootSeed, gid);
        XorShift64 rng = new XorShift64(seed);
        Galaxy.Shape shape = pickShape(rng);
        double radius = pickRadius(rng, shape);
        int arms = pickArms(rng, shape);
        double metallicity = 0.5 + (rng.nextDouble() - 0.5) * 0.6; // 0.2..0.8 typical

        int systemCount = pickSystemCount(rng, shape, radius);
        List<UniverseId> systemIds = new ArrayList<>(systemCount);
        for (int i = 0; i < systemCount; i++) {
            systemIds.add(gid.withSystem(i + 1L));
        }
        return new Galaxy(gid, seed, shape, radius, arms, metallicity, systemIds);
    }

    public Galaxy.Shape pickShape(XorShift64 rng) {
        double r = rng.nextDouble();
        if (r < 0.55) return Galaxy.Shape.SPIRAL;
        if (r < 0.75) return Galaxy.Shape.ELLIPTICAL;
        if (r < 0.88) return Galaxy.Shape.BARRED_SPIRAL;
        if (r < 0.95) return Galaxy.Shape.IRREGULAR;
        if (r < 0.98) return Galaxy.Shape.CLUSTER;
        return Galaxy.Shape.LENTICULAR;
    }

    public double pickRadius(XorShift64 rng, Galaxy.Shape shape) {
        double base = switch (shape) {
            case SPIRAL, BARRED_SPIRAL -> rng.nextDouble(30000.0, 80000.0);
            case ELLIPTICAL -> rng.nextDouble(20000.0, 100000.0);
            case IRREGULAR -> rng.nextDouble(5000.0, 25000.0);
            case LENTICULAR -> rng.nextDouble(15000.0, 45000.0);
            case CLUSTER -> rng.nextDouble(20000.0, 60000.0);
            case CUSTOM -> rng.nextDouble(10000.0, 50000.0);
        };
        return base;
    }

    public int pickArms(XorShift64 rng, Galaxy.Shape shape) {
        return switch (shape) {
            case SPIRAL, BARRED_SPIRAL -> 2 + rng.nextInt(0, 4);
            default -> 0;
        };
    }

    public int pickSystemCount(XorShift64 rng, Galaxy.Shape shape, double radius) {
        // Density scales roughly with radius^2 for spirals; clamp into reasonable range.
        double base = radius * radius * 1.0e-6;
        double jitter = rng.nextDouble(0.5, 1.5);
        int n = (int) Math.round(base * jitter);
        if (shape == Galaxy.Shape.CLUSTER) n = (int) (n * 1.4);
        if (shape == Galaxy.Shape.IRREGULAR) n = (int) (n * 0.6);
        return Math.max(8, Math.min(200000, n));
    }
}
