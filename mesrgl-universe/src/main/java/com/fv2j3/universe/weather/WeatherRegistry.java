package com.fv2j3.universe.weather;

import com.fv2j3.universe.extension.ExtensionRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Weather registry (§63). */
public final class WeatherRegistry implements ExtensionRegistry<WeatherType> {
    private final Map<String, WeatherType> byId = new LinkedHashMap<>();
    public WeatherRegistry() {
        register(WeatherType.clear());
        register(WeatherType.rain());
        register(WeatherType.snow());
        register(WeatherType.fog());
        register(WeatherType.ash());
    }
    @Override public synchronized void register(WeatherType def) {
        Objects.requireNonNull(def, "def");
        if (byId.containsKey(def.id())) throw new IllegalStateException("dup weather: " + def.id());
        byId.put(def.id(), def);
    }
    @Override public synchronized boolean unregister(String id) { return byId.remove(id) != null; }
    @Override public WeatherType get(String id) { return byId.get(id); }
    @Override public List<WeatherType> all() { return new ArrayList<>(byId.values()); }
}
