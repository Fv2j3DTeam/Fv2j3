package io.github.fv2j3dteam.universe.smoke;

import io.github.fv2j3dteam.universe.extension.ExtensionRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Gas registry (§47, §63). */
public final class GasRegistry implements ExtensionRegistry<GasDefinition> {
    private final Map<String, GasDefinition> byId = new LinkedHashMap<>();

    public GasRegistry() {
        register(GasDefinition.air());
        register(GasDefinition.smoke());
        register(GasDefinition.steam());
        register(GasDefinition.volcanicGas());
    }

    @Override
    public synchronized void register(GasDefinition def) {
        Objects.requireNonNull(def, "def");
        if (byId.containsKey(def.id())) throw new IllegalStateException("dup gas: " + def.id());
        byId.put(def.id(), def);
    }

    @Override
    public synchronized boolean unregister(String id) { return byId.remove(id) != null; }

    @Override
    public GasDefinition get(String id) { return byId.get(id); }

    @Override
    public List<GasDefinition> all() { return new ArrayList<>(byId.values()); }
}
