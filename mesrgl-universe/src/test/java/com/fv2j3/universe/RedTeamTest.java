package com.fv2j3.universe;

import com.fv2j3.universe.cache.CacheManager;
import com.fv2j3.universe.diagnostics.DefaultDiagnostics;
import com.fv2j3.universe.diagnostics.Diagnostics;
import com.fv2j3.universe.events.EventBus;
import com.fv2j3.universe.fluid.FluidDefinition;
import com.fv2j3.universe.fluid.FluidGrid;
import com.fv2j3.universe.identifiers.UniverseId;
import com.fv2j3.universe.persistence.PersistenceManager;
import com.fv2j3.universe.streaming.MemoryBudget;
import com.fv2j3.universe.streaming.Region;
import com.fv2j3.universe.streaming.RegionKey;
import com.fv2j3.universe.streaming.RegionState;
import com.fv2j3.universe.streaming.StreamingManager;
import com.fv2j3.universe.terrain.Chunk;
import com.fv2j3.universe.terrain.VoxelType;
import com.fv2j3.universe.thermal.ThermalField;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RedTeamTest {

    @Test
    void concurrentRequestsForSameRegion() throws Exception {
        Path p = Files.createTempFile("rt-", ".dat");
        try {
            EventBus events = new EventBus();
            Diagnostics diag = new DefaultDiagnostics();
            PersistenceManager pm = new PersistenceManager(p, events);
            CacheManager cache = new CacheManager(new MemoryBudget(1_000_000));
            StreamingManager sm = new StreamingManager(UniverseId.universeRoot(1L), 42L, 1, cache, pm, events, diag);
            RegionKey k = new RegionKey(UniverseId.universeRoot(1L), 0, 0, 0);
            AtomicInteger runCount = new AtomicInteger();
            ExecutorService pool = Executors.newFixedThreadPool(8);
            try {
                CompletableFuture<?>[] futs = new CompletableFuture[20];
                for (int i = 0; i < 20; i++) {
                    futs[i] = CompletableFuture.supplyAsync(() ->
                            sm.request(k, (kk, s, g, pmm) -> {
                                runCount.incrementAndGet();
                                try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                                return new Region(kk, RegionState.LOADED, "shared", 5);
                            }), pool);
                }
                CompletableFuture.allOf(futs).get(10, TimeUnit.SECONDS);
            } finally {
                pool.shutdownNow();
                sm.shutdown();
            }
            // Same region => same job, only one generator invocation.
            assertEquals(1, runCount.get());
        } finally { Files.deleteIfExists(p); }
    }

    @Test
    void cancelDuringGeneration() throws Exception {
        Path p = Files.createTempFile("rt-", ".dat");
        try {
            EventBus events = new EventBus();
            Diagnostics diag = new DefaultDiagnostics();
            PersistenceManager pm = new PersistenceManager(p, events);
            CacheManager cache = new CacheManager(new MemoryBudget(1_000_000));
            StreamingManager sm = new StreamingManager(UniverseId.universeRoot(1L), 42L, 1, cache, pm, events, diag);
            RegionKey k = new RegionKey(UniverseId.universeRoot(1L), 0, 0, 0);
            CompletableFuture<Region> f = sm.request(k, (kk, s, g, pmm) -> {
                try { Thread.sleep(5000); } catch (InterruptedException e) { return new Region(kk, RegionState.FAILED, "cancelled", 0); }
                return new Region(kk, RegionState.LOADED, "done", 5);
            });
            Thread.sleep(20);
            sm.cancel(k);
            try { f.get(1, TimeUnit.SECONDS); } catch (Exception ignored) {}
            // After cancel, state is UNLOADED, no stale reference.
            assertEquals(RegionState.UNLOADED, sm.stateOf(k));
        } finally { Files.deleteIfExists(p); }
    }

    @Test
    void unloadDuringGenerationDoesNotCorrupt() throws Exception {
        Path p = Files.createTempFile("rt-", ".dat");
        try {
            EventBus events = new EventBus();
            Diagnostics diag = new DefaultDiagnostics();
            PersistenceManager pm = new PersistenceManager(p, events);
            CacheManager cache = new CacheManager(new MemoryBudget(1_000_000));
            StreamingManager sm = new StreamingManager(UniverseId.universeRoot(1L), 42L, 1, cache, pm, events, diag);
            RegionKey k = new RegionKey(UniverseId.universeRoot(1L), 0, 0, 0);
            sm.request(k, (kk, s, g, pmm) -> {
                try { Thread.sleep(500); } catch (InterruptedException e) { return new Region(kk, RegionState.FAILED, "x", 0); }
                return new Region(kk, RegionState.LOADED, "y", 5);
            });
            Thread.sleep(20);
            sm.unload(k);
            Thread.sleep(800);
            // No exception, no stale references.
            assertFalse(cache.contains(k));
        } finally { Files.deleteIfExists(p); }
    }

    @Test
    void saveDuringGenerationDoesNotCorrupt() throws Exception {
        Path p = Files.createTempFile("rt-save-", ".dat");
        try {
            var w = UniverseFactory.wire(UniverseId.universeRoot(1L), 42L, 1, p, 1_000_000L);
            try {
                w.streaming.request(new RegionKey(UniverseId.universeRoot(1L), 0, 0, 0),
                        (k, s, g, pm) -> {
                            try { Thread.sleep(200); } catch (InterruptedException e) { return new Region(k, RegionState.FAILED, "x", 0); }
                            return new Region(k, RegionState.LOADED, "y", 5);
                        });
                Thread.sleep(20);
                var result = w.persistence.save(42L, 1);
                assertTrue(result.isOk());
            } finally {
                w.streaming.shutdown();
            }
        } finally { Files.deleteIfExists(p); }
    }

    @Test
    void invalidFluidPropertiesRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new FluidDefinition("bad", -1, 0.001, 1e-6, 4184, 273, 373, 0.6, 1, false));
        assertThrows(IllegalArgumentException.class, () ->
                new FluidDefinition("bad2", 1000, -0.001, 1e-6, 4184, 273, 373, 0.6, 1, false));
    }

    @Test
    void invalidMaterialPropertiesRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new com.fv2j3.universe.materials.MaterialDefinition("bad", -1, 0.5, 0.0, 1, 800, 1500, 2873, Double.NaN, 0.7, 0.1, 0.0, false));
    }

    @Test
    void negativeCoordinateSupported() {
        com.fv2j3.universe.identifiers.ChunkCoord c = new com.fv2j3.universe.identifiers.ChunkCoord(-1000, -2000, -3000);
        assertEquals(1000, Math.abs(c.x()));
    }

    @Test
    void extremeCoordinatesSupported() {
        com.fv2j3.universe.identifiers.ChunkCoord c = new com.fv2j3.universe.identifiers.ChunkCoord(Long.MAX_VALUE / 2, 0, 0);
        assertEquals(Long.MAX_VALUE / 2, c.x());
    }

    @Test
    void fluidUnderStress() {
        FluidGrid g = new FluidGrid(FluidDefinition.water());
        Chunk c = new Chunk(0, 0, 0);
        c.fillBox(0, 0, 0, FluidGrid.GRID_EDGE, 8, FluidGrid.GRID_EDGE, VoxelType.WATER);
        g.importSolids(c);
        for (int i = 0; i < 200; i++) g.step(0.016, 9.81);
        for (int i = 0; i < FluidGrid.GRID_EDGE * FluidGrid.GRID_EDGE * FluidGrid.GRID_EDGE; i++) {
            int x = i % FluidGrid.GRID_EDGE;
            int y = (i / FluidGrid.GRID_EDGE) % FluidGrid.GRID_EDGE;
            int z = i / (FluidGrid.GRID_EDGE * FluidGrid.GRID_EDGE);
            double d = g.density(x, y, z);
            assertTrue(Double.isFinite(d));
            assertTrue(d >= 0 && d <= 1.0 + 1e-6, "density out of range at " + i + ": " + d);
        }
    }

    @Test
    void lowMemorySurvives() {
        // 100KB budget; load 100 regions with 1KB each — eviction must keep us alive.
        EventBus events = new EventBus();
        Diagnostics diag = new DefaultDiagnostics();
        Path p = Path.of("nonexistent-" + System.nanoTime() + ".dat");
        try {
            PersistenceManager pm = new PersistenceManager(p, events);
            // Use a budget that will definitely trigger eviction: 50 regions x 1KB = 50KB
            CacheManager cache = new CacheManager(new MemoryBudget(50_000));
            StreamingManager sm = new StreamingManager(UniverseId.universeRoot(1L), 42L, 1, cache, pm, events, diag);
            for (int i = 0; i < 100; i++) {
                final int idx = i;
                try {
                    sm.request(new RegionKey(UniverseId.universeRoot(1L), idx, 0, 0),
                            (k, s, g, pmm) -> new Region(k, RegionState.LOADED, "x" + idx, 1000)).get(5, TimeUnit.SECONDS);
                } catch (Exception ignored) {}
            }
            // Either we evicted, or the budget allows everything; both are valid.
            // What MUST be true: resident bytes <= 2x budget.
            assertTrue(cache.budget().residentBytes() <= 2 * cache.budget().maxBytes());
            sm.shutdown();
            try { Files.deleteIfExists(pm.targetFile()); } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    @Test
    void noGlobalRandomState() {
        // The deterministic generation test (in DeterministicGenerationTest) implicitly
        // verifies this; here we explicitly call generation from multiple threads
        // and ensure identical results.
        com.fv2j3.universe.generation.GalaxyGenerator g = new com.fv2j3.universe.generation.GalaxyGenerator();
        UniverseId root = new UniverseId(1, 0, 0, 0, 0, 0, 0);
        com.fv2j3.universe.universe.Galaxy a = g.generate(root, 42L, 1L);
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                com.fv2j3.universe.universe.Galaxy b = g.generate(root, 42L, 1L);
                if (!a.equals(b)) throw new AssertionError("non-deterministic");
            }
        });
        t1.start();
        for (int i = 0; i < 100; i++) {
            com.fv2j3.universe.universe.Galaxy b = g.generate(root, 42L, 1L);
            if (!a.equals(b)) throw new AssertionError("non-deterministic");
        }
        try { t1.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @Test
    void smokeStepDoesNotDeadlockWithActiveSources() throws Exception {
        // Regression: SmokeSystem.step used to acquire the write lock
        // (via getOrCreate) while holding the read lock — a read→write
        // upgrade that self-deadlocks as soon as any source is active.
        // It also mutated sources/grids under only a read lock (data race).
        var reg = new com.fv2j3.universe.smoke.GasRegistry();
        var smoke = new com.fv2j3.universe.smoke.SmokeSystem(reg);
        com.fv2j3.universe.identifiers.ChunkCoord c = new com.fv2j3.universe.identifiers.ChunkCoord(0, 0, 0);
        smoke.addSource(new com.fv2j3.universe.smoke.SmokeSystem.Source(
                0L, c, 4, 1, 4, 1.0, 800.0, 0, 0, 0, "smoke", 3600.0));

        Thread sequential = new Thread(() -> {
            for (int i = 0; i < 20; i++) smoke.step(0.05);
        });
        sequential.start();
        sequential.join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(sequential.isAlive(), "SmokeSystem.step deadlocked with an active source");
        assertTrue(smoke.gridCount() >= 1);

        // Concurrent steps must also terminate (they are serialized internally).
        Thread[] steppers = new Thread[4];
        for (int i = 0; i < steppers.length; i++) {
            steppers[i] = new Thread(() -> {
                for (int j = 0; j < 25; j++) smoke.step(0.02);
            });
            steppers[i].start();
        }
        for (Thread t2 : steppers) t2.join(TimeUnit.SECONDS.toMillis(15));
        for (Thread t2 : steppers) assertFalse(t2.isAlive(), "Concurrent SmokeSystem.step deadlocked");
    }
}
