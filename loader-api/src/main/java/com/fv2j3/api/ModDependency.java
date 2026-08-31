package com.fv2j3.api;

import java.util.Objects;

public record ModDependency(String id, String versionRange, DependencyType type) {
    public ModDependency {
        id = Objects.requireNonNull(id, "mod dependency id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Mod dependency id must not be blank");
        }
        versionRange = versionRange == null || versionRange.isBlank() ? "*" : versionRange;
        type = type == null ? DependencyType.REQUIRED : type;
    }
}
