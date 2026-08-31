package com.fv2j3.minecraft.compat.monitor;

import java.util.OptionalDouble;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemMonitorServiceTest {
    private final SystemMonitorService service = SystemMonitorService.forTesting();

    @AfterEach
    void stopService() {
        service.stop();
        assertTrue(SystemMonitorService.noSamplerThreadsAlive(), "sampler thread must not leak");
    }

    @Test
    void publishesSnapshotsInBackground() throws Exception {
        CountDownLatch sampled = new CountDownLatch(1);
        service.start(() -> {
            sampled.countDown();
            return new SystemStats(1, 2, 3,
                    OptionalDouble.of(0.25), OptionalDouble.empty(),
                    OptionalDouble.empty(), OptionalDouble.empty());
        });
        assertTrue(sampled.await(5, TimeUnit.SECONDS), "monitor should sample in the background");
        SystemStats stats = service.latest();
        assertNotNull(stats);
        assertEquals(0.25, stats.cpuUsage().getAsDouble(), 1e-9);
        assertEquals("25%", stats.cpuUsagePercent());
        assertEquals("N/A", stats.gpuUsagePercent());
        assertTrue(service.isRunning());
    }

    @Test
    void latestIsNullBeforeFirstSample() {
        service.start(() -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return SystemStats.unavailable(0, 0, 0);
        });
        // Sample never completed; render thread reads null and must handle it.
        assertNull(service.latest());
    }

    @Test
    void failingMonitorDoesNotCrashService() throws Exception {
        service.start(() -> {
            throw new IllegalStateException("simulated platform failure");
        });
        long deadline = System.currentTimeMillis() + 5000;
        while (service.latest() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        SystemStats stats = service.latest();
        assertNotNull(stats, "fallback memory-only stats should be published on monitor failure");
        assertEquals("N/A", stats.gpuUsagePercent());
        assertEquals("N/A", stats.cpuTemperatureCelsius());
        assertTrue(service.isRunning());
    }

    @Test
    void unavailableCpuGpuTemperatureHandledGracefully() throws Exception {
        service.start(SystemStats::sampleUnavailable);
        long deadline = System.currentTimeMillis() + 5000;
        while (service.latest() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        SystemStats stats = service.latest();
        assertNotNull(stats);
        assertEquals("N/A", stats.cpuUsagePercent());
        assertEquals("N/A", stats.gpuUsagePercent());
        assertEquals("N/A", stats.cpuTemperatureCelsius());
        assertEquals("N/A", stats.gpuTemperatureCelsius());
    }

    @Test
    void stopTerminatesSamplerThread() throws Exception {
        service.start(SystemStats::sampleUnavailable);
        Thread.sleep(100);
        assertTrue(service.isRunning());
        service.stop();
        assertFalse(service.isRunning());
        Thread.sleep(200);
        assertTrue(SystemMonitorService.noSamplerThreadsAlive(), "no sampler thread may remain after stop");
    }

    @Test
    void restartAfterStopWorks() throws Exception {
        service.start(SystemStats::sampleUnavailable);
        service.stop();
        service.start(SystemStats::sampleUnavailable);
        assertTrue(service.isRunning());
    }
}
