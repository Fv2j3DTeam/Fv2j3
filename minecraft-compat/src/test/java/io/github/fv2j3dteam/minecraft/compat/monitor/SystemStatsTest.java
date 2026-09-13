package io.github.fv2j3dteam.minecraft.compat.monitor;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemStatsTest {
    @Test
    void unavailableMetricsRenderAsNa() {
        SystemStats stats = SystemStats.unavailable(1024, 2048, 4096);
        assertEquals("N/A", stats.cpuUsagePercent());
        assertEquals("N/A", stats.gpuUsagePercent());
        assertEquals("N/A", stats.cpuTemperatureCelsius());
        assertEquals("N/A", stats.gpuTemperatureCelsius());
        assertFalse(stats.cpuUsage().isPresent());
    }

    @Test
    void availableMetricsRenderWithValues() {
        SystemStats stats = new SystemStats(5L * 1024 * 1024 * 1024, 6L * 1024 * 1024 * 1024,
                16L * 1024 * 1024 * 1024,
                OptionalDouble.of(0.34), OptionalDouble.of(0.51),
                OptionalDouble.of(62.0), OptionalDouble.of(58.0));
        assertEquals("34%", stats.cpuUsagePercent());
        assertEquals("51%", stats.gpuUsagePercent());
        assertEquals("62\u00B0C", stats.cpuTemperatureCelsius());
        assertEquals("58\u00B0C", stats.gpuTemperatureCelsius());
        assertEquals("5.0 GB / 16.0 GB", stats.memoryGigabytes());
    }

    @Test
    void invalidValuesAreSanitizedToUnavailable() {
        SystemStats stats = new SystemStats(0, 0, 0,
                OptionalDouble.of(-0.2), OptionalDouble.of(Double.NaN),
                OptionalDouble.of(-1.0), OptionalDouble.of(Double.POSITIVE_INFINITY));
        assertEquals("N/A", stats.cpuUsagePercent());
        assertEquals("N/A", stats.gpuUsagePercent());
        assertEquals("N/A", stats.cpuTemperatureCelsius());
        assertEquals("N/A", stats.gpuTemperatureCelsius());
    }

    @Test
    void recordIsImmutable() {
        SystemStats stats = new SystemStats(1, 2, 3,
                OptionalDouble.of(0.5), OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty());
        // Records cannot be mutated; sanity check accessors return stable values.
        assertEquals(0.5, stats.cpuUsage().getAsDouble());
        assertEquals(1, stats.memoryUsed());
        assertEquals(3, stats.memoryMax());
    }

    @Test
    void negativeMemoryIsClampedToZero() {
        SystemStats stats = new SystemStats(-1, -5, -100,
                OptionalDouble.empty(), OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty());
        assertEquals(0, stats.memoryUsed());
        assertEquals(0, stats.memoryMax());
        assertTrue(stats.memoryGigabytes().contains("0.0"));
    }
}
