package io.github.fv2j3dteam.minecraft.compat.monitor;

import java.lang.management.ManagementFactory;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * Shared cross-platform behavior: heap memory from the JVM runtime and real
 * system CPU load from the HotSpot platform MXBean. Platform subclasses only
 * supply GPU/temperature metrics where the OS actually exposes them.
 */
abstract class BaseSystemMonitor implements SystemMonitor {
    private final com.sun.management.OperatingSystemMXBean osBean;

    protected BaseSystemMonitor() {
        com.sun.management.OperatingSystemMXBean bean = null;
        try {
            var platform = ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
            bean = platform;
        } catch (RuntimeException | LinkageError ignored) {
            bean = null;
        }
        this.osBean = bean;
    }

    @Override
    public final SystemStats snapshot() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        return new SystemStats(
                used,
                runtime.totalMemory(),
                runtime.maxMemory(),
                safeDouble(this::cpuLoad),
                safeDouble(this::gpuUsage),
                safeDouble(this::cpuTemperature),
                safeDouble(this::gpuTemperature)
        );
    }

    /** Real system-wide CPU load (0..1) from the platform MXBean; not core counts. */
    protected OptionalDouble cpuLoad() {
        if (osBean == null) {
            return OptionalDouble.empty();
        }
        double load = osBean.getCpuLoad();
        if (!Double.isFinite(load) || load < 0.0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(Math.min(load, 1.0));
    }

    protected OptionalDouble gpuUsage() {
        return OptionalDouble.empty();
    }

    protected OptionalDouble cpuTemperature() {
        return OptionalDouble.empty();
    }

    protected OptionalDouble gpuTemperature() {
        return OptionalDouble.empty();
    }

    protected static OptionalDouble safeDouble(Supplier<OptionalDouble> supplier) {
        try {
            return supplier.get();
        } catch (Throwable ignored) {
            return OptionalDouble.empty();
        }
    }
}
