package com.fv2j3.universe.biome;

import com.fv2j3.universe.extension.ExtensionRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Biome registry (§12, §63). Holds built-in and externally-registered biome
 * definitions. Selection picks the matching biome; ties broken by deterministic
 * hash of (planet, coord).
 */
public final class BiomeRegistry implements ExtensionRegistry<BiomeDefinition> {

    private final Map<String, BiomeDefinition> byId = new LinkedHashMap<>();

    public BiomeRegistry() {
        register(BiomeDefinition.ocean());
        register(BiomeDefinition.desert());
        register(BiomeDefinition.tundra());
        register(BiomeDefinition.forest());
        register(BiomeDefinition.grassland());
        register(BiomeDefinition.mountain());
        register(BiomeDefinition.volcanic());
        register(BiomeDefinition.swamp());
        register(BiomeDefinition.taiga());
        register(BiomeDefinition.ice());
    }

    @Override
    public synchronized void register(BiomeDefinition def) {
        Objects.requireNonNull(def, "def");
        if (byId.containsKey(def.id())) {
            throw new IllegalStateException("biome id already registered: " + def.id());
        }
        byId.put(def.id(), def);
    }

    @Override
    public synchronized boolean unregister(String id) {
        return byId.remove(id) != null;
    }

    @Override
    public BiomeDefinition get(String id) {
        return byId.get(id);
    }

    @Override
    public List<BiomeDefinition> all() {
        return new ArrayList<>(byId.values());
    }

    public BiomeDefinition select(double tempK, double humidity, double altitude, long deterministicTieBreaker) {
        List<BiomeDefinition> matches = new ArrayList<>();
        for (BiomeDefinition d : byId.values()) {
            if (d.matches(tempK, humidity, altitude)) matches.add(d);
        }
        if (matches.isEmpty()) {
            // fallback: pick the closest by temperature
            return byId.values().stream()
                    .min(Comparator.comparingDouble(d -> Math.abs((d.minTempK() + d.maxTempK()) * 0.5 - tempK)))
                    .orElseThrow();
        }
        // deterministic tie-break
        int idx = Math.floorMod(deterministicTieBreaker, matches.size());
        return matches.get(idx);
    }
}
