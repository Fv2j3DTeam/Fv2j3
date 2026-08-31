package com.fv2j3.minecraft.compat.monitor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;

/**
 * macOS implementation. CPU/memory come from the platform MXBean (works on
 * both Apple Silicon and Intel). GPU usage is read from the IOAccelerator
 * registry only when exactly one accelerator reports it; temperatures have no
 * dependable unprivileged API, so they degrade to N/A rather than fake values.
 */
public final class MacOSSystemMonitor extends BaseSystemMonitor {

    @Override
    protected OptionalDouble gpuUsage() {
        List<Double> values = queryAcceleratorUtilization();
        if (values.size() == 1) {
            return OptionalDouble.of(values.get(0) / 100.0);
        }
        return OptionalDouble.empty();
    }

    private List<Double> queryAcceleratorUtilization() {
        Process process = null;
        List<Double> values = new ArrayList<>();
        try {
            ProcessBuilder builder = new ProcessBuilder("ioreg", "-r", "-d", "1", "-w", "1", "-c", "IOAccelerator");
            builder.redirectErrorStream(true);
            process = builder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Double value = parseUtilization(line);
                    if (value != null) {
                        values.add(value);
                    }
                }
            }
            process.waitFor(3, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            values.clear();
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
        return values;
    }

    /**
     * Matches the "Device Utilization %"=N entry inside the PerformanceStatistics
     * dictionary reported by IOAccelerator.
     */
    private static Double parseUtilization(String line) {
        int keyIndex = line.indexOf("\"Device Utilization %\"=");
        if (keyIndex < 0) {
            return null;
        }
        try {
            double percent = Double.parseDouble(line.substring(keyIndex + "\"Device Utilization %\"=".length()).trim());
            if (percent < 0.0 || percent > 100.0) {
                return null;
            }
            return percent;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
