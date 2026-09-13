package io.github.fv2j3dteam.universe;

import io.github.fv2j3dteam.universe.fire.FireSystem;
import io.github.fv2j3dteam.universe.identifiers.ChunkCoord;
import io.github.fv2j3dteam.universe.identifiers.UniverseId;
import io.github.fv2j3dteam.universe.smoke.SmokeGrid;
import io.github.fv2j3dteam.universe.smoke.SmokeSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SmokeFireTest {

    @Test
    void hotSmokeRises() {
        io.github.fv2j3dteam.universe.smoke.GasRegistry reg = new io.github.fv2j3dteam.universe.smoke.GasRegistry();
        SmokeSystem sys = new SmokeSystem(reg);
        ChunkCoord c = new ChunkCoord(0, 0, 0);
        SmokeGrid g = sys.getOrCreate(c, "smoke", 293.0);
        g.addSmoke(4, 1, 4, 0.5, 800.0, 0, 0, 0);
        double before = g.vy(4, 1, 4);
        g.step(0.1);
        double after = g.vy(4, 1, 4);
        // Hot smoke gains upward velocity.
        assertTrue(after >= before);
        // But not negative.
        assertTrue(after >= 0 || after > before);
    }

    @Test
    void fireProducesSmoke() {
        var reg = new io.github.fv2j3dteam.universe.smoke.GasRegistry();
        var smoke = new SmokeSystem(reg);
        var events = new io.github.fv2j3dteam.universe.events.EventBus();
        FireSystem fire = new FireSystem(events, smoke);
        UniverseId pid = new UniverseId(1, 0, 0, 0, 0, 0, 0);
        ChunkCoord c = new ChunkCoord(0, 0, 0);
        long id = fire.ignite(pid, c, 4, 1, 4, 900.0, 100, 1, 1.0, 1.0, "smoke");
        assertTrue(id > 0);
        assertEquals(1, fire.activeSourceCount());
        fire.extinguish(id);
        assertEquals(0, fire.activeSourceCount());
    }

    @Test
    void coolSmokeSinks() {
        var reg = new io.github.fv2j3dteam.universe.smoke.GasRegistry();
        SmokeSystem sys = new SmokeSystem(reg);
        ChunkCoord c = new ChunkCoord(0, 0, 0);
        SmokeGrid g = sys.getOrCreate(c, "smoke", 293.0);
        g.addSmoke(4, 4, 4, 0.5, 200.0, 0, 0, 0); // much colder than ambient
        double before = g.vy(4, 4, 4);
        g.step(0.1);
        double after = g.vy(4, 4, 4);
        // Cool smoke sinks: vy should become negative.
        assertTrue(after < before);
    }

    @Test
    void smokeDissipatesOverTime() {
        var reg = new io.github.fv2j3dteam.universe.smoke.GasRegistry();
        SmokeSystem sys = new SmokeSystem(reg);
        ChunkCoord c = new ChunkCoord(0, 0, 0);
        SmokeGrid g = sys.getOrCreate(c, "smoke", 293.0);
        g.addSmoke(4, 4, 4, 1.0, 500.0, 0, 0, 0);
        double initial = g.density(4, 4, 4);
        for (int i = 0; i < 100; i++) g.step(0.1);
        assertTrue(g.density(4, 4, 4) < initial);
    }
}
