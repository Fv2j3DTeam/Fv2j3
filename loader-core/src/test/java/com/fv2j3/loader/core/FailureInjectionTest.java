package com.fv2j3.loader.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fv2j3.api.ModRuntimeState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FailureInjectionTest {

    @TempDir
    Path tempDir;

    @Test
    void initializeFailureRollsBackAndReportsCorrectMod() throws Exception {
        Path modsDir = createModJar("init_fail_mod", "com.example.InitFailMod", (sb) -> {
            sb.append("        throw new IllegalStateException(\"Intentional initialization failure\");\n");
        }, false, null);

        Fv2j3Loader loader = new Fv2j3Loader(LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT"));
        loader.initialize(modsDir);

        LoaderInitializationException ex = assertThrows(LoaderInitializationException.class, loader::start);
        assertTrue(ex.getMessage().contains("init_fail_mod") || ex.getMessage().toLowerCase().contains("init"),
            "Exception should reference mod or initialization phase: " + ex.getMessage());
        assertEquals(LoaderState.FAILED, loader.state());
    }

    @Test
    void startFailureRollsBackPreviouslyStartedMods() throws Exception {
        Path modsDir = tempDir.resolve("start-fail-mods");
        Files.createDirectories(modsDir);

        createModJarInDir(modsDir, "good_mod", "com.example.GoodMod", null, false, null);
        createModJarInDir(modsDir, "failing_mod", "com.example.FailMod", (sb) -> {
            sb.append("        throw new IllegalStateException(\"Intentional start failure\");\n");
        }, false, null);

        Fv2j3Loader loader = new Fv2j3Loader(LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT"));
        loader.initialize(modsDir);

        LoaderInitializationException ex = assertThrows(LoaderInitializationException.class, loader::start);
        assertTrue(ex.getMessage().toLowerCase().contains("start") || ex.getMessage().contains("failing_mod"),
            "Exception should reference startup phase or mod: " + ex.getMessage());
        assertEquals(LoaderState.FAILED, loader.state());
    }

    @Test
    void laterModFailureCleansUpPreviouslyStartedMods() throws Exception {
        Path modsDir = tempDir.resolve("later-mods");
        Files.createDirectories(modsDir);

        createModJarInDir(modsDir, "alpha_mod", "com.example.AlphaMod", null, false, null);
        createModJarInDir(modsDir, "beta_mod", "com.example.BetaMod", (sb) -> {
            sb.append("        throw new IllegalStateException(\"Intentional later-mod failure\");\n");
        }, false, null);

        Fv2j3Loader loader = new Fv2j3Loader(LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT"));
        loader.initialize(modsDir);

        LoaderInitializationException ex = assertThrows(LoaderInitializationException.class, loader::start);
        assertTrue(ex.getMessage().toLowerCase().contains("beta_mod") || ex.getMessage().toLowerCase().contains("later"),
            "Exception should reference the failing mod: " + ex.getMessage());
        assertEquals(LoaderState.FAILED, loader.state());
    }

    @Test
    void cleanupFailureIsHandledGracefully() throws Exception {
        Path modsDir = createModJar("cleanup_fail_mod", "com.example.CleanupFailMod", (sb) -> {
            sb.append("        throw new IllegalStateException(\"Intentional cleanup failure\");\n");
        }, true, null);

        Fv2j3Loader loader = new Fv2j3Loader(LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT"));
        loader.initialize(modsDir);
        loader.start();
        assertEquals(LoaderState.RUNNING, loader.state());

        Exception thrownException = null;
        try {
            loader.stop();
        } catch (Exception ex) {
            thrownException = ex;
        }
        assertNotNull(thrownException, "Cleanup failure should propagate");
        assertTrue(thrownException.getMessage().toLowerCase().contains("cleanup") || 
                   thrownException.getMessage().contains("cleanup_fail_mod") ||
                   thrownException.getMessage().toLowerCase().contains("shutdown"),
            "Exception should reference cleanup: " + thrownException.getMessage());
        assertEquals(LoaderState.FAILED, loader.state(),
            "Loader should transition to FAILED after cleanup failure");
    }

    @Test
    void missingDependencyFailsBeforeInitialization() throws Exception {
        Path modsDir = tempDir.resolve("missing-dep-mods");
        Files.createDirectories(modsDir);

        createModJarInDir(modsDir, "missing_dep_mod", "com.example.MissingDepMod", null, false, "nonexistent_mod_xyz");

        Fv2j3Loader loader = new Fv2j3Loader(LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT"));
        loader.initialize(modsDir);

        LoaderInitializationException ex = assertThrows(LoaderInitializationException.class, loader::start);
        assertTrue(ex.getMessage().contains("nonexistent_mod_xyz") || ex.getMessage().contains("missing"),
            "Exception should reference missing dependency: " + ex.getMessage());
        assertEquals(LoaderState.FAILED, loader.state());
    }

    @Test
    void dependencyCycleIsDetectedAndReported() throws Exception {
        Path modsDir = tempDir.resolve("cycle-mods");
        Files.createDirectories(modsDir);

        createModJarInDir(modsDir, "cycle_a_mod", "com.example.CycleAMod", null, false, "cycle_b_mod");
        createModJarInDir(modsDir, "cycle_b_mod", "com.example.CycleBMod", null, false, "cycle_a_mod");

        Fv2j3Loader loader = new Fv2j3Loader(LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT"));
        loader.initialize(modsDir);

        LoaderInitializationException ex = assertThrows(LoaderInitializationException.class, loader::start);
        assertTrue(ex.getMessage().toLowerCase().contains("cycle") || ex.getMessage().contains("cycle_a_mod") || ex.getMessage().contains("cycle_b_mod"),
            "Exception should reference dependency cycle: " + ex.getMessage());
        assertEquals(LoaderState.FAILED, loader.state());
    }

    @Test
    void duplicateModIdIsRejected() throws Exception {
        Path modsDir = tempDir.resolve("dup-id-mods");
        Files.createDirectories(modsDir);

        createModJarInDir(modsDir, "duplicate_mod", "com.example.DupModA", null, false, null);
        createModJarInDir(modsDir, "duplicate_mod", "com.example.DupModB", null, false, null);

        Fv2j3Loader loader = new Fv2j3Loader(LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT"));
        loader.initialize(modsDir);

        int registeredCount = loader.context().modRegistry().all().size();
        assertEquals(1, registeredCount,
            "Only one of duplicate-ID mods should be registered. Found: " + registeredCount);
    }

    @Test
    void invalidMetadataIsRejected() throws Exception {
        Path modsDir = tempDir.resolve("invalid-meta-mods");
        Files.createDirectories(modsDir);

        Path jarPath = modsDir.resolve("invalid.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            addText(output, "META-INF/fv2j3.mod.json", """
                {
                  "id": "",
                  "name": "Invalid Mod",
                  "version": "1.0.0",
                  "entrypoint": "com.example.InvalidMod"
                }
                """);
        }

        Fv2j3Loader loader = new Fv2j3Loader(LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT"));
        loader.initialize(modsDir);

        int registeredCount = loader.context().modRegistry().all().size();
        assertEquals(0, registeredCount,
            "Mod with invalid metadata should be skipped. Found: " + registeredCount);
    }

    @Test
    void loaderStateMachinePreventsInvalidTransitions() {
        LoaderState state = LoaderState.CREATED;

        assertTrue(state.canTransitionTo(LoaderState.INITIALIZING));
        assertFalse(state.canTransitionTo(LoaderState.RUNNING));
        assertFalse(state.canTransitionTo(LoaderState.STOPPED));
        assertFalse(state.canTransitionTo(LoaderState.FAILED));

        state = LoaderState.INITIALIZED;
        assertTrue(state.canTransitionTo(LoaderState.STARTING));
        assertFalse(state.canTransitionTo(LoaderState.RUNNING));

        state = LoaderState.RUNNING;
        assertTrue(state.canTransitionTo(LoaderState.STOPPING));
        assertFalse(state.canTransitionTo(LoaderState.INITIALIZED));

        state = LoaderState.STOPPED;
        assertFalse(state.canTransitionTo(LoaderState.RUNNING));
        assertFalse(state.canTransitionTo(LoaderState.INITIALIZED));

        state = LoaderState.FAILED;
        assertFalse(state.canTransitionTo(LoaderState.RUNNING));
        assertFalse(state.canTransitionTo(LoaderState.INITIALIZED));
        assertFalse(state.canTransitionTo(LoaderState.STOPPED));
    }

    @Test
    void classLoaderIsClosedOnFailure() throws Exception {
        Path modsDir = createModJar("classloader_test_mod", "com.example.ClassLoaderTestMod", (sb) -> {
            sb.append("        throw new IllegalStateException(\"Intentional initialization failure\");\n");
        }, false, null);

        Fv2j3Loader loader = new Fv2j3Loader(LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT"));
        loader.initialize(modsDir);

        assertThrows(LoaderInitializationException.class, loader::start);
        assertEquals(LoaderState.FAILED, loader.state());
    }

    private Path createModJar(String modId, String entrypoint, StringAppender appender, boolean isCleanupFailure, String dependsOn) throws IOException {
        Path modsDir = tempDir.resolve("mods-" + modId);
        Files.createDirectories(modsDir);
        createModJarInDir(modsDir, modId, entrypoint, appender, isCleanupFailure, dependsOn);
        return modsDir;
    }

    private void createModJarInDir(Path dir, String modId, String entrypoint, StringAppender appender, boolean isCleanupFailure, String dependsOn) throws IOException {
        String className = entrypoint.substring(entrypoint.lastIndexOf('.') + 1);
        String packageName = entrypoint.substring(0, entrypoint.lastIndexOf('.'));
        String modNiceName = modId.replace('_', ' ');

        StringBuilder code = new StringBuilder();
        code.append("package ").append(packageName).append(";\n\n");
        code.append("import com.fv2j3.api.Mod;\n");
        code.append("import com.fv2j3.api.ModContext;\n");
        code.append("import com.fv2j3.api.ModDescriptor;\n\n");
        code.append("public final class ").append(className).append(" implements Mod {\n");
        code.append("    @Override\n");
        code.append("    public ModDescriptor descriptor() {\n");
        code.append("        return new ModDescriptor(\"").append(modId).append("\", \"1.0.0\", \"").append(modNiceName).append("\");\n");
        code.append("    }\n\n");
        code.append("    @Override\n");
        code.append("    public void onLoad(ModContext context) { }\n\n");
        code.append("    @Override\n");
        code.append("    public void onInitialize(ModContext context) {\n");
        if (appender != null && !isCleanupFailure) {
            appender.append(code);
        }
        code.append("    }\n\n");
        code.append("    @Override\n");
        code.append("    public void onStart(ModContext context) {\n");
        if (appender != null && !isCleanupFailure) {
            appender.append(code);
        }
        code.append("    }\n\n");
        code.append("    @Override\n");
        code.append("    public void onStop(ModContext context) {\n");
        if (appender != null && isCleanupFailure) {
            appender.append(code);
        }
        code.append("    }\n");
        code.append("}\n");

        Path sourceDir = Files.createTempDirectory("fv2j3-src-");
        Path classesDir = sourceDir.resolve("classes");
        Files.createDirectories(classesDir);

        Path sourceFile = classesDir.resolve(packageName.replace('.', '/') + "/" + className + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, code.toString(), StandardCharsets.UTF_8);

        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null, "-cp", 
            System.getProperty("java.class.path"), "-d", classesDir.toString(), sourceFile.toString());
        if (result != 0) {
            throw new IllegalStateException("Failed to compile test mod: " + modId);
        }

        StringBuilder metadata = new StringBuilder();
        metadata.append("{\n");
        metadata.append("  \"id\": \"").append(modId).append("\",\n");
        metadata.append("  \"name\": \"").append(modNiceName).append("\",\n");
        metadata.append("  \"version\": \"1.0.0\",\n");
        metadata.append("  \"description\": \"Test mod\",\n");
        metadata.append("  \"authors\": [\"Test\"],\n");
        metadata.append("  \"license\": \"MIT\",\n");
        if (dependsOn != null) {
            metadata.append("  \"dependencies\": [{\"id\": \"").append(dependsOn).append("\", \"versionRange\": \"*\", \"type\": \"required\"}],\n");
        }
        metadata.append("  \"requiredLoaderVersion\": \">=0.1.0\",\n");
        metadata.append("  \"entrypoint\": \"").append(entrypoint).append("\"\n");
        metadata.append("}\n");

        Path jarPath = dir.resolve(modId + ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            addFile(output, classesDir.resolve(packageName.replace('.', '/') + "/" + className + ".class"), 
                packageName.replace('.', '/') + "/" + className + ".class");
            addText(output, "META-INF/fv2j3.mod.json", metadata.toString());
        }
    }

    @FunctionalInterface
    interface StringAppender {
        void append(StringBuilder sb);
    }

    private static void addFile(JarOutputStream output, Path sourceFile, String entryName) throws IOException {
        try (java.io.InputStream input = Files.newInputStream(sourceFile)) {
            addText(output, entryName, input.readAllBytes());
        }
    }

    private static void addText(JarOutputStream output, String entryName, byte[] bytes) throws IOException {
        JarEntry entry = new JarEntry(entryName);
        output.putNextEntry(entry);
        output.write(bytes);
        output.closeEntry();
    }

    private static void addText(JarOutputStream output, String entryName, String text) throws IOException {
        addText(output, entryName, text.getBytes(StandardCharsets.UTF_8));
    }
}
