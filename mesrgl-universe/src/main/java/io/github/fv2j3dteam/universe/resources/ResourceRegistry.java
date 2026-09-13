package io.github.fv2j3dteam.universe.resources;

import io.github.fv2j3dteam.universe.extension.ExtensionRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Resource registry (§13, §63). */
public final class ResourceRegistry implements ExtensionRegistry<ResourceDefinition> {

    private final Map<String, ResourceDefinition> byId = new LinkedHashMap<>();

    public ResourceRegistry() {
        register(ResourceDefinition.iron());
        register(ResourceDefinition.copper());
        register(ResourceDefinition.water());
        register(ResourceDefinition.coal());
    }

    @Override
    public synchronized void register(ResourceDefinition def) {
        Objects.requireNonNull(def, "def");
        if (byId.containsKey(def.id())) throw new IllegalStateException("dup resource: " + def.id());
        byId.put(def.id(), def);
    }

    @Override
    public synchronized boolean unregister(String id) { return byId.remove(id) != null; }

    @Override
    public ResourceDefinition get(String id) { return byId.get(id); }

    @Override
    public List<ResourceDefinition> all() { return new ArrayList<>(byId.values()); }
}
