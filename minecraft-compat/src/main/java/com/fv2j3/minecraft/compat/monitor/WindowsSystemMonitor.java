package com.fv2j3.minecraft.compat.monitor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;

/**
 * Windows implementation. CPU/memory come from the platform MXBean. GPU
 * metrics use nvidia-smi only when an NVIDIA driver is actually installed
 * (queried from the background sampler thread, never the render thread);
 * otherwise the metrics degrade to N/A. CPU temperature has no dependable
 * stock API without heavyweight native dependencies, so it is N/A.
 */
public final class WindowsSystemMonitor extends BaseSystemMonitor {

    @Override
    protected OptionalDouble gpuUsage() {
        NvidiaSample sample = queryNvidia();
        if (sample == null || sample.gpuCount() != 1 || sample.utilization() == null) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(sample.utilization() / 100.0);
    }

    @Override
    protected OptionalDouble gpuTemperature() {
        NvidiaSample sample = queryNvidia();
        if (sample == null || sample.gpuCount() != 1 || sample.temperature() == null) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(sample.temperature());
    }

    private NvidiaSample queryNvidia() {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    "nvidia-smi",
                    "--query-gpu=utilization.gpu,temperature.gpu",
                    "--format=csv=noheader,nounits");
            builder.redirectErrorStream(true);
            process = builder.start();
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        lines.add(line);
                    }
                }
            }
            process.waitFor(3, TimeUnit.SECONDS);
            if (lines.size() != 1) {
                // Zero adapters or multiple GPUs: cannot identify the one
                // Minecraft uses, so report N/A instead of guessing.
                return new NvidiaSample(lines.size(), null, null);
            }
            String[] parts = lines.get(0).split(",");
            Double utilization = parsePercent(parts.length > 0 ? parts[0] : "");
            Double temperature = parseTemperature(parts.length > 1 ? parts[1] : "");
            return new NvidiaSample(1, utilization, temperature);
        } catch (Exception ignored) {
            return new NvidiaSample(0, null, null);
        } finally {
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    private static Double parsePercent(String value) {
        try {
            double percent = Double.parseDouble(value.trim());
            if (percent < 0.0 || percent > 100.0) {
                return null;
            }
            return percent;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Double parseTemperature(String value) {
        try {
            double celsius = Double.parseDouble(value.trim());
            if (celsius < -20.0 || celsius > 150.0) {
                return null;
            }
            return celsius;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record NvidiaSample(int gpuCount, Double utilization, Double temperature) {
    }
}
