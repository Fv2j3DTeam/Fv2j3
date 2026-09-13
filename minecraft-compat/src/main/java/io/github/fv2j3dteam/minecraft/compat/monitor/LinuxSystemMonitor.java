package io.github.fv2j3dteam.minecraft.compat.monitor;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Linux implementation backed by standard kernel interfaces: DRM sysfs for
 * GPU usage/temperature and hwmon/thermal_zone for CPU temperature. Requires
 * no external process; permission problems degrade to empty (N/A) values.
 */
public final class LinuxSystemMonitor extends BaseSystemMonitor {

    @Override
    protected OptionalDouble gpuUsage() {
        // gpu_busy_percent is exposed by amdgpu and i915; NVIDIA proprietary
        // does not export it, which degrades to N/A rather than a fake value.
        List<Double> values = readMatchingCardValues("gpu_busy_percent");
        if (values.size() == 1) {
            return OptionalDouble.of(values.get(0) / 100.0);
        }
        // Zero or multiple reporting cards: the GPU Minecraft actually uses
        // cannot be reliably identified, so report unavailable instead of guessing.
        return OptionalDouble.empty();
    }

    @Override
    protected OptionalDouble gpuTemperature() {
        List<Double> values = readMatchingCardHwmonTemperatures();
        if (values.size() == 1) {
            return OptionalDouble.of(values.get(0));
        }
        return OptionalDouble.empty();
    }

    @Override
    protected OptionalDouble cpuTemperature() {
        OptionalDouble fromHwmon = readCpuHwmonTemperature();
        if (fromHwmon.isPresent()) {
            return fromHwmon;
        }
        return readCpuThermalZoneTemperature();
    }

    private List<Double> readMatchingCardValues(String fileName) {
        List<Double> values = new ArrayList<>();
        try (DirectoryStream<Path> cards = Files.newDirectoryStream(Path.of("/sys/class/drm"), "card*")) {
            for (Path card : cards) {
                Path file = card.resolve("device").resolve(fileName);
                String content = readSmallFile(file);
                if (content == null) {
                    continue;
                }
                try {
                    values.add(Double.parseDouble(content.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException | SecurityException ignored) {
        }
        return values;
    }

    private List<Double> readMatchingCardHwmonTemperatures() {
        List<Double> values = new ArrayList<>();
        try (DirectoryStream<Path> cards = Files.newDirectoryStream(Path.of("/sys/class/drm"), "card*")) {
            for (Path card : cards) {
                Double temperature = readFirstHwmonTemperature(card.resolve("device").resolve("hwmon"));
                if (temperature != null) {
                    values.add(temperature);
                }
            }
        } catch (IOException | SecurityException ignored) {
        }
        return values;
    }

    private OptionalDouble readCpuHwmonTemperature() {
        try (DirectoryStream<Path> hwmons = Files.newDirectoryStream(Path.of("/sys/class/hwmon"), "hwmon*")) {
            for (Path hwmon : sorted(hwmons)) {
                try (DirectoryStream<Path> inputs = Files.newDirectoryStream(hwmon, "temp*_input")) {
                    for (Path input : sorted(inputs)) {
                        if (!isCpuLabel(hwmon, input)) {
                            continue;
                        }
                        Double temperature = readTemperatureMillis(input);
                        if (temperature != null) {
                            return OptionalDouble.of(temperature);
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        } catch (IOException | SecurityException ignored) {
        }
        return OptionalDouble.empty();
    }

    private boolean isCpuLabel(Path hwmon, Path input) {
        Path label = input.resolveSibling(input.getFileName().toString().replace("_input", "_label"));
        String text = readSmallFile(label);
        if (text == null) {
            // coretemp exposes no labels on some kernels; accept the chip name instead.
            String name = readSmallFile(hwmon.resolve("name"));
            return name != null && name.toLowerCase(Locale.ROOT).contains("coretemp");
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("cpu") || normalized.contains("package")
                || normalized.contains("tdie") || normalized.contains("tctl")
                || normalized.contains("tctl") || normalized.contains("soc");
    }

    private OptionalDouble readCpuThermalZoneTemperature() {
        try (DirectoryStream<Path> zones = Files.newDirectoryStream(Path.of("/sys/class/thermal"), "thermal_zone*")) {
            for (Path zone : sorted(zones)) {
                String type = readSmallFile(zone.resolve("type"));
                if (type == null) {
                    continue;
                }
                String normalized = type.toLowerCase(Locale.ROOT);
                if (!normalized.contains("cpu") && !normalized.contains("pkg")
                        && !normalized.contains("soc") && !normalized.contains("x86_pkg_temp")) {
                    continue;
                }
                Double temperature = readTemperatureMillis(zone.resolve("temp"));
                if (temperature != null) {
                    return OptionalDouble.of(temperature);
                }
            }
        } catch (IOException | SecurityException ignored) {
        }
        return OptionalDouble.empty();
    }

    /** Returns the first temperature (°C) under the given hwmon directory, or null. */
    private Double readFirstHwmonTemperature(Path hwmonDir) {
        try (DirectoryStream<Path> inputs = Files.newDirectoryStream(hwmonDir, "temp*_input")) {
            for (Path input : sorted(inputs)) {
                Double temperature = readTemperatureMillis(input);
                if (temperature != null) {
                    return temperature;
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private Double readTemperatureMillis(Path file) {
        String content = readSmallFile(file);
        if (content == null) {
            return null;
        }
        try {
            double millis = Double.parseDouble(content.trim());
            double celsius = millis / 1000.0;
            // Sanity check: anything outside physical range is treated as unavailable.
            if (celsius < -20.0 || celsius > 150.0) {
                return null;
            }
            return celsius;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String readSmallFile(Path file) {
        try {
            if (!Files.isReadable(file)) {
                return null;
            }
            return Files.readString(file).trim();
        } catch (IOException | SecurityException ignored) {
            return null;
        }
    }

    private static List<Path> sorted(DirectoryStream<Path> stream) {
        List<Path> paths = new ArrayList<>();
        for (Path path : stream) {
            paths.add(path);
        }
        paths.sort(Path::compareTo);
        return paths;
    }
}
