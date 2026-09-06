package com.fv2j3.universe.fluid;

import com.fv2j3.universe.extension.ExtensionRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Fluid registry (§63). */
public final class FluidRegistry implements ExtensionRegistry<FluidDefinition> {
    private final Map<String, FluidDefinition> byId = new LinkedHashMap<>();

    public FluidRegistry() {
        register(FluidDefinition.water());
        register(FluidDefinition.lava());
        register(FluidDefinition.oil());
        register(FluidDefinition.air());
        register(FluidDefinition.steam());
    }

    @Override
    public synchronized void register(FluidDefinition def) {
        Objects.requireNonNull(def, "def");
        if (byId.containsKey(def.id())) throw new IllegalStateException("dup fluid: " + def.id());
        byId.put(def.id(), def);
    }

    @Override
    public synchronized boolean unregister(String id) { return byId.remove(id) != null; }

    @Override
    public FluidDefinition get(String id) { return byId.get(id); }

    @Override
    public List<FluidDefinition> all() { return new ArrayList<>(byId.values()); }
}
