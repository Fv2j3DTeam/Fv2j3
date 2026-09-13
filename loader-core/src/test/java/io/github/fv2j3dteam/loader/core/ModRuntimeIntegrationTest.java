package io.github.fv2j3dteam.loader.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.fv2j3dteam.api.ModContainer;
import io.github.fv2j3dteam.api.ModRuntimeState;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class ModRuntimeIntegrationTest {
    @Test
    void loadsRealJarModAndRunsFullLifecycle() throws Exception {
        Path root = Files.createTempDirectory("fv2j3-real-runtime-");
        Path modsDir = root.resolve("mods");
        Files.createDirectories(modsDir);
        Path jarPath = createRuntimeModJar(modsDir.resolve("example_mod.jar"));

        Fv2j3Loader loader = new Fv2j3Loader(LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT"));
        loader.initialize(modsDir);
        loader.start();

        ModContainer container = loader.context().modRegistry().get("example_mod");
        assertNotNull(container);
        assertNotNull(container.runtime());
        assertNotNull(container.classLoader());
        assertEquals(ModRuntimeState.RUNNING, container.runtime().state());

        Object mod = container.mod();
        assertNotNull(mod);
        var eventsField = mod.getClass().getField("EVENTS");
        @SuppressWarnings("unchecked")
        List<String> events = (List<String>) eventsField.get(null);
        assertEquals(List.of("LOAD", "INITIALIZE", "START"), events);

        loader.stop();
        assertEquals(LoaderState.STOPPED, loader.state());
        assertEquals(List.of("LOAD", "INITIALIZE", "START", "STOP"), events);
    }

    @Test
    void cleansUpPreviouslyStartedModWhenLaterModFails() throws Exception {
        Path root = Files.createTempDirectory("fv2j3-failed-startup-");
        Path modsDir = root.resolve("mods");
        Files.createDirectories(modsDir);
        Path goodJar = createRuntimeModJar(modsDir.resolve("alpha_mod.jar"), "com.example.ok.GoodMod");
        Path failingJar = createFailingStartModJar(modsDir.resolve("zeta_mod.jar"), "zeta_mod", "com.example.fail.ZetaStartMod");

        Fv2j3Loader loader = new Fv2j3Loader(LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT"));
        loader.initialize(modsDir);

        LoaderInitializationException ex = assertThrows(LoaderInitializationException.class, loader::start);
        assertTrue(ex.getMessage().contains("onStart"));
        assertTrue(ex.getCause() instanceof IllegalStateException);
        assertTrue(ex.getCause().getCause() instanceof java.lang.reflect.InvocationTargetException);
        assertTrue(ex.getCause().getCause().getCause().getMessage().contains("boom"));
        assertEquals(LoaderState.FAILED, loader.state());
        assertEquals(ModRuntimeState.STOPPED, loader.context().modRegistry().get("alpha_mod").runtime().state());
    }

    private static Path createRuntimeModJar(Path jarPath) throws Exception {
        return createRuntimeModJar(jarPath, "com.example.runtime.ExampleRuntimeMod");
    }

    private static Path createRuntimeModJar(Path jarPath, String entrypoint) throws Exception {
        String className = entrypoint.substring(entrypoint.lastIndexOf('.') + 1);
        String packageName = entrypoint.substring(0, entrypoint.lastIndexOf('.'));
        String modId = jarPath.getFileName().toString().replace(".jar", "").replace('-', '_');
        String niceName = modId.replace('_', ' ');
        String sourcePath = packageName.replace('.', '/') + "/" + className + ".java";
        Path sourceDir = Files.createTempDirectory("fv2j3-real-runtime-src-");
        Path classesDir = sourceDir.resolve("classes");
        Files.createDirectories(classesDir);

        Path sourceFile = sourceDir.resolve(sourcePath);
        Path parent = sourceFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(
                sourceFile,
                """
                package %s;

                import io.github.fv2j3dteam.api.Mod;
                import io.github.fv2j3dteam.api.ModContext;
                import io.github.fv2j3dteam.api.ModDescriptor;
                import java.util.ArrayList;
                import java.util.List;

                public final class %s implements Mod {
                    public static final List<String> EVENTS = new ArrayList<>();

                    @Override
                    public ModDescriptor descriptor() {
                        return new ModDescriptor("%s", "1.0.0", "%s");
                    }

                    @Override
                    public void onLoad(ModContext context) {
                        EVENTS.add("LOAD");
                    }

                    @Override
                    public void onInitialize(ModContext context) {
                        EVENTS.add("INITIALIZE");
                    }

                    @Override
                    public void onStart(ModContext context) {
                        EVENTS.add("START");
                    }

                    @Override
                    public void onStop(ModContext context) {
                        EVENTS.add("STOP");
                    }
                }
                """.formatted(packageName, className, modId, niceName),
                StandardCharsets.UTF_8
        );

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Java compiler must be available for runtime integration test");
        int result = compiler.run(
                null,
                null,
                null,
                "-cp",
                System.getProperty("java.class.path"),
                "-d",
                classesDir.toString(),
                sourceFile.toString()
        );
        if (result != 0) {
            throw new IllegalStateException("Failed to compile generated runtime mod source");
        }

        String metadata = """
                {
                  "id": "%s",
                  "name": "%s",
                  "version": "1.0.0",
                  "description": "Generated runtime test mod",
                  "authors": ["Test Author"],
                  "license": "MIT",
                  "requiredLoaderVersion": ">=0.1.0",
                  "entrypoint": "%s"
                }
                """.formatted(modId, niceName, entrypoint);

        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            addFile(output, classesDir.resolve(packageName.replace('.', '/') + "/" + className + ".class"), packageName.replace('.', '/') + "/" + className + ".class");
            addText(output, "META-INF/fv2j3.mod.json", metadata);
        }
        return jarPath;
    }

    private static Path createFailingStartModJar(Path jarPath) throws Exception {
        return createFailingStartModJar(jarPath, "failing_mod", "com.example.fail.FailingStartMod");
    }

    private static Path createFailingStartModJar(Path jarPath, String modId, String entrypoint) throws Exception {
        String className = entrypoint.substring(entrypoint.lastIndexOf('.') + 1);
        String packageName = entrypoint.substring(0, entrypoint.lastIndexOf('.'));
        String sourcePath = packageName.replace('.', '/') + "/" + className + ".java";
        Path sourceDir = Files.createTempDirectory("fv2j3-failing-start-src-");
        Path classesDir = sourceDir.resolve("classes");
        Files.createDirectories(classesDir);

        Path sourceFile = sourceDir.resolve(sourcePath);
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(
                sourceFile,
                """
                package %s;

                import io.github.fv2j3dteam.api.Mod;
                import io.github.fv2j3dteam.api.ModContext;
                import io.github.fv2j3dteam.api.ModDescriptor;

                public final class %s implements Mod {
                    @Override
                    public ModDescriptor descriptor() {
                        return new ModDescriptor("%s", "1.0.0", "Failing Start Mod");
                    }

                    @Override
                    public void onStart(ModContext context) {
                        throw new IllegalStateException("boom");
                    }
                }
                """.formatted(packageName, className, modId),
                StandardCharsets.UTF_8
        );

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Java compiler must be available for runtime integration test");
        int result = compiler.run(
                null,
                null,
                null,
                "-cp",
                System.getProperty("java.class.path"),
                "-d",
                classesDir.toString(),
                sourceFile.toString()
        );
        if (result != 0) {
            throw new IllegalStateException("Failed to compile failing mod source");
        }

        String metadata = """
                {
                  "id": "%s",
                  "name": "Failing Start Mod",
                  "version": "1.0.0",
                  "description": "Generated failing runtime mod",
                  "authors": ["Test Author"],
                  "license": "MIT",
                  "requiredLoaderVersion": ">=0.1.0",
                  "entrypoint": "%s"
                }
                """.formatted(modId, entrypoint);

        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            addFile(output, classesDir.resolve(packageName.replace('.', '/') + "/" + className + ".class"), packageName.replace('.', '/') + "/" + className + ".class");
            addText(output, "META-INF/fv2j3.mod.json", metadata);
        }
        return jarPath;
    }

    private static Path createJarWithMetadata(Path jarPath, String entrypoint) throws Exception {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            addText(output, "META-INF/fv2j3.mod.json", """
                    {
                      "id": "broken_mod",
                      "name": "Broken Mod",
                      "version": "1.0.0",
                      "description": "Missing entrypoint test mod",
                      "authors": ["Test Author"],
                      "license": "MIT",
                      "requiredLoaderVersion": ">=0.1.0",
                      "entrypoint": "%s"
                    }
                    """.formatted(entrypoint));
        }
        return jarPath;
    }

    private static void addFile(JarOutputStream output, Path sourceFile, String entryName) throws IOException {
        try (InputStream input = Files.newInputStream(sourceFile)) {
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
