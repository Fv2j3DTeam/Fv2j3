package com.fv2j3.universe.life;

import com.fv2j3.universe.extension.ExtensionRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SpeciesRegistry implements ExtensionRegistry<SpeciesDefinition> {
    private final Map<String, SpeciesDefinition> byId = new LinkedHashMap<>();
    @Override public synchronized void register(SpeciesDefinition def) {
        Objects.requireNonNull(def, "def");
        if (byId.containsKey(def.id())) throw new IllegalStateException("dup species: " + def.id());
        byId.put(def.id(), def);
    }
    @Override public synchronized boolean unregister(String id) { return byId.remove(id) != null; }
    @Override public SpeciesDefinition get(String id) { return byId.get(id); }
    @Override public List<SpeciesDefinition> all() { return new ArrayList<>(byId.values()); }
}
