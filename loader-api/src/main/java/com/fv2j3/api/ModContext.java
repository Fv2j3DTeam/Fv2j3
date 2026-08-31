package com.fv2j3.api;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public final class ModContext {
    private final ModDescriptor descriptor;
    private final Object loaderContext;
    private final ModLogger logger;
    private final ModRegistryView registry;
    private final Map<String, Object> attributes;

    public ModContext(ModDescriptor descriptor, Object loaderContext, Object logger, Map<String, Object> attributes) {
        this(descriptor, loaderContext, logger, null, attributes);
    }

    public ModContext(
            ModDescriptor descriptor,
            Object loaderContext,
            Object logger,
            ModRegistryView registry,
            Map<String, Object> attributes
    ) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.loaderContext = loaderContext;
        this.logger = logger instanceof ModLogger modLogger ? modLogger : null;
        this.registry = registry;
        this.attributes = attributes == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(attributes));
    }

    public String modId() {
        return descriptor.id();
    }

    public String modName() {
        return descriptor.name();
    }

    public ModDescriptor descriptor() {
        return descriptor;
    }

    public Object loaderContext() {
        return loaderContext;
    }

    public <T> T loaderContext(Class<T> expectedType) {
        if (loaderContext == null) {
            return null;
        }
        return expectedType.cast(loaderContext);
    }

    public ModLogger logger() {
        return logger;
    }

    public <T> T logger(Class<T> expectedType) {
        if (logger == null) {
            return null;
        }
        return expectedType.cast(logger);
    }

    public ModRegistryView registry() {
        return registry;
    }

    public ModRuntimeState runtimeState() {
        ModRuntimeState state = attribute("runtimeState", ModRuntimeState.class);
        return state == null ? ModRuntimeState.READY : state;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public <T> T attribute(String key, Class<T> expectedType) {
        Objects.requireNonNull(key, "attribute key");
        Object value = attributes.get(key);
        if (value == null) {
            return null;
        }
        return expectedType.cast(value);
    }
}
