package com.fv2j3.loader.core;

import com.fv2j3.api.ModDescriptor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DirectoryModDiscovery implements ModDiscovery {
    @Override
    public List<ModCandidate> discover(ModSource source) {
        if (source == null || source.location() == null) {
            throw new IllegalArgumentException("Mod source location is required.");
        }
        Path root = source.location();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Directory mod source must be a directory: " + root);
        }
        Path metadata = root.resolve("META-INF").resolve("fv2j3.mod.json");
        if (!Files.isRegularFile(metadata)) {
            return List.of();
        }

        List<ModCandidate> result = new ArrayList<>();
        try (var input = Files.newInputStream(metadata)) {
            ModDescriptor descriptor = ModMetadataParser.parse(input);
            ValidationResult validationResult = ModMetadataValidator.validate(descriptor);
            ModCandidate candidate = new ModCandidate(
                    descriptor.id(),
                    root.getFileName() == null ? root.toString() : root.getFileName().toString(),
                    "directory",
                    descriptor,
                    validationResult,
                    metadata.toString(),
                    root
            );
            result.add(candidate);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read mod metadata from directory '" + root + "'.", ex);
        } catch (ModMetadataParseException ex) {
            throw new IllegalArgumentException("Invalid metadata in directory '" + root + "'.", ex);
        }
        return result;
    }
}
