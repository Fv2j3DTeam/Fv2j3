package com.fv2j3.universe.structures;

import com.fv2j3.universe.extension.ExtensionRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Structure registry (§63). */
public final class StructureRegistry implements ExtensionRegistry<StructureDefinition> {
    private final Map<String, StructureDefinition> byId = new LinkedHashMap<>();
    public StructureRegistry() {
        register(new StructureDefinition("station", StructureDefinition.Kind.STATION, 0.05, "", 5000));
        register(new StructureDefinition("ruin", StructureDefinition.Kind.RUIN, 0.3, "desert", 2000));
        register(new StructureDefinition("anomaly", StructureDefinition.Kind.ANOMALY, 0.02, "", 8000));
        register(new StructureDefinition("monolith", StructureDefinition.Kind.MONOLITH, 0.1, "forest", 4000));
    }
    @Override public synchronized void register(StructureDefinition def) {
        Objects.requireNonNull(def, "def");
        if (byId.containsKey(def.id())) throw new IllegalStateException("dup structure: " + def.id());
        byId.put(def.id(), def);
    }
    @Override public synchronized boolean unregister(String id) { return byId.remove(id) != null; }
    @Override public StructureDefinition get(String id) { return byId.get(id); }
    @Override public List<StructureDefinition> all() { return new ArrayList<>(byId.values()); }
}
