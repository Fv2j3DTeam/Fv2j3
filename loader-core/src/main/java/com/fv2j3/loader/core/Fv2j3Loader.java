package com.fv2j3.loader.core;

import com.fv2j3.api.ModLoadingProgress;
import com.fv2j3.api.ModRuntimeState;
import com.fv2j3.api.ModSide;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class Fv2j3Loader {
    private final LoaderEnvironment environment;
    private final LoaderLogger logger;
    private LoaderContext context;
    private LoaderState state;
    private Path configDirectory;
    private Consumer<ModLoadingProgress> progressListener = ignored -> { };

    public Fv2j3Loader(LoaderEnvironment environment) {
        this(environment, new StandardLoaderLogger("Fv2j3"));
    }

    public Fv2j3Loader(LoaderEnvironment environment, LoaderLogger logger) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.logger = logger == null ? new StandardLoaderLogger("Fv2j3") : logger;
        this.state = LoaderState.CREATED;
        this.context = new LoaderContext(this, this.environment, null, null, null, this.logger, Map.of(), List.of());
    }

    public LoaderState state() {
        return state;
    }

    public LoaderEnvironment environment() {
        return environment;
    }

    public LoaderContext context() {
        return context;
    }

    public void setProgressListener(Consumer<ModLoadingProgress> progressListener) {
        this.progressListener = progressListener == null ? ignored -> { } : progressListener;
    }

    /**
     * Sets the directory where per-mod {@code Fv2j3Config} files live.
     * Defaults to {@code <modDirectory>/../config} when unset.
     */
    public void setConfigDirectory(Path configDirectory) {
        this.configDirectory = configDirectory;
    }

    public Path configDirectory() {
        return configDirectory;
    }

    public void initialize() throws LoaderInitializationException {
        initialize(defaultModDirectory());
    }

    public void initialize(Path modDirectory) throws LoaderInitializationException {
        if (state == LoaderState.CREATED) {
            transitionTo(LoaderState.INITIALIZING);
        } else {
            throw new IllegalStateException("Loader must be in CREATED state to initialize; current state is " + state);
        }

        try {
            environment.validateJava26();
            logger.info("Initializing Fv2j3 loader for Minecraft " + environment.minecraftVersion());
            progress("discovering", null, 0, 1);

            List<ModSource> sources = resolveModSources(modDirectory);
            this.context = new LoaderContext(
                    this,
                    environment,
                    List.of(),
                    ClassLoader.getSystemClassLoader(),
                    null,
                    logger,
                    Map.of("mods.directory", modDirectory == null ? "" : modDirectory.toString()),
                    sources
            );

            // Per-mod config dir. Default: <modDirectory>/../config.
            if (this.configDirectory == null && modDirectory != null) {
                Path parent = modDirectory.toAbsolutePath().getParent();
                if (parent != null) {
                    this.configDirectory = parent.resolve("config");
                }
            }
            if (this.configDirectory != null) {
                this.context.setConfigProvider(new ModConfigRegistry(this.configDirectory));
                logger.info("Mod config directory: " + this.configDirectory.toAbsolutePath());
            }

            discoverAndRegisterSources(sources);
            progress("validating", null, 1, 1);
            logger.info("Fv2j3 initialized with " + context.modRegistry().all().size() + " discovered mod container(s)");
            transitionTo(LoaderState.INITIALIZED);
        } catch (RuntimeException ex) {
            fail(ex);
            throw new LoaderInitializationException("Fv2j3 loader initialization failed: " + ex.getMessage(), ex);
        }
    }

    public void start() throws LoaderInitializationException {
        if (state == LoaderState.INITIALIZED) {
            transitionTo(LoaderState.STARTING);
        } else {
            throw new IllegalStateException("Loader must be in INITIALIZED state to start; current state is " + state);
        }

        try {
            logger.info("Starting Fv2j3 loader");
            int total = context.modRegistry().all().size();
            progress("resolving dependencies", null, 0, total);
            context.modRegistry().validateDependencies(context);
            List<String> readyOrder = context.modRegistry().orderedIds();
            List<DefaultModRuntime> runtimes = new ArrayList<>();
            List<com.fv2j3.api.ModRuntime> created = context.modRegistry().createRuntimes(context.classLoader());
            for (var runtime : created) {
                runtimes.add((DefaultModRuntime) runtime);
            }
            List<DefaultModRuntime> started = new ArrayList<>();
            try {
                for (int index = 0; index < runtimes.size(); index++) {
                    DefaultModRuntime runtime = runtimes.get(index);
                    started.add(runtime);
                    progress("loading", runtime.container().id(), index, runtimes.size());
                    runtime.load();
                    progress("initializing", runtime.container().id(), index, runtimes.size());
                    runtime.initialize();
                    progress("starting", runtime.container().id(), index, runtimes.size());
                    runtime.start();
                    progress("running", runtime.container().id(), index + 1, runtimes.size());
                }
            } catch (RuntimeException ex) {
                for (int i = started.size() - 1; i >= 0; i--) {
                    DefaultModRuntime runtime = started.get(i);
                    try {
                        if (runtime.state() == ModRuntimeState.RUNNING) {
                            runtime.stop();
                        } else if (runtime.state() == ModRuntimeState.STARTING
                                || runtime.state() == ModRuntimeState.INITIALIZING
                                || runtime.state() == ModRuntimeState.LOADING
                                || runtime.state() == ModRuntimeState.READY) {
                            if (runtime.state() != ModRuntimeState.FAILED && runtime.state() != ModRuntimeState.STOPPED) {
                                runtime.transitionTo(ModRuntimeState.FAILED);
                            }
                        }
                    } catch (RuntimeException cleanupFailure) {
                        if (cleanupFailure != ex) {
                            ex.addSuppressed(cleanupFailure);
                        }
                    }
                    try {
                        runtime.close();
                    } catch (RuntimeException closeFailure) {
                        if (closeFailure != ex) {
                            ex.addSuppressed(closeFailure);
                        }
                    }
                }
                throw ex;
            }
            progress("running", null, runtimes.size(), runtimes.size());
            logger.info("Ready mod ordering: " + readyOrder);
            // Persist any per-mod configs that mods touched during init.
            saveAllConfigs();
            transitionTo(LoaderState.RUNNING);
            // The only path to 100%: the loader truly finished loading all mods.
            progress("complete", null, runtimes.size(), runtimes.size());
        } catch (RuntimeException ex) {
            fail(ex);
            throw new LoaderInitializationException("Fv2j3 loader startup failed: " + ex.getMessage(), ex);
        }

    }

    private void saveAllConfigs() {
        if (context != null && context.configProvider() instanceof ModConfigRegistry registry) {
            try {
                registry.saveAll();
            } catch (RuntimeException ex) {
                logger.warn("Failed to persist mod configs: " + ex.getMessage());
            }
        }
    }

    private void progress(String phase, String modId, int completed, int total) {
        progressListener.accept(new ModLoadingProgress(phase, modId, completed, total));
        String delay = System.getProperty("fv2j3.test.loadingDelayMs");
        if (delay == null || delay.isBlank()) {
            return;
        }
        try {
            long delayMillis = Long.parseLong(delay);
            if (delayMillis < 0) {
                throw new IllegalArgumentException("fv2j3.test.loadingDelayMs must not be negative.");
            }
            Thread.sleep(delayMillis);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("fv2j3.test.loadingDelayMs must be a whole number.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during the configured loading verification delay.", ex);
        }
    }

    public void stop() throws LoaderInitializationException {
        if (state != LoaderState.RUNNING && state != LoaderState.STARTING) {
            throw new IllegalStateException("Loader cannot stop from state " + state);
        }

        try {
            transitionTo(LoaderState.STOPPING);
            logger.info("Stopping Fv2j3 loader");
            List<String> orderedIds = context.modRegistry().orderedIds();
            for (int i = orderedIds.size() - 1; i >= 0; i--) {
                var container = context.modRegistry().get(orderedIds.get(i));
                if (container.runtime() instanceof DefaultModRuntime runtime) {
                    runtime.stop();
                    runtime.close();
                }
            }
            context.modRegistry().clear();
            // Final flush: persist any last-second config changes before
            // the loader shuts down.
            saveAllConfigs();
            transitionTo(LoaderState.STOPPED);
        } catch (RuntimeException ex) {
            fail(ex);
            throw new LoaderInitializationException("Fv2j3 loader shutdown failed: " + ex.getMessage(), ex);
        }
    }

    public void fail(String message, Throwable cause) {
        logger.error(message, cause);
        transitionTo(LoaderState.FAILED);
    }

    public void fail(Throwable throwable) {
        fail("Fv2j3 loader failed", throwable);
    }

    private static ModSide modSideOf(LoaderSide side) {
        return switch (side) {
            case CLIENT -> ModSide.CLIENT;
            case DEDICATED_SERVER -> ModSide.SERVER;
            case BOTH -> ModSide.BOTH;
        };
    }

    private void discoverAndRegisterSources(List<ModSource> sources) {
        if (sources.isEmpty()) {
            logger.info("No mod sources configured for this runtime.");
            return;
        }

        List<ModCandidate> discovered = new ArrayList<>();
        for (ModSource source : sources) {
            try {
                List<ModCandidate> candidates = switch (source.sourceType()) {
                    case "directory" -> new DirectoryModDiscovery().discover(source);
                    case "jar" -> new JarModDiscovery().discover(source);
                    default -> List.of();
                };
                if (candidates.isEmpty()) {
                    logger.info("No mod metadata found in source: " + source.sourceId());
                    continue;
                }
                discovered.addAll(candidates);
            } catch (RuntimeException ex) {
                logger.error("Failed to discover mods from source '" + source.sourceId() + "'.", ex);
            }
        }

        if (discovered.isEmpty()) {
            logger.info("No valid mod candidates were discovered.");
            return;
        }

        for (ModCandidate candidate : discovered) {
            if (!candidate.validationResult().valid()) {
                logger.warn("Skipping invalid mod candidate '" + candidate.id() + "' from " + candidate.sourceName());
                continue;
            }
            ModSide modSide = candidate.descriptor().side();
            ModSide loaderSide = modSideOf(environment.side());
            if (!modSide.isCompatibleWith(loaderSide)) {
                logger.warn("Skipping mod '" + candidate.id() + "': it declares side '"
                        + modSide.value() + "' which cannot load on the '" + loaderSide.value()
                        + "' side. Remove it from this instance's mods directory or change its side declaration.");
                continue;
            }
            try {
                context.modRegistry().register(candidate);
                logger.info("Registered mod candidate: " + candidate.id() + " from " + candidate.sourceName());
            } catch (IllegalArgumentException ex) {
                logger.error("Rejected mod candidate '" + candidate.id() + "' during registration.", ex);
            }
        }
        // Real per-mod counts: registered after each successful registration.
        int registered = context.modRegistry().all().size();
        progress("discovering", null, registered, discovered.size());
    }

    private List<ModSource> resolveModSources(Path modDirectory) {
        if (modDirectory == null || modDirectory.toString().isBlank()) {
            return List.of();
        }

        Path normalized = modDirectory.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            logger.info("Mod directory does not exist: " + normalized + ". Loader will continue without mods.");
            return List.of();
        }
        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("Mod source path must be a directory: " + normalized);
        }

        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(normalized)) {
            for (Path entry : stream) {
                entries.add(entry);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to inspect mod directory '" + normalized + "'.", ex);
        }

        entries.sort(Comparator.comparing(path -> path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT)));
        List<ModSource> sources = new ArrayList<>();
        for (Path entry : entries) {
            if (Files.isDirectory(entry)) {
                sources.add(ModSource.directory(entry));
            } else if (entry.getFileName() != null && entry.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                sources.add(ModSource.jar(entry));
            }
        }
        return List.copyOf(sources);
    }

    private static Path defaultModDirectory() {
        return Path.of("mods");
    }

    private void transitionTo(LoaderState next) {
        LoaderState previous = this.state;
        if (!previous.canTransitionTo(next)) {
            throw new IllegalStateException("Illegal state transition from " + previous + " to " + next);
        }
        this.state = next;
        logger.debug("Loader state transition: " + previous + " -> " + next);
    }
}
