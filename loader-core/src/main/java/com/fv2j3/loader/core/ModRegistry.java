package com.fv2j3.loader.core;

import com.fv2j3.api.ModContainer;
import com.fv2j3.api.ModContainerState;
import com.fv2j3.api.ModConfigProvider;
import com.fv2j3.api.ModContext;
import com.fv2j3.api.ModDependency;
import com.fv2j3.api.ModDescriptor;
import com.fv2j3.api.ModRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarFile;

public final class ModRegistry implements com.fv2j3.api.ModRegistryView {
    private final Map<String, ModContainer> registry = new LinkedHashMap<>();

    public void register(ModCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (!candidate.validationResult().valid()) {
            throw new IllegalArgumentException("Candidate validation failed for mod '" + candidate.id() + "'.");
        }
        String id = candidate.descriptor().id();
        if (registry.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate mod id: " + id);
        }

        DefaultModContainer container = new DefaultModContainer(
                candidate.descriptor(),
                candidate.sourceName(),
                candidate.sourceType(),
                ModContainerState.DISCOVERED,
                null,
                candidate.sourcePath()
        );
        container.setState(ModContainerState.VALIDATED);
        registry.put(id, container);
    }

    public void registerAll(List<ModCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        for (ModCandidate candidate : candidates) {
            register(candidate);
        }
    }

    public boolean contains(String id) {
        return registry.containsKey(Objects.requireNonNull(id, "id"));
    }

    public ModContainer get(String id) {
        return registry.get(Objects.requireNonNull(id, "id"));
    }

    public List<String> orderedIds() {
        return new ModDependencyGraph(registry.values()).resolveOrder();
    }

    public List<ModContainer> all() {
        return Collections.unmodifiableList(new ArrayList<>(registry.values()));
    }

    @Override
    public List<com.fv2j3.api.ModInfo> mods() {
        return registry.values().stream()
                .map(container -> new com.fv2j3.api.ModInfo(
                        container.id(),
                        container.descriptor().name(),
                        container.descriptor().version(),
                        container.runtime() == null ? com.fv2j3.api.ModRuntimeState.READY : container.runtime().state()
                ))
                .toList();
    }

    public void clear() {
        registry.clear();
    }

    public void validateDependencies(LoaderContext loaderContext) {
        Objects.requireNonNull(loaderContext, "loaderContext");
        if (registry.isEmpty()) {
            return;
        }

        List<String> orderedIds = orderedIds();
        for (String id : orderedIds) {
            ModContainer container = registry.get(id);
            if (!(container instanceof DefaultModContainer defaultContainer)) {
                throw new IllegalStateException("Unsupported mod container type for id '" + id + "'.");
            }

            ModDescriptor descriptor = container.descriptor();
            for (ModDependency dependency : descriptor.dependencies()) {
                if (dependency.id().equals(descriptor.id())) {
                    throw new IllegalArgumentException("Mod '" + descriptor.id() + "' cannot depend on itself.");
                }
                if (!registry.containsKey(dependency.id())) {
                    throw new IllegalArgumentException(
                            "Missing required dependency '" + dependency.id() + "' for mod '" + descriptor.id() + "'."
                    );
                }
            }

            for (ModDependency dependency : descriptor.optionalDependencies()) {
                if (!registry.containsKey(dependency.id())) {
                    loaderContext.logger().warn(
                            "Optional dependency '" + dependency.id() + "' missing for mod '" + descriptor.id() + "'."
                    );
                }
            }

            defaultContainer.setState(ModContainerState.READY);
            ModConfigProvider configProvider = loaderContext.configProvider();
            defaultContainer.setContext(new ModContext(
                    descriptor,
                    loaderContext,
                    loaderContext.logger(),
                    this,
                    configProvider,
                    Map.of(
                            "registry", this,
                            "sourceName", defaultContainer.sourceName(),
                            "sourceType", defaultContainer.sourceType(),
                            "sourcePath", defaultContainer.sourcePath()
                    )
            ));
        }
    }

    public List<ModRuntime> createRuntimes(ClassLoader parentLoader) {
        List<String> orderedIds = orderedIds();
        List<ModRuntime> created = new ArrayList<>();
        for (String id : orderedIds) {
            ModContainer container = registry.get(id);
            if (!(container instanceof DefaultModContainer defaultContainer)) {
                throw new IllegalStateException("Unsupported mod container type for id '" + id + "'.");
            }
            if (defaultContainer.runtime() != null) {
                created.add(defaultContainer.runtime());
                continue;
            }
            List<ClassLoader> dependencyLoaders = new ArrayList<>();
            if (!hasEntrypointClass(defaultContainer)) {
                continue;
            }
            for (ModDependency dependency : defaultContainer.descriptor().dependencies()) {
                ModContainer dependencyContainer = registry.get(dependency.id());
                if (dependencyContainer != null && dependencyContainer.runtime() != null && dependencyContainer.runtime().classLoader() != null) {
                    dependencyLoaders.add(dependencyContainer.runtime().classLoader());
                }
            }
            for (ModDependency dependency : defaultContainer.descriptor().optionalDependencies()) {
                ModContainer dependencyContainer = registry.get(dependency.id());
                if (dependencyContainer != null && dependencyContainer.runtime() != null && dependencyContainer.runtime().classLoader() != null) {
                    dependencyLoaders.add(dependencyContainer.runtime().classLoader());
                }
            }
            ModRuntime runtime = DefaultModRuntime.create(defaultContainer, parentLoader, dependencyLoaders);
            defaultContainer.attachRuntime(runtime);
            created.add(runtime);
        }
        return List.copyOf(created);
    }

    private boolean hasEntrypointClass(DefaultModContainer container) {
        if (container == null || container.sourcePath() == null) {
            return false;
        }
        String entrypoint = container.descriptor().entrypoint();
        if (entrypoint == null || entrypoint.isBlank()) {
            return false;
        }
        String resourcePath = entrypoint.replace('.', '/') + ".class";
        Path sourcePath = container.sourcePath();
        if (Files.isDirectory(sourcePath)) {
            return Files.exists(sourcePath.resolve(resourcePath));
        }
        if (Files.isRegularFile(sourcePath)) {
            try (JarFile jarFile = new JarFile(sourcePath.toFile())) {
                return jarFile.getJarEntry(resourcePath) != null;
            } catch (IOException ex) {
                return false;
            }
        }
        return false;
    }
}
