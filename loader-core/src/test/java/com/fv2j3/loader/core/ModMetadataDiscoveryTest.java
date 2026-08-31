package com.fv2j3.loader.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fv2j3.api.ModDependency;
import com.fv2j3.api.ModDescriptor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;

class ModMetadataDiscoveryTest {
    @Test
    void parsesValidMetadata() throws ModMetadataParseException {
        String json = """
                {
                  "id": "example_mod",
                  "name": "Example Mod",
                  "version": "1.0.0",
                  "description": "Example Fv2j3 mod",
                  "authors": ["Author"],
                  "license": "MIT",
                  "dependencies": [],
                  "optionalDependencies": [],
                  "requiredLoaderVersion": ">=1.0.0",
                  "entrypoint": "com.example.ExampleMod"
                }
                """;

        ModDescriptor descriptor = ModMetadataParser.parse(json);

        assertEquals("example_mod", descriptor.id());
        assertEquals("Example Mod", descriptor.name());
        assertEquals("com.example.ExampleMod", descriptor.entrypoint());
    }

    @Test
    void rejectsMissingEntryPoint() {
        String json = """
                {
                  "id": "example_mod",
                  "name": "Example Mod",
                  "version": "1.0.0"
                }
                """;

        ModMetadataParseException ex = assertThrows(ModMetadataParseException.class, () -> ModMetadataParser.parse(json));
        assertTrue(ex.getMessage().contains("entrypoint"));
    }

    @Test
    void rejectsMinecraftVersionMetadata() {
        String json = """
                {
                  "id": "example_mod",
                  "name": "Example Mod",
                  "version": "1.0.0",
                  "entrypoint": "com.example.ExampleMod",
                  "requiredMinecraftVersion": "1.12.2"
                }
                """;

        ModMetadataParseException ex = assertThrows(ModMetadataParseException.class, () -> ModMetadataParser.parse(json));
        assertTrue(ex.getMessage().contains("requiredMinecraftVersion"));
    }

    @Test
    void directoryDiscoveryReadsMetadata() throws IOException {
        Path dir = Files.createTempDirectory("fv2j3-mod-");
        Path metaDir = dir.resolve("META-INF");
        Files.createDirectories(metaDir);
        Files.writeString(
                metaDir.resolve("fv2j3.mod.json"),
                """
                {
                  "id": "directory_mod",
                  "name": "Directory Mod",
                  "version": "1.0.0",
                  "entrypoint": "com.example.DirectoryMod",
                  "authors": ["Author"],
                  "license": "MIT"
                }
                """,
                StandardCharsets.UTF_8
        );

        List<ModCandidate> candidates = new DirectoryModDiscovery().discover(ModSource.directory(dir));

        assertEquals(1, candidates.size());
        assertEquals("directory_mod", candidates.getFirst().id());
    }

    @Test
    void jarDiscoveryReadsMetadataWithoutLoadingClass() throws IOException {
        Path jar = Files.createTempFile("fv2j3-mod-", ".jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            JarEntry entry = new JarEntry("META-INF/fv2j3.mod.json");
            out.putNextEntry(entry);
            out.write("""
                    {
                      "id": "jar_mod",
                      "name": "Jar Mod",
                      "version": "1.0.0",
                      "entrypoint": "com.example.JarMod",
                      "authors": ["Author"],
                      "license": "MIT"
                    }
                    """.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        List<ModCandidate> candidates = new JarModDiscovery().discover(ModSource.jar(jar));

        assertEquals(1, candidates.size());
        assertEquals("jar_mod", candidates.getFirst().descriptor().id());
        assertFalse(candidates.getFirst().descriptor().entrypoint().isBlank());
    }

    @Test
    void registryRejectsDuplicateIds() {
        ModRegistry registry = new ModRegistry();
        ModCandidate candidate = new ModCandidate(
                "dup_mod",
                "source",
                "directory",
                new ModDescriptor("dup_mod", "1.0.0", "Dup Mod", "", List.of(), "MIT", List.of(), List.of(), ">=0.1.0", "com.example.DupMod"),
                ValidationResult.validResult(),
                "META-INF/fv2j3.mod.json"
        );

        registry.register(candidate);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> registry.register(candidate));
        assertTrue(ex.getMessage().contains("Duplicate mod id"));
    }

    @Test
    void registryValidatesDependenciesAndBuildsContainerState() {
        LoaderEnvironment environment = LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT");
        LoaderContext loaderContext = new LoaderContext(
                new Fv2j3Loader(environment),
                environment,
                List.of("base_mod"),
                ClassLoader.getSystemClassLoader(),
                null,
                new StandardLoaderLogger("Fv2j3"),
                Map.of("mode", "testing")
        );

        ModRegistry registry = loaderContext.modRegistry();
        ModCandidate base = new ModCandidate(
                "base_mod",
                "source",
                "directory",
                new ModDescriptor("base_mod", "1.0.0", "Base Mod", "", List.of(), "MIT", List.of(), List.of(), ">=0.1.0", "com.example.BaseMod"),
                ValidationResult.validResult(),
                "META-INF/fv2j3.mod.json"
        );
        registry.register(base);

        ModCandidate dependent = new ModCandidate(
                "dependent_mod",
                "source",
                "directory",
                new ModDescriptor(
                        "dependent_mod",
                        "1.0.0",
                        "Dependent Mod",
                        "",
                        List.of(),
                        "MIT",
                        List.of(new ModDependency("base_mod", ">=1.0.0", com.fv2j3.api.DependencyType.REQUIRED)),
                        List.of(),
                        ">=0.1.0",
                        "com.example.DependentMod"
                ),
                ValidationResult.validResult(),
                "META-INF/fv2j3.mod.json"
        );

        registry.register(dependent);
        assertTrue(registry.contains("dependent_mod"));
        assertEquals(2, registry.all().size());
    }

}
