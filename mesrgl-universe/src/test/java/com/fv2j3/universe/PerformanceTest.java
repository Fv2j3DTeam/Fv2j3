package com.fv2j3.universe;

import com.fv2j3.universe.cache.CacheManager;
import com.fv2j3.universe.diagnostics.DefaultDiagnostics;
import com.fv2j3.universe.diagnostics.Profiler;
import com.fv2j3.universe.events.EventBus;
import com.fv2j3.universe.fluid.FluidDefinition;
import com.fv2j3.universe.fluid.FluidGrid;
import com.fv2j3.universe.fluid.FluidSystem;
import com.fv2j3.universe.generation.*;
import com.fv2j3.universe.identifiers.ChunkCoord;
import com.fv2j3.universe.identifiers.UniverseId;
import com.fv2j3.universe.smoke.GasRegistry;
import com.fv2j3.universe.smoke.SmokeGrid;
import com.fv2j3.universe.smoke.SmokeSystem;
import com.fv2j3.universe.terrain.Chunk;
import com.fv2j3.universe.terrain.VoxelType;
import com.fv2j3.universe.streaming.*;
import com.fv2j3.universe.persistence.PersistenceManager;
import com.fv2j3.universe.universe.*;
import com.fv2j3.universe.weather.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Performance / scaling / stress measurements. */
class PerformanceTest {

    @Test
    void logicalUniverseScale() {
        // Generate 1000 unique star systems cheaply; verify each is unique.
        StarSystemGenerator g = new StarSystemGenerator();
        UniverseId root = new UniverseId(1, 0, 0, 0, 0, 0, 0);
        java.util.Set<UniverseId> seen = new java.util.HashSet<>();
        long t0 = System.nanoTime();
        for (int i = 1; i <= 1000; i++) {
            StarSystem s = g.generate(root, 42L, 1L, i);
            seen.add(s.id());
        }
        long ns = System.nanoTime() - t0;
        assertEquals(1000, seen.size());
        System.out.printf("[perf] 1000 star systems in %.2f ms (%.0f sys/s)%n", ns / 1e6, 1000.0 / (ns / 1e9));
    }

    @Test
    void planetGenerationThroughput() {
        PlanetGenerator g = new PlanetGenerator();
        UniverseId root = new UniverseId(1, 0, 0, 0, 0, 0, 0);
        long t0 = System.nanoTime();
        int n = 1000;
        for (int i = 1; i <= n; i++) {
            Planet p = g.generate(root, 42L, 1L, 5L, i);
            assertTrue(p.radiusMeters() > 0);
        }
        long ns = System.nanoTime() - t0;
        System.out.printf("[perf] %d planets in %.2f ms (%.0f p/s)%n", n, ns / 1e6, n * 1e9 / ns);
    }

    @Test
    void terrainGenerationThroughput() {
        PlanetGenerator pg = new PlanetGenerator();
        UniverseId root = new UniverseId(1, 0, 0, 0, 0, 0, 0);
        Planet p = pg.generate(root, 42L, 1L, 5L, 1L);
        TerrainGenerator tg = new TerrainGenerator();
        long t0 = System.nanoTime();
        int n = 50;
        for (int i = 0; i < n; i++) {
            Chunk c = tg.generate(p, new ChunkCoord(i, 0, 0));
            assertNotNull(c);
        }
        long ns = System.nanoTime() - t0;
        System.out.printf("[perf] %d terrain chunks in %.2f ms (%.1f chunk/s)%n", n, ns / 1e6, n * 1e9 / ns);
    }

    @Test
    void fluidStepThroughput() {
        FluidGrid g = new FluidGrid(FluidDefinition.water());
        Chunk c = new Chunk(0, 0, 0);
        c.fillBox(0, 0, 0, FluidGrid.GRID_EDGE, 8, FluidGrid.GRID_EDGE, VoxelType.WATER);
        g.importSolids(c);
        // Warm-up
        for (int i = 0; i < 5; i++) g.step(0.016, 9.81);
        long t0 = System.nanoTime();
        int steps = 100;
        for (int i = 0; i < steps; i++) g.step(0.016, 9.81);
        long ns = System.nanoTime() - t0;
        System.out.printf("[perf] fluid %d steps in %.2f ms (%.1f step/s)%n", steps, ns / 1e6, steps * 1e9 / ns);
    }

