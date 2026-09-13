package io.github.fv2j3dteam.minecraft.compat.monitor;

import java.util.OptionalDouble;

/**
 * Immutable, thread-safe snapshot of system resource stats. Any metric the
 * platform cannot provide is an empty OptionalDouble; consumers must render
 * that as "N/A", never as 0.
 */
public record SystemStats(
        long memoryUsed,
        long memoryAllocated,
        long memoryMax,
        OptionalDouble cpuUsage,
        OptionalDouble gpuUsage,
        OptionalDouble cpuTemperature,
        OptionalDouble gpuTemperature) {

    public SystemStats {
        cpuUsage = sanitized(cpuUsage);
        gpuUsage = sanitized(gpuUsage);
        cpuTemperature = sanitized(cpuTemperature);
        gpuTemperature = sanitized(gpuTemperature);
        if (memoryUsed < 0) memoryUsed = 0;
        if (memoryAllocated < 0) memoryAllocated = 0;
        if (memoryMax < 0) memoryMax = 0;
    }

    private static OptionalDouble sanitized(OptionalDouble value) {
        if (value == null || value.isEmpty()) {
            return OptionalDouble.empty();
        }
        double v = value.getAsDouble();
        if (!Double.isFinite(v) || v < 0) {
            return OptionalDouble.empty();
        }
        return value;
    }

    public static SystemStats unavailable(long memoryUsed, long memoryAllocated, long memoryMax) {
        return new SystemStats(memoryUsed, memoryAllocated, memoryMax,
                OptionalDouble.empty(), OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty());
    }

    /** Memory-only snapshot with all platform metrics unavailable. */
    public static SystemStats sampleUnavailable() {
        Runtime runtime = Runtime.getRuntime();
        return unavailable(runtime.totalMemory() - runtime.freeMemory(),
                runtime.totalMemory(), runtime.maxMemory());
    }

    public String cpuUsagePercent() {
        return usagePercent(cpuUsage);
    }

    public String gpuUsagePercent() {
        return usagePercent(gpuUsage);
    }

    public String cpuTemperatureCelsius() {
        return temperature(cpuTemperature);
    }

    public String gpuTemperatureCelsius() {
        return temperature(gpuTemperature);
    }

    public String memoryGigabytes() {
        return gibibytes(memoryUsed) + " GB / " + gibibytes(memoryMax) + " GB";
    }

    private static String usagePercent(OptionalDouble value) {
        return value.isEmpty() ? "N/A" : Math.round(value.getAsDouble() * 100.0) + "%";
    }

    private static String temperature(OptionalDouble value) {
        return value.isEmpty() ? "N/A" : Math.round(value.getAsDouble()) + "\u00B0C";
    }

    private static String gibibytes(long bytes) {
        return String.format(java.util.Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
