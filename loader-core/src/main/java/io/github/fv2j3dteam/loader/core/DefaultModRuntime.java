package io.github.fv2j3dteam.loader.core;

import io.github.fv2j3dteam.api.Mod;
import io.github.fv2j3dteam.api.ModContainer;
import io.github.fv2j3dteam.api.ModContainerState;
import io.github.fv2j3dteam.api.ModInstance;
import io.github.fv2j3dteam.api.ModLifecycleAdapter;
import io.github.fv2j3dteam.api.ModRuntime;
import io.github.fv2j3dteam.api.ModRuntimeState;
import io.github.fv2j3dteam.api.RuntimeTransition;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DefaultModRuntime implements ModRuntime {
    private final ModContainer container;
    private final ModInstance instance;
    private final ClassLoader classLoader;
    private final DefaultModLifecycleAdapter lifecycleAdapter;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private ModRuntimeState state = ModRuntimeState.READY;

    private DefaultModRuntime(ModContainer container, ModInstance instance, ClassLoader classLoader) {
        this.container = Objects.requireNonNull(container, "container");
        this.instance = Objects.requireNonNull(instance, "instance");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.lifecycleAdapter = new DefaultModLifecycleAdapter(container, this);
    }

    public static DefaultModRuntime create(ModContainer container, ClassLoader parentLoader, List<ClassLoader> dependencyLoaders) {
        if (!(container instanceof DefaultModContainer defaultContainer)) {
            throw new IllegalArgumentException("Unsupported mod container implementation: " + container.getClass().getName());
        }
        Path sourcePath = defaultContainer.sourcePath();
        if (sourcePath == null || sourcePath.toString().isBlank()) {
            throw new IllegalArgumentException("Mod source path is required to create runtime for '" + container.id() + "'.");
        }

        ClassLoader effectiveParent = parentLoader == null ? ClassLoader.getSystemClassLoader() : parentLoader;
        ModClassLoader modClassLoader = new ModClassLoader(sourcePath, effectiveParent, dependencyLoaders);
        defaultContainer.setClassLoader(modClassLoader);

        Mod mod = instantiateMod(defaultContainer, modClassLoader);
        defaultContainer.setMod(mod);
        DefaultModRuntime runtime = new DefaultModRuntime(defaultContainer, new DefaultModInstance(defaultContainer, mod), modClassLoader);
        defaultContainer.attachRuntime(runtime);
        return runtime;
    }

    @Override
    public ModContainer container() {
        return container;
    }

    @Override
    public ModInstance instance() {
        return instance;
    }

    @Override
    public ModRuntimeState state() {
        return state;
    }

    @Override
    public ClassLoader classLoader() {
        return classLoader;
    }

    @Override
    public ModLifecycleAdapter lifecycleAdapter() {
        return lifecycleAdapter;
    }

    @Override
    public void transitionTo(ModRuntimeState next) {
        RuntimeTransition transition = transition(next);
        if (!transition.allowed()) {
            throw new IllegalStateException(transition.reason());
        }
        this.state = next;
        applyContainerState(next);
    }

    public void load() {
        transitionTo(ModRuntimeState.LOADING);
        lifecycleAdapter.beforeLoad();
        lifecycleAdapter.onLoad();
        transitionTo(ModRuntimeState.LOADED);
    }

    public void initialize() {
        transitionTo(ModRuntimeState.INITIALIZING);
        lifecycleAdapter.beforeInitialize();
        lifecycleAdapter.onInitialize();
        transitionTo(ModRuntimeState.INITIALIZED);
    }

    public void start() {
        transitionTo(ModRuntimeState.STARTING);
        lifecycleAdapter.beforeStart();
        lifecycleAdapter.onStart();
        transitionTo(ModRuntimeState.RUNNING);
    }

    public void stop() {
        if (isTerminal()) {
            return;
        }
        transitionTo(ModRuntimeState.STOPPING);
        lifecycleAdapter.beforeStop();
        lifecycleAdapter.onStop();
        transitionTo(ModRuntimeState.STOPPED);
    }

    public void fail(Throwable cause) {
        if (state != ModRuntimeState.FAILED && state != ModRuntimeState.STOPPED) {
            try {
                transitionTo(ModRuntimeState.FAILED);
            } catch (IllegalStateException ex) {
                this.state = ModRuntimeState.FAILED;
                applyContainerState(this.state);
            }
        }
        close(cause);
    }

    public void close() {
        close(null);
    }

    public void close(Throwable cause) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Throwable primary = cause;
        try {
            if (classLoader instanceof AutoCloseable closeable) {
                closeable.close();
            }
        } catch (Throwable failure) {
            if (primary == null) {
                primary = failure;
            } else {
                primary.addSuppressed(failure);
            }
        }
        if (primary != null && !(primary instanceof RuntimeException) && !(primary instanceof Error)) {
            throw new IllegalStateException("Mod runtime cleanup failed for '" + container.id() + "'.", primary);
        }
        if (primary instanceof RuntimeException re) {
            throw re;
        }
        if (primary instanceof Error error) {
            throw error;
        }
    }

    private static Mod instantiateMod(DefaultModContainer container, ClassLoader classLoader) {
        String entrypoint = container.descriptor().entrypoint();
        try {
            Class<?> type = Class.forName(entrypoint, false, classLoader);
            if (type.isInterface()) {
                throw new IllegalArgumentException("Entrypoint '" + entrypoint + "' for mod '" + container.id() + "' is an interface.");
            }
            if (Modifier.isAbstract(type.getModifiers())) {
                throw new IllegalArgumentException("Entrypoint '" + entrypoint + "' for mod '" + container.id() + "' is abstract.");
            }
            if (!Mod.class.isAssignableFrom(type)) {
                throw new IllegalArgumentException("Entrypoint '" + entrypoint + "' for mod '" + container.id() + "' does not implement io.github.fv2j3dteam.api.Mod.");
            }
            Constructor<?> constructor = type.getDeclaredConstructor();
            if (!Modifier.isPublic(constructor.getModifiers())) {
                throw new IllegalArgumentException("Entrypoint '" + entrypoint + "' for mod '" + container.id() + "' must declare a public no-arg constructor.");
            }
            Object modObject = constructor.newInstance();
            return (Mod) modObject;
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException("Entrypoint class '" + entrypoint + "' for mod '" + container.id() + "' was not found in '" + container.sourcePath() + "'.", ex);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("Failed to instantiate entrypoint '" + entrypoint + "' for mod '" + container.id() + "' from '" + container.sourcePath() + "'.", ex);
        }
    }

    private void applyContainerState(ModRuntimeState next) {
        if (!(container instanceof DefaultModContainer defaultContainer)) {
            return;
        }
        ModContainerState target = switch (next) {
            case DISCOVERED -> ModContainerState.DISCOVERED;
            case VALIDATED -> ModContainerState.VALIDATED;
            case READY -> ModContainerState.READY;
            case LOADING -> ModContainerState.LOADING;
            case LOADED -> ModContainerState.LOADED;
            case INITIALIZING -> ModContainerState.INITIALIZING;
            case INITIALIZED -> ModContainerState.INITIALIZED;
            case STARTING -> ModContainerState.STARTING;
            case RUNNING -> ModContainerState.RUNNING;
            case STOPPING -> ModContainerState.STOPPING;
            case STOPPED -> ModContainerState.STOPPED;
            case FAILED -> ModContainerState.FAILED;
        };
        try {
            defaultContainer.setState(target);
        } catch (IllegalStateException ignored) {
            // container state can be observed but not forced across impossible transitions
        }
    }
}
