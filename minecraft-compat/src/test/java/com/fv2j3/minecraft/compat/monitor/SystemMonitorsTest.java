package com.fv2j3.minecraft.compat.monitor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemMonitorsTest {
    @Test
    void platformMonitorIsAlwaysAvailable() {
        assertNotNull(SystemMonitors.create());
    }

    @Test
    void snapshotNeverThrowsAndMemoryIsReal() {
        SystemMonitor monitor = SystemMonitors.create();
        SystemStats stats = monitor.snapshot();
        assertNotNull(stats);
        assertTrue(stats.memoryUsed() >= 0);
        assertTrue(stats.memoryAllocated() >= stats.memoryUsed());
        // JVM heap memory must be a real value, never zero.
        assertTrue(stats.memoryMax() > 0);
        // Unavailable platform metrics must be absent, never fake numbers.
        stats.gpuUsage().ifPresent(value -> assertTrue(value >= 0.0 && value <= 1.0));
        stats.cpuUsage().ifPresent(value -> assertTrue(value >= 0.0 && value <= 1.0));
        stats.cpuTemperature().ifPresent(value -> assertTrue(value > -20.0 && value < 150.0));
        stats.gpuTemperature().ifPresent(value -> assertTrue(value > -20.0 && value < 150.0));
    }

    @Test
    void repeatedSnapshotsAreSafe() {
        SystemMonitor monitor = SystemMonitors.create();
        for (int i = 0; i < 3; i++) {
            assertNotNull(monitor.snapshot());
        }
    }
}
