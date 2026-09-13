package io.github.fv2j3dteam.loader.core;

import java.util.Objects;

public final class ModLoaderRuntime {
    private final LoaderEnvironment environment;

    public ModLoaderRuntime(LoaderEnvironment environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    public static ModLoaderRuntime createDefault() {
        return new ModLoaderRuntime(LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT"));
    }

    public LoaderEnvironment environment() {
        return environment;
    }

    public String minecraftVersion() {
        return environment.minecraftVersion();
    }

    public int javaTargetVersion() {
        return environment.javaMajorVersion();
    }

    public String platformSummary() {
        return "Minecraft " + environment.minecraftVersion() + " / Java " + environment.javaVersion() + " target";
    }
}
