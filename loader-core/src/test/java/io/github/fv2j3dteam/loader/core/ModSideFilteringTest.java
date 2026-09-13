package io.github.fv2j3dteam.loader.core;

import io.github.fv2j3dteam.loader.core.Fv2j3Loader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §25 crash/error handling: a mod that declares a side it cannot run on
 * must be excluded with a clear, actionable, non-silent message — never
 * loaded silently and never allowed to crash the game.
 */
class ModSideFilteringTest {

    @TempDir
    Path tempDir;

    @Test
    void clientOnlyModIsExcludedFromDedicatedServerWithActionableMessage() throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Files.createDirectories(modsDir);
        createSideModJar(modsDir, "fancy_client_fx", "com.example.ClientFxMod", "client");

        CapturingLoaderLogger logger = new CapturingLoaderLogger();
        Fv2j3Loader loader = new Fv2j3Loader(
                LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT", LoaderSide.DEDICATED_SERVER), logger);
        loader.initialize(modsDir);

        assertEquals(0, loader.context().modRegistry().all().size(),
                "client-only mod must not be registered on the dedicated server");
        assertTrue(logger.warnings.stream().anyMatch(w -> w.contains("fancy_client_fx")
                        && w.contains("client") && w.contains("mods directory")),
                "the exclusion must be logged with the mod id, its side and the remedy");
    }

    @Test
    void serverOnlyModIsExcludedFromClientWithActionableMessage() throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Files.createDirectories(modsDir);
        createSideModJar(modsDir, "server_tick_optimizer", "com.example.ServerOptMod", "server");

        CapturingLoaderLogger logger = new CapturingLoaderLogger();
        Fv2j3Loader loader = new Fv2j3Loader(
                LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT", LoaderSide.CLIENT), logger);
        loader.initialize(modsDir);

        assertEquals(0, loader.context().modRegistry().all().size(),
                "server-only mod must not be registered on the client");
        assertTrue(logger.warnings.stream().anyMatch(w -> w.contains("server_tick_optimizer")
                        && w.contains("server")),
                "the exclusion must be logged");
    }

    @Test
    void sideCompatibleModsStillLoad() throws Exception {
        Path modsDir = tempDir.resolve("mods");
        Files.createDirectories(modsDir);
        createSideModJar(modsDir, "both_sides_mod", "com.example.BothSidesMod", "both");
        createSideModJar(modsDir, "server_tool_mod", "com.example.ServerToolMod", "server");

        Fv2j3Loader loader = new Fv2j3Loader(
                LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT", LoaderSide.DEDICATED_SERVER));
        loader.initialize(modsDir);
        loader.start();

        assertEquals(2, loader.context().modRegistry().all().size(),
                "both-side and server-side mods must load on the dedicated server");
        loader.stop();
    }

    private static final class CapturingLoaderLogger implements LoaderLogger {
        final java.util.List<String> warnings = new java.util.ArrayList<>();

        @Override public void debug(String message) { }
        @Override public void info(String message) { }
        @Override public void warn(String message) { warnings.add(message); }
        @Override public void error(String message) { warnings.add(message); }
        @Override public void error(String message, Throwable throwable) { warnings.add(message); }
    }

    /** Builds a mod jar whose metadata declares the given side. */
    private void createSideModJar(Path dir, String modId, String entrypoint, String side) throws IOException {
        String className = entrypoint.substring(entrypoint.lastIndexOf('.') + 1);
        String packageName = entrypoint.substring(0, entrypoint.lastIndexOf('.'));

        String code = """
                package %s;

                import io.github.fv2j3dteam.api.Mod;
                import io.github.fv2j3dteam.api.ModContext;
                import io.github.fv2j3dteam.api.ModDescriptor;

                public final class %s implements Mod {
                    @Override
                    public ModDescriptor descriptor() {
                        return new ModDescriptor("%s", "1.0.0", "%s");
                    }
                    @Override public void onLoad(ModContext context) { }
                    @Override public void onInitialize(ModContext context) { }
                    @Override public void onStart(ModContext context) { }
                    @Override public void onStop(ModContext context) { }
                }
                """.formatted(packageName, className, modId, modId.replace('_', ' '));

        Path sourceDir = Files.createTempDirectory("fv2j3-side-src-");
        Path classesDir = sourceDir.resolve("classes");
        Files.createDirectories(classesDir);
        Path sourceFile = classesDir.resolve(packageName.replace('.', '/') + "/" + className + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, code, StandardCharsets.UTF_8);

        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null, "-cp",
                System.getProperty("java.class.path"), "-d", classesDir.toString(), sourceFile.toString());
        if (result != 0) {
            throw new IllegalStateException("Failed to compile side test mod: " + modId);
        }

        String metadata = """
                {
                  "id": "%s",
                  "name": "%s",
                  "version": "1.0.0",
                  "side": "%s",
                  "entrypoint": "%s"
                }
                """.formatted(modId, modId.replace('_', ' '), side, entrypoint);

        Path jarPath = dir.resolve(modId + ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            output.putNextEntry(new JarEntry(packageName.replace('.', '/') + "/" + className + ".class"));
            output.write(Files.readAllBytes(classesDir.resolve(packageName.replace('.', '/') + "/" + className + ".class")));
            output.closeEntry();
            output.putNextEntry(new JarEntry("META-INF/fv2j3.mod.json"));
            output.write(metadata.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }
}
