package com.fv2j3.universe;

import com.fv2j3.universe.identifiers.UniverseId;
import com.fv2j3.universe.seeds.SeedDerivation;
import com.fv2j3.universe.math.XorShift64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SeedDerivationTest {

    @Test
    void deterministicSameSeedSameId() {
        long root = 42L;
        UniverseId a = new UniverseId(1, 2, 3, 4, 5, 6, 7);
        UniverseId b = new UniverseId(1, 2, 3, 4, 5, 6, 7);
        assertEquals(SeedDerivation.deriveSeed(root, a), SeedDerivation.deriveSeed(root, b));
    }

    @Test
    void differentIdsProduceDifferentSeeds() {
        long root = 42L;
        UniverseId a = new UniverseId(1, 2, 3, 4, 5, 6, 7);
        UniverseId b = new UniverseId(1, 2, 3, 4, 5, 6, 8);
        assertNotEquals(SeedDerivation.deriveSeed(root, a), SeedDerivation.deriveSeed(root, b));
    }

    @Test
    void generationVersionIsolated() {
        long root = 42L;
        UniverseId a = new UniverseId(1, 2, 3, 4, 5, 6, 7);
        long s1 = SeedDerivation.deriveUniverseSeed(root);
        assertNotEquals(0L, s1);
    }

    @Test
    void prngIsIndependent() {
        long root = 42L;
        UniverseId a = new UniverseId(1, 2, 3, 4, 5, 6, 7);
        XorShift64 r1 = SeedDerivation.prng(root, a);
        XorShift64 r2 = SeedDerivation.prng(root, a);
        // Same seed => same stream
        for (int i = 0; i < 16; i++) {
            assertEquals(r1.nextLong(), r2.nextLong());
        }
    }

    @Test
    void differentGalaxyProducesDifferentStream() {
        long root = 42L;
        UniverseId a = new UniverseId(1, 1, 0, 0, 0, 0, 0);
        UniverseId b = new UniverseId(1, 2, 0, 0, 0, 0, 0);
        XorShift64 ra = SeedDerivation.prng(root, a);
        XorShift64 rb = SeedDerivation.prng(root, b);
        assertNotEquals(ra.nextLong(), rb.nextLong());
    }
}
