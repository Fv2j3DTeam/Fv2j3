package com.fv2j3.universe;

import com.fv2j3.universe.events.EventBus;
import com.fv2j3.universe.identifiers.UniverseId;
import com.fv2j3.universe.streaming.*;
import com.fv2j3.universe.streaming.Region;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class StreamingIntegrationTest {

    @Test
    void manyRegionsLoadInOrder() throws Exception {
        Path p = Files.createTempFile("stream-many-", ".dat");
        try {
            var w = UniverseFactory.wire(UniverseId.universeRoot(1L), 42L,
                    com.fv2j3.universe.seeds.SeedDerivation.GENERATOR_VERSION, p, 1_000_000L);
            try {
                for (int i = 0; i < 20; i++) {
                    final int idx = i;
                    RegionKey key = new RegionKey(w.universe.universeId(), idx, 0, 0);
                    Region r = w.streaming.request(key, (k, seed, gen, pm) -> new Region(k, RegionState.LOADED, "v" + idx, 10)).get(5, TimeUnit.SECONDS);
                    assertEquals("v" + idx, r.content());
                }
                assertTrue(w.cache.size() >= 1);
            } finally {
                w.streaming.shutdown();
            }
        } finally { Files.deleteIfExists(p); }
    }

    @Test
    void repeatedTraversalRespectsMemory() throws Exception {
        Path p = Files.createTempFile("stream-traverse-", ".dat");
        try {
            var w = UniverseFactory.wire(UniverseId.universeRoot(1L), 42L,
                    com.fv2j3.universe.seeds.SeedDerivation.GENERATOR_VERSION, p, 5_000L);
            try {
                for (int rep = 0; rep < 30; rep++) {
                    for (int i = 0; i < 10; i++) {
                        final int idx = i;
                        RegionKey key = new RegionKey(w.universe.universeId(), idx, 0, 0);
                        try {
                            w.streaming.request(key, (k, seed, gen, pm) -> new Region(k, RegionState.LOADED, "x" + idx, 1000)).get(5, TimeUnit.SECONDS);
                        } catch (Exception e) { /* may be cancelled */ }
                    }
                    w.cache.evictions(); // ensure no error
                }
                // Resident memory should be roughly at or under budget.
                assertTrue(w.memoryBudget.residentBytes() <= 2 * w.memoryBudget.maxBytes());
            } finally {
                w.streaming.shutdown();
            }
        } finally { Files.deleteIfExists(p); }
    }
}
