package io.github.fv2j3dteam.loader.core;

import java.nio.file.Path;
import java.util.Objects;

public record ModSource(String sourceId, String sourceType, Path location) {
    public ModSource(String sourceId, String sourceType) {
        this(sourceId, sourceType, null);
    }

    public ModSource {
        sourceId = Objects.requireNonNullElse(sourceId, "unknown");
        sourceType = Objects.requireNonNullElse(sourceType, "unknown");
    }

    public static ModSource directory(Path directory) {
        return new ModSource(directory.toString(), "directory", directory);
    }

    public static ModSource jar(Path jar) {
        return new ModSource(jar.toString(), "jar", jar);
    }
}
