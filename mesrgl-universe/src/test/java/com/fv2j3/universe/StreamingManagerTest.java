package com.fv2j3.universe;

import com.fv2j3.universe.cache.CacheManager;
import com.fv2j3.universe.diagnostics.DefaultDiagnostics;
import com.fv2j3.universe.diagnostics.Diagnostics;
import com.fv2j3.universe.events.EventBus;
import com.fv2j3.universe.identifiers.UniverseId;
import com.fv2j3.universe.persistence.PersistenceManager;
import com.fv2j3.universe.streaming.*;
import com.fv2j3.universe.streaming.Region;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class StreamingManagerTest {

    private Path tmp;
    private EventBus events;
    private Diagnostics diagnostics;
    private PersistenceManager persistence;
    private StreamingManager streaming;
    private CacheManager cache;
    private UniverseId uid;
    private UniverseFactory.Wired wired;

    @BeforeEach
    void setup() throws Exception {
        tmp = Files.createTempFile("stream-test-", ".dat");
        wired = UniverseFactory.wire(UniverseId.universeRoot(1L), 42L,
                com.fv2j3.universe.seeds.SeedDerivation.GENERATOR_VERSION,
                tmp, 10_000_000L);
        events = wired.events; diagnostics = wired.diagnostics; persistence = wired.persistence;
        streaming = wired.streaming; cache = wired.cache; uid = wired.universe.universeId();
    }

    @AfterEach
    void teardown() throws Exception {
        streaming.shutdown();
        if (tmp != null) Files.deleteIfExists(tmp);
    }

    @Test
    void requestLoadsRegion() throws Exception {
        RegionKey key = new RegionKey(uid, 0, 0, 0);
        CompletableFuture<Region> f = streaming.request(key, (k, seed, gen, pm) -> new Region(k, RegionState.LOADED, "payload", 5));
        Region r = f.get(5, TimeUnit.SECONDS);
        assertEquals("payload", r.content());
        assertEquals(RegionState.LOADED, streaming.stateOf(key));
        assertTrue(cache.contains(key));
    }

    @Test
    void unloadRemovesRegion() throws Exception {
        RegionKey key = new RegionKey(uid, 0, 0, 0);
        streaming.request(key, (k, seed, gen, pm) -> new Region(k, RegionState.LOADED, "x", 5)).get(5, TimeUnit.SECONDS);
        streaming.unload(key);
        assertEquals(RegionState.UNLOADED, streaming.stateOf(key));
    }

    @Test
    void duplicateRequestsCoalesce() throws Exception {
        RegionKey key = new RegionKey(uid, 0, 0, 0);
        CompletableFuture<Region> f1 = streaming.request(key, (k, seed, gen, pm) -> {
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return new Region(k, RegionState.LOADED, "shared", 5);
        });
        CompletableFuture<Region> f2 = streaming.request(key, (k, seed, gen, pm) -> {
            fail("second request should not run");
            return null;
        });
        Region r1 = f1.get(5, TimeUnit.SECONDS);
        Region r2 = f2.get(5, TimeUnit.SECONDS);
        assertSame(r1, r2);
    }

    @Test
    void cancelStopsGeneration() throws Exception {
        RegionKey key = new RegionKey(uid, 1, 0, 0);
        CompletableFuture<Region> f = streaming.request(key, (k, seed, gen, pm) -> {
            try { Thread.sleep(5000); } catch (InterruptedException e) { return new Region(k, RegionState.FAILED, "cancelled", 0); }
            return new Region(k, RegionState.LOADED, "done", 5);
        });
        Thread.sleep(50);
        streaming.cancel(key);
        try {
            f.get(2, TimeUnit.SECONDS);
            fail("should have been cancelled");
        } catch (Exception e) {
            // expected
        }
        assertEquals(RegionState.UNLOADED, streaming.stateOf(key));
    }

    @Test
    void invalidTransitionRejected() {
        RegionKey key = new RegionKey(uid, 0, 0, 0);
        // We test the underlying enum logic.
        assertTrue(RegionState.UNLOADED.canTransitionTo(RegionState.QUEUED));
        assertTrue(RegionState.QUEUED.canTransitionTo(RegionState.GENERATING));
        assertTrue(RegionState.GENERATING.canTransitionTo(RegionState.LOADING));
        assertTrue(RegionState.LOADING.canTransitionTo(RegionState.LOADED));
        assertFalse(RegionState.LOADED.canTransitionTo(RegionState.GENERATING));
        assertFalse(RegionState.UNLOADED.canTransitionTo(RegionState.ACTIVE));
    }

    @Test
    void streamingReportsDiagnostics() throws Exception {
        RegionKey key = new RegionKey(uid, 0, 0, 0);
        streaming.request(key, (k, seed, gen, pm) -> new Region(k, RegionState.LOADED, "x", 5)).get(5, TimeUnit.SECONDS);
        Diagnostics d = diagnostics;
        assertTrue(d.counter("streaming.generation.completed") >= 1L);
    }
}
