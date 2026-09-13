package io.github.fv2j3dteam.minecraft.compat;

import io.github.fv2j3dteam.api.ModContainer;
import io.github.fv2j3dteam.api.ModRuntime;
import io.github.fv2j3dteam.api.ModRuntimeState;
import io.github.fv2j3dteam.loader.core.Fv2j3Loader;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Holds the live Fv2j3Loader instance for the running Minecraft JVM.
 * Populated by MinecraftBootstrap before Minecraft.main is invoked, so the
 * GUI running inside Minecraft's classloader reads mod state from the SAME
 * Loader instance that loaded the mods.
 */
public final class Fv2j3RuntimeBridge {
    private static final AtomicReference<Fv2j3Loader> ACTIVE_LOADER = new AtomicReference<>();
    private static volatile List<String> SNAPSHOT = List.of();

    private Fv2j3RuntimeBridge() {
    }

    public static void register(Fv2j3Loader loader) {
        ACTIVE_LOADER.set(loader);
        if (loader != null && loader.context() != null && loader.context().modRegistry() != null) {
            SNAPSHOT = snapshot(loader);
        }
    }

    public static Fv2j3Loader activeLoader() {
        return ACTIVE_LOADER.get();
    }

    public static List<String> snapshot(Fv2j3Loader loader) {
        if (loader == null || loader.context() == null || loader.context().modRegistry() == null) {
            return List.of();
        }
        return loader.context().modRegistry().all().stream()
                .map(container -> {
                    ModRuntime runtime = container.runtime();
                    String state = runtime == null ? "DISCOVERED" : runtime.state().name();
                    return container.id() + " | " + container.descriptor().name() + " | " + state;
                })
                .collect(Collectors.toList());
    }

    public static List<String> currentMods() {
        Fv2j3Loader loader = ACTIVE_LOADER.get();
        if (loader == null) {
            return SNAPSHOT;
        }
        return snapshot(loader);
    }
}
