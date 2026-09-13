package io.github.fv2j3dteam.loader.core;

import io.github.fv2j3dteam.api.ModConfigProvider;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LoaderContext {
    private final Fv2j3Loader loader;
    private final LoaderEnvironment environment;
    private final List<String> modList;
    private final ClassLoader classLoader;
    private final Object eventBus;
    private final LoaderLogger logger;
    private final Map<String, String> configuration;
    private final ModRegistry modRegistry;
    private final List<ModSource> modSources;
    private volatile ModConfigProvider configProvider;

    public LoaderContext(
            Fv2j3Loader loader,
            LoaderEnvironment environment,
            List<String> modList,
            ClassLoader classLoader,
            Object eventBus,
            LoaderLogger logger,
            Map<String, String> configuration
    ) {
        this(loader, environment, modList, classLoader, eventBus, logger, configuration, List.of());
    }

    public LoaderContext(
            Fv2j3Loader loader,
            LoaderEnvironment environment,
            List<String> modList,
            ClassLoader classLoader,
            Object eventBus,
            LoaderLogger logger,
            Map<String, String> configuration,
            List<ModSource> modSources
    ) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.modList = modList == null ? List.of() : List.copyOf(modList);
        this.classLoader = classLoader == null ? ClassLoader.getSystemClassLoader() : classLoader;
        this.eventBus = eventBus;
        this.logger = logger == null ? new StandardLoaderLogger("Fv2j3") : logger;
        this.configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
        this.modRegistry = new ModRegistry();
        this.modSources = modSources == null ? List.of() : List.copyOf(modSources);
    }

    public Fv2j3Loader loader() {
        return loader;
    }

    public LoaderEnvironment environment() {
        return environment;
    }

    public List<String> modList() {
        return Collections.unmodifiableList(modList);
    }

    public List<ModSource> modSources() {
        return Collections.unmodifiableList(modSources);
    }

    public ClassLoader classLoader() {
        return classLoader;
    }

    public Object eventBus() {
        return eventBus;
    }

    public LoaderLogger logger() {
        return logger;
    }

    public Map<String, String> configuration() {
        return Collections.unmodifiableMap(configuration);
    }

    public ModRegistry modRegistry() {
        return modRegistry;
    }

    /**
     * The per-mod config provider wired by the loader. May be null when
     * no config dir is configured (e.g. minimal tests); mods should
     * handle that gracefully.
     */
    public ModConfigProvider configProvider() {
        return configProvider;
    }

    /** Loader-internal setter: the Fv2j3Loader installs the registry. */
    public void setConfigProvider(ModConfigProvider configProvider) {
        this.configProvider = configProvider;
    }
}
