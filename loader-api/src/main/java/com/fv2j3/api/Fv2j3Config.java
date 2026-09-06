package com.fv2j3.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Per-mod configuration store (Phase 47.1 - mod config API).
 *
 * Each mod gets its own {@code Fv2j3Config} instance bound to a config
 * file under {@code <runtime>/config/<modId>.properties} (the path is
 * injected by the loader, so two mods never share a file). The store
 * supports the full primitive type set the spec requires:
 *
 *   - {@code String}, {@code boolean}, {@code int}, {@code long},
 *     {@code float}, {@code double}, {@code enum}, {@code List<String>}
 *
 * Values are persisted to disk on {@link #save()}; the loader calls
 * {@link #save()} after every successful pre-init/init lifecycle step.
 * Unknown keys in the file are preserved on round-trip so a config can
 * be edited by hand without losing entries.
 */
public final class Fv2j3Config {
    private final String modId;
    private final Path configFile;
    private final Map<String, String> values = new LinkedHashMap<>();
    private boolean dirty;

    public Fv2j3Config(String modId, Path configFile) {
        this.modId = Objects.requireNonNull(modId, "modId");
        this.configFile = Objects.requireNonNull(configFile, "configFile");
        load();
    }

    public String modId() {
        return modId;
    }

    public Path configFile() {
        return configFile;
    }

    // ---- raw access ----

    public String get(String key, String defaultValue) {
        String v = values.get(key);
        return v != null ? v : defaultValue;
    }

    public void set(String key, String value) {
        values.put(key, value == null ? "" : value);
        dirty = true;
    }

    public Map<String, String> values() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public boolean isDirty() {
        return dirty;
    }

    // ---- typed accessors ----

    public int getInt(String key, int defaultValue) {
        String v = values.get(key);
        if (v == null) return defaultValue;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public void setInt(String key, int value) {
        values.put(key, Integer.toString(value));
        dirty = true;
    }

    public long getLong(String key, long defaultValue) {
        String v = values.get(key);
        if (v == null) return defaultValue;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public void setLong(String key, long value) {
        values.put(key, Long.toString(value));
        dirty = true;
    }

    public float getFloat(String key, float defaultValue) {
        String v = values.get(key);
        if (v == null) return defaultValue;
        try {
            return Float.parseFloat(v.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public void setFloat(String key, float value) {
        values.put(key, Float.toString(value));
        dirty = true;
    }

    public double getDouble(String key, double defaultValue) {
        String v = values.get(key);
        if (v == null) return defaultValue;
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public void setDouble(String key, double value) {
        values.put(key, Double.toString(value));
        dirty = true;
    }

    public boolean getBool(String key, boolean defaultValue) {
        String v = values.get(key);
        if (v == null) return defaultValue;
        return Boolean.parseBoolean(v.trim());
    }

    public void setBool(String key, boolean value) {
        values.put(key, Boolean.toString(value));
        dirty = true;
    }

    /**
     * Reads an enum value by class; returns {@code defaultValue} if the
     * stored value is missing, malformed, or not a valid constant of the
     * enum class. The matching is case-insensitive on the constant name.
     */
    public <E extends Enum<E>> E getEnum(String key, Class<E> type, E defaultValue) {
        Objects.requireNonNull(type, "type");
        String v = values.get(key);
        if (v == null) return defaultValue;
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(v.trim())) {
                return constant;
            }
        }
        return defaultValue;
    }

    public <E extends Enum<E>> void setEnum(String key, E value) {
        Objects.requireNonNull(value, "value");
        values.put(key, value.name());
        dirty = true;
    }

    /**
     * Reads a comma-separated list. Whitespace around entries is trimmed;
     * empty entries are skipped.
     */
    public List<String> getList(String key, List<String> defaultValue) {
        String v = values.get(key);
        if (v == null) return defaultValue == null ? List.of() : defaultValue;
        if (v.isEmpty()) return List.of();
        String[] parts = v.split(",");
        List<String> out = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public void setList(String key, List<String> value) {
        Objects.requireNonNull(value, "value");
        values.put(key, String.join(",", value));
        dirty = true;
    }

    // ---- persistence ----

    public void save() {
        try {
            if (configFile.getParent() != null) {
                Files.createDirectories(configFile.getParent());
            }
            StringBuilder sb = new StringBuilder();
            sb.append("# Fv2j3 config for mod ").append(modId).append('\n');
            for (Map.Entry<String, String> entry : values.entrySet()) {
                sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
            }
            Files.writeString(configFile, sb.toString());
            dirty = false;
        } catch (IOException ex) {
            throw new RuntimeException("Failed to save config for " + modId, ex);
        }
    }

    private void load() {
        if (!Files.exists(configFile)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(configFile)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq > 0) {
                    values.put(trimmed.substring(0, eq), trimmed.substring(eq + 1));
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("Failed to load config for " + modId, ex);
        }
    }

    @Override
    public String toString() {
        return "Fv2j3Config{" + modId + " -> " + configFile + " (" + values.size() + " entries)}";
    }
}
