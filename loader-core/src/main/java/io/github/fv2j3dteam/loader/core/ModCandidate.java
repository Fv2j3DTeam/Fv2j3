package io.github.fv2j3dteam.loader.core;

import io.github.fv2j3dteam.api.ModDescriptor;
import java.nio.file.Path;
import java.util.Objects;

public record ModCandidate(
        String id,
        String sourceName,
        String sourceType,
        ModDescriptor descriptor,
        ValidationResult validationResult,
        String metadataPath,
        Path sourcePath
) {
    public ModCandidate(String id, String sourceName, String sourceType, ModDescriptor descriptor) {
        this(id, sourceName, sourceType, descriptor, ValidationResult.validResult(), "", null);
    }

    public ModCandidate(
            String id,
            String sourceName,
            String sourceType,
            ModDescriptor descriptor,
            ValidationResult validationResult,
            String metadataPath
    ) {
        this(id, sourceName, sourceType, descriptor, validationResult, metadataPath, null);
    }

    public ModCandidate {
        id = Objects.requireNonNullElse(id, "unknown");
        sourceName = Objects.requireNonNullElse(sourceName, "unknown");
        sourceType = Objects.requireNonNullElse(sourceType, "unknown");
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        validationResult = validationResult == null ? ValidationResult.validResult() : validationResult;
        metadataPath = metadataPath == null ? "" : metadataPath;
        sourcePath = sourcePath == null || sourcePath.toString().isBlank() ? null : sourcePath;
    }
}