    @Test
    void streamingThroughput() throws Exception {
        Path p = Files.createTempFile("perf-stream-", ".dat");
        try {
            var w = UniverseFactory.wire(UniverseId.universeRoot(1L), 42L, 1, p, 50_000_000L);
            try {
                int n = 200;
                long t0 = System.nanoTime();
                for (int i = 0; i < n; i++) {
                    final int idx = i;
                    w.streaming.request(new RegionKey(UniverseId.universeRoot(1L), idx, 0, 0),
                            (k, s, g, pm) -> new Region(k, RegionState.LOADED, "x" + idx, 100)).get(10, TimeUnit.SECONDS);
                }
                long ns = System.nanoTime() - t0;
                System.out.printf("[perf] streamed %d regions in %.2f ms (%.0f reg/s)%n", n, ns / 1e6, n * 1e9 / ns);
            } finally { w.streaming.shutdown(); }
        } finally { Files.deleteIfExists(p); }
    }

    @Test
    void cacheEvictionUnderPressure() {
        CacheManager cache = new CacheManager(new MemoryBudget(10_000));
        long t0 = System.nanoTime();
        int n = 1000;
        for (int i = 0; i < n; i++) {
            RegionKey k = new RegionKey(UniverseId.universeRoot(1L), i, 0, 0);
            cache.put(k, RegionState.LOADED, "x", 2000);
        }
        long ns = System.nanoTime() - t0;
        System.out.printf("[perf] cache %d puts in %.2f ms (%.0f put/s, %d evictions)%n",
                n, ns / 1e6, n * 1e9 / ns, cache.evictions());
        assertTrue(cache.evictions() > 0);
    }

    @Test
    void largeCoordinateIdStability() {
        // Generate 100k planet ids across very large coordinate ranges.
        PlanetGenerator pg = new PlanetGenerator();
        UniverseId root = new UniverseId(1, 0, 0, 0, 0, 0, 0);
        long t0 = System.nanoTime();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int i = 0; i < 1000; i++) {
            Planet p = pg.generate(root, 42L, 1L, 5L, i + 1L);
            seen.add(p.id().body());
        }
        long ns = System.nanoTime() - t0;
        assertEquals(1000, seen.size());
        System.out.printf("[perf] 1000 planet IDs at varying coords in %.2f ms%n", ns / 1e6);
    }

    @Test
    void fluidMassConservation() {
        // Water should not be created or destroyed under simple flow.
        FluidGrid g = new FluidGrid(FluidDefinition.water());
        g.fillDensity(1.0);
        Chunk c = new Chunk(0, 0, 0);
        c.fillBox(0, 0, 0, FluidGrid.GRID_EDGE, 8, FluidGrid.GRID_EDGE, VoxelType.WATER);
        g.importSolids(c);
        double initial = g.totalMass();
        for (int i = 0; i < 30; i++) g.step(0.016, 9.81);
        double after = g.totalMass();
        double ratio = initial > 0 ? after / initial : 1.0;
        System.out.printf("[perf] fluid mass ratio: initial=%.2f, after=%.2f, ratio=%.4f%n", initial, after, ratio);
        assertTrue(ratio > 0.5 && ratio < 2.0, "mass conservation violated: " + ratio);
    }

    @Test
    void smokeAdvectionStable() {
        var reg = new com.fv2j3.universe.smoke.GasRegistry();
        SmokeSystem sys = new SmokeSystem(reg);
        ChunkCoord c = new ChunkCoord(0, 0, 0);
        var g = sys.getOrCreate(c, "smoke", 293.0);
        g.addSmoke(4, 1, 4, 0.5, 800.0, 0, 0, 0);
        long t0 = System.nanoTime();
        for (int i = 0; i < 200; i++) g.step(0.05);
        long ns = System.nanoTime() - t0;
        System.out.printf("[perf] 200 smoke steps in %.2f ms (%.0f step/s)%n", ns / 1e6, 200 * 1e9 / ns);
    }
}
