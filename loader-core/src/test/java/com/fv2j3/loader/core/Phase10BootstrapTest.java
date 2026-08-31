package com.fv2j3.loader.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fv2j3.minecraft.compat.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Phase10BootstrapTest {
    @Test
    void detectMinecraftRuntimeWhenJarExists() throws IOException {
        Path root = Files.createTempDirectory("fv2j3-mc");
        Path versionDir = root.resolve("versions").resolve("1.12.2");
        Files.createDirectories(versionDir);
        Files.createFile(versionDir.resolve("1.12.2.jar"));

        MinecraftBootstrap.MinecraftBootstrapResult result = MinecraftBootstrap.detect(root);

        assertTrue(result.available());
        assertEquals("1.12.2", result.version());
        assertNotNull(result.runtimeHome());
        assertFalse(result.candidates().isEmpty());
    }

    @Test
    void reportMissingMinecraftRuntimeWithoutClaimingSuccess() throws IOException {
        Path root = Files.createTempDirectory("fv2j3-empty");

        MinecraftBootstrap.MinecraftBootstrapResult result = MinecraftBootstrap.detect(root);

        assertFalse(result.available());
        assertTrue(result.message().contains("1.12.2"));
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void benchmarkCapturesTimingStatsForStartupProfiling() {
        PerformanceBenchmark.BenchmarkSnapshot snapshot = PerformanceBenchmark.snapshot("loader_startup", () -> {
            try {
                Thread.sleep(1L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }, 5);

        assertEquals(5, snapshot.samples());
        assertTrue(snapshot.averageNanos() >= 0L);
        assertTrue(snapshot.minNanos() >= 0L);
        assertTrue(snapshot.maxNanos() >= snapshot.minNanos());
    }
}
