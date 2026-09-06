package com.fv2j3.universe.materials;

import com.fv2j3.universe.extension.ExtensionRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Material registry. Built-ins + external registration (§48, §63). */
public final class MaterialRegistry implements ExtensionRegistry<MaterialDefinition> {

    private final Map<String, MaterialDefinition> byId = new LinkedHashMap<>();

    public MaterialRegistry() {
        register(MaterialDefinition.stone());
        register(MaterialDefinition.dirt());
        register(MaterialDefinition.wood());
        register(MaterialDefinition.leaves());
        register(MaterialDefinition.water());
        register(MaterialDefinition.lava());
        register(MaterialDefinition.air());
        register(MaterialDefinition.snow());
        register(MaterialDefinition.ice());
        register(MaterialDefinition.steam());
    }

    @Override
    public synchronized void register(MaterialDefinition def) {
        Objects.requireNonNull(def, "def");
        if (byId.containsKey(def.id())) throw new IllegalStateException("dup material: " + def.id());
        byId.put(def.id(), def);
    }

    @Override
    public synchronized boolean unregister(String id) { return byId.remove(id) != null; }

    @Override
    public MaterialDefinition get(String id) { return byId.get(id); }

    @Override
    public List<MaterialDefinition> all() { return new ArrayList<>(byId.values()); }
}
