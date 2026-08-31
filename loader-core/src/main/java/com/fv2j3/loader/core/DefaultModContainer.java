package com.fv2j3.loader.core;

import com.fv2j3.api.Mod;
import com.fv2j3.api.ModContainer;
import com.fv2j3.api.ModContainerState;
import com.fv2j3.api.ModContext;
import com.fv2j3.api.ModDescriptor;
import com.fv2j3.api.ModRuntime;
import java.nio.file.Path;
import java.util.Objects;

public final class DefaultModContainer implements ModContainer {
    private final ModDescriptor descriptor;
    private final String sourceName;
    private final String sourceType;
    private final Path sourcePath;
    private ModContainerState state;
    private ModContext context;
    private Mod mod;
    private ClassLoader classLoader;
    private ModRuntime runtime;

    public DefaultModContainer(
            ModDescriptor descriptor,
            String sourceName,
            String sourceType,
            ModContainerState state,
            ModContext context,
            Path sourcePath
    ) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.sourceName = sourceName == null ? "" : sourceName;
        this.sourceType = sourceType == null ? "" : sourceType;
        this.state = state == null ? ModContainerState.DISCOVERED : state;
        this.context = context;
        this.sourcePath = sourcePath;
    }

    public DefaultModContainer(ModDescriptor descriptor, String sourceName, String sourceType, ModContainerState state, ModContext context) {
        this(descriptor, sourceName, sourceType, state, context, null);
    }

    public DefaultModContainer(ModDescriptor descriptor, String sourceName, String sourceType) {
        this(descriptor, sourceName, sourceType, ModContainerState.DISCOVERED, null, null);
    }

    @Override
    public ModDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public String sourceName() {
        return sourceName;
    }

    @Override
    public String sourceType() {
        return sourceType;
    }

    @Override
    public ModContainerState state() {
        return state;
    }

    @Override
    public ModContext context() {
        return context;
    }

    @Override
    public Mod mod() {
        return mod;
    }

    @Override
    public ClassLoader classLoader() {
        return classLoader;
    }

    @Override
    public ModRuntime runtime() {
        return runtime;
    }

    @Override
    public void attachRuntime(ModRuntime runtime) {
        this.runtime = runtime;
    }

    public Path sourcePath() {
        return sourcePath;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public void setState(ModContainerState state) {
        ModContainerState next = state == null ? ModContainerState.DISCOVERED : state;
        if (!this.state.canTransitionTo(next)) {
            throw new IllegalStateException("Illegal mod container state transition from " + this.state + " to " + next);
        }
        this.state = next;
    }

    public void setContext(ModContext context) {
        this.context = context;
    }

    public void setMod(Mod mod) {
        this.mod = mod;
    }
}
