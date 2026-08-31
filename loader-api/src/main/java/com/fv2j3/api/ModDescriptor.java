package com.fv2j3.api;

import java.util.List;
import java.util.Objects;

public record ModDescriptor(
        String id,
        String version,
        String name,
        String description,
        List<String> authors,
        String license,
        List<ModDependency> dependencies,
        List<ModDependency> optionalDependencies,
        String requiredLoaderVersion,
        String entrypoint
) {
    public ModDescriptor(String id, String version, String name) {
        this(
                id,
                version,
                name,
                "",
                List.of(),
                "UNSPECIFIED",
                List.of(),
                List.of(),
                ">=0.1.0",
                defaultEntrypoint(id)
        );
    }

    public ModDescriptor {
        id = requireNonBlank(id, "Mod id");
        version = requireNonBlank(version, "Mod version");
        name = requireNonBlank(name, "Mod name");
        description = description == null ? "" : description;
        authors = authors == null ? List.of() : List.copyOf(authors);
        license = license == null || license.isBlank() ? "UNSPECIFIED" : license;
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        optionalDependencies = optionalDependencies == null ? List.of() : List.copyOf(optionalDependencies);
        requiredLoaderVersion = requiredLoaderVersion == null || requiredLoaderVersion.isBlank() ? ">=0.1.0" : requiredLoaderVersion;
        entrypoint = requireNonBlank(entrypoint, "Mod entrypoint");
    }

    public static String defaultEntrypoint(String id) {
        String normalized = Objects.requireNonNull(id, "Mod id must not be null");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Mod id must not be blank");
        }
        return normalized.replace('-', '_') + ".ModMain";
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
