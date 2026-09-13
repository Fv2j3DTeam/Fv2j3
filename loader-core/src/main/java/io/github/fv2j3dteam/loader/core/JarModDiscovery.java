package io.github.fv2j3dteam.loader.core;

import io.github.fv2j3dteam.api.ModDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

public final class JarModDiscovery implements ModDiscovery {
    @Override
    public List<ModCandidate> discover(ModSource source) {
        if (source == null || source.location() == null) {
            throw new IllegalArgumentException("Mod source location is required.");
        }
        Path jarPath = source.location();
        if (!Files.isRegularFile(jarPath)) {
            throw new IllegalArgumentException("Jar mod source must be a file: " + jarPath);
        }

        String metadataEntry = "META-INF/fv2j3.mod.json";
        List<ModCandidate> result = new ArrayList<>();
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            var entry = jarFile.getJarEntry(metadataEntry);
            if (entry == null) {
                return List.of();
            }
            try (InputStream input = jarFile.getInputStream(entry)) {
                ModDescriptor descriptor = ModMetadataParser.parse(input);
                ValidationResult validationResult = ModMetadataValidator.validate(descriptor);
                result.add(new ModCandidate(
                        descriptor.id(),
                        jarPath.getFileName() == null ? jarPath.toString() : jarPath.getFileName().toString(),
                        "jar",
                        descriptor,
                        validationResult,
                        metadataEntry,
                        jarPath
                ));
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read mod metadata from jar '" + jarPath + "'.", ex);
        } catch (ModMetadataParseException ex) {
            throw new IllegalArgumentException("Invalid metadata in jar '" + jarPath + "'.", ex);
        }
        return result;
    }
}
