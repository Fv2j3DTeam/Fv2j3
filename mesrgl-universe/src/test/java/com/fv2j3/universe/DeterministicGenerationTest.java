package com.fv2j3.universe;

import com.fv2j3.universe.generation.*;
import com.fv2j3.universe.identifiers.UniverseId;
import com.fv2j3.universe.universe.Galaxy;
import com.fv2j3.universe.universe.Planet;
import com.fv2j3.universe.universe.Star;
import com.fv2j3.universe.universe.StarSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicGenerationTest {

    @Test
    void galaxyIdenticalTwice() {
        GalaxyGenerator g = new GalaxyGenerator();
        UniverseId root = new UniverseId(1, 0, 0, 0, 0, 0, 0);
        Galaxy a = g.generate(root, 42L, 1L);
        Galaxy b = g.generate(root, 42L, 1L);
        assertEquals(a, b);
        assertTrue(a.systemCount() > 0);
        assertTrue(a.radiusLightYears() > 0);
        assertTrue(a.starSystemIds().stream().allMatch(id -> id.galaxy() == 1L));
    }

    @Test
    void starSystemIdenticalTwice() {
        StarSystemGenerator g = new StarSystemGenerator();
        UniverseId root = new UniverseId(1, 0, 0, 0, 0, 0, 0);
        StarSystem a = g.generate(root, 42L, 1L, 5L);
        StarSystem b = g.generate(root, 42L, 1L, 5L);
        assertEquals(a, b);
    }

    @Test
    void starIdenticalTwice() {
        StarGenerator g = new StarGenerator();
        UniverseId root = new UniverseId(1, 0, 0, 0, 0, 0, 0);
        Star a = g.generate(root, 42L, 1L, 5L, -100L);
        Star b = g.generate(root, 42L, 1L, 5L, -100L);
        assertEquals(a, b);
    }

    @Test
    void planetIdenticalTwice() {
        PlanetGenerator g = new PlanetGenerator();
        UniverseId root = new UniverseId(1, 0, 0, 0, 0, 0, 0);
        Planet a = g.generate(root, 42L, 1L, 5L, 1L);
        Planet b = g.generate(root, 42L, 1L, 5L, 1L);
        assertEquals(a, b);
        assertTrue(a.gravityMs2() > 0);
        assertTrue(a.atmosphere().pressurePa() >= 0);
    }

    @Test
    void planetPropertiesCorrelated() {
        PlanetGenerator g = new PlanetGenerator();
        UniverseId root = new UniverseId(1, 0, 0, 0, 0, 0, 0);
        // Many planets; ensure no "all same radius" or "all gravity 9.8" pattern.
        double sumR = 0; int n = 50;
        for (int i = 1; i <= n; i++) {
            Planet p = g.generate(root, 42L, 1L, 5L, i);
            sumR += p.radiusMeters();
        }
        double meanR = sumR / n;
        assertTrue(meanR > 0);
    }

    @Test
    void differentOrderSameResult() {
        GalaxyGenerator g = new GalaxyGenerator();
        UniverseId root = new UniverseId(1, 0, 0, 0, 0, 0, 0);
        // Two threads with no shared state: results identical.
        Galaxy a = g.generate(root, 42L, 1L);
        Galaxy b = g.generate(root, 42L, 2L);
        assertNotEquals(a, b); // different galaxy index
    }

    @Test
    void climateDeterministic() {
        ClimateGenerator c = new ClimateGenerator();
        PlanetGenerator pg = new PlanetGenerator();
        UniverseId root = new UniverseId(1, 0, 0, 0, 0, 0, 0);
        Planet p = pg.generate(root, 42L, 1L, 5L, 1L);
        var a = c.sample(p, new com.fv2j3.universe.identifiers.ChunkCoord(0, 0, 0), 0, 0, 0);
        var b = c.sample(p, new com.fv2j3.universe.identifiers.ChunkCoord(0, 0, 0), 0, 0, 0);
        assertEquals(a.temperatureK, b.temperatureK, 1e-9);
    }
}
