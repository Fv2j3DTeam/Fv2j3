package com.fv2j3.loader.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LoaderRuntimeIntegrationTest {
    @Test
    void startsWithEmptyModDirectory() throws IOException, LoaderInitializationException {
        Path modsDirectory = Files.createTempDirectory("fv2j3-empty-mods-");
        Fv2j3Loader loader = new Fv2j3Loader(LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT"));

        loader.initialize(modsDirectory);
        loader.start();

        assertEquals(LoaderState.RUNNING, loader.state());
        assertTrue(loader.context().modRegistry().all().isEmpty());

        loader.stop();
        assertEquals(LoaderState.STOPPED, loader.state());
    }

    @Test
    void discoversAndOrdersDependentMods() throws IOException, LoaderInitializationException {
        Path modsDirectory = Files.createTempDirectory("fv2j3-runtime-mods-");
        createMod(modsDirectory.resolve("base_mod"), "base_mod", "Base Mod", "");
        createMod(modsDirectory.resolve("dependent_mod"), "dependent_mod", "Dependent Mod", "base_mod");

        Fv2j3Loader loader = new Fv2j3Loader(LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT"));
        loader.initialize(modsDirectory);
        loader.start();

        assertTrue(loader.context().modRegistry().contains("base_mod"));
        assertTrue(loader.context().modRegistry().contains("dependent_mod"));
        assertEquals("[base_mod, dependent_mod]", loader.context().modRegistry().orderedIds().toString());
        assertEquals(LoaderState.RUNNING, loader.state());
    }

    @Test
    void invalidMetadataDoesNotBlockValidMods() throws IOException, LoaderInitializationException {
        Path modsDirectory = Files.createTempDirectory("fv2j3-invalid-mods-");
        createInvalidMod(modsDirectory.resolve("broken_mod"));
        createMod(modsDirectory.resolve("valid_mod"), "valid_mod", "Valid Mod", "");

        Fv2j3Loader loader = new Fv2j3Loader(LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT"));
        loader.initialize(modsDirectory);
        loader.start();

        assertTrue(loader.context().modRegistry().contains("valid_mod"));
        assertFalse(loader.context().modRegistry().contains("broken_mod"));
    }

    @Test
    void dependencyCycleFailsStartup() throws IOException, LoaderInitializationException {
        Path modsDirectory = Files.createTempDirectory("fv2j3-cycle-mods-");
        createMod(modsDirectory.resolve("a_mod"), "a_mod", "A Mod", "b_mod");
        createMod(modsDirectory.resolve("b_mod"), "b_mod", "B Mod", "a_mod");

        Fv2j3Loader loader = new Fv2j3Loader(LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT"));
        loader.initialize(modsDirectory);

        LoaderInitializationException ex = assertThrows(LoaderInitializationException.class, loader::start);
        assertEquals(LoaderState.FAILED, loader.state());
        assertTrue(ex.getMessage().contains("Dependency cycle"));
    }

    private static void createMod(Path root, String id, String name, String dependencyId) throws IOException {
        Path metadataDir = root.resolve("META-INF");
        Files.createDirectories(metadataDir);

        String dependencyJson = dependencyId.isBlank()
                ? ""
                : "\n  \"dependencies\": [{\"id\": \"" + dependencyId + "\", \"versionRange\": \"*\", \"type\": \"required\"}],";

        String json = """
                {
                  "id": "%s",
                  "name": "%s",
                  "version": "1.0.0",
                  "description": "Generated test mod",
                  "authors": ["Test Author"],
                  "license": "MIT",
                  "requiredLoaderVersion": ">=0.1.0",%s
                  "entrypoint": "com.example.%s"
                }
                """.formatted(id, name, dependencyJson, titleCase(id));

        Files.writeString(metadataDir.resolve("fv2j3.mod.json"), json, StandardCharsets.UTF_8);
    }

    private static void createInvalidMod(Path root) throws IOException {
        Path metadataDir = root.resolve("META-INF");
        Files.createDirectories(metadataDir);
        Files.writeString(
                metadataDir.resolve("fv2j3.mod.json"),
                """
                {
                  "id": "broken_mod",
                  "name": "Broken Mod",
                  "version": "1.0.0",
                  "entrypoint": "invalid entrypoint"
                }
                """,
                StandardCharsets.UTF_8
        );
    }

    private static String titleCase(String value) {
        String[] parts = value.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }
}
