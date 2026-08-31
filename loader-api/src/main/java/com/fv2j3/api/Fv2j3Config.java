package com.fv2j3.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class Fv2j3Config {
    private final String modId;
    private final Path configFile;
    private final Map<String, String> values = new HashMap<>();

    public Fv2j3Config(String modId, Path configFile) {
        this.modId = modId;
        this.configFile = configFile;
        load();
    }

    public String get(String key, String defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String v = values.get(key);
        return v != null ? Integer.parseInt(v) : defaultValue;
    }

    public boolean getBool(String key, boolean defaultValue) {
        String v = values.get(key);
        return v != null ? Boolean.parseBoolean(v) : defaultValue;
    }

    public void set(String key, String value) {
        values.put(key, value);
    }

    public void setInt(String key, int value) {
        values.put(key, String.valueOf(value));
    }

    public void setBool(String key, boolean value) {
        values.put(key, String.valueOf(value));
    }

    public void save() {
        try {
            Files.createDirectories(configFile.getParent());
            StringBuilder sb = new StringBuilder();
            values.forEach((k, v) -> sb.append(k).append("=").append(v).append("\n"));
            Files.writeString(configFile, sb.toString());
        } catch (IOException ex) {
            throw new RuntimeException("Failed to save config for " + modId, ex);
        }
    }

    private void load() {
        if (Files.exists(configFile)) {
            try {
                Files.readAllLines(configFile).forEach(line -> {
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        values.put(line.substring(0, eq), line.substring(eq + 1));
                    }
                });
            } catch (IOException ex) {
                throw new RuntimeException("Failed to load config for " + modId, ex);
            }
        }
    }

    public String modId() { return modId; }
    public Map<String, String> values() { return Map.copyOf(values); }
}
