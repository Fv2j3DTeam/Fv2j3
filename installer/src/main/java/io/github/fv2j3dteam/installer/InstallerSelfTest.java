package io.github.fv2j3dteam.installer;

import io.github.fv2j3dteam.installer.core.InstallerEngine;
import io.github.fv2j3dteam.installer.core.MinecraftDetector;
import io.github.fv2j3dteam.installer.util.SelfContainedExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

public final class InstallerSelfTest {
    private static final Logger LOG = LoggerFactory.getLogger(InstallerSelfTest.class);
    private final List<TestResult> results = new ArrayList<>();

    public SelfTestReport run() {
        run("Installer JAR exists", () -> testJarExists());
        run("Manifest Main-Class", () -> testManifestMainClass());
        run("Installer is self-contained", () -> testSelfContained());
        run("Installer classes load", () -> testClassesLoad());
        run("Minecraft detector", () -> testMinecraftDetector());
        run("Minecraft 1.12.2 detector", () -> testMinecraft112Detector());
        run("Version JSON generator", () -> testVersionJsonGenerator());
        run("Launcher profile integration", () -> testLauncherIntegration());
        run("Bootstrap installer", () -> testBootstrapInstaller());
        run("Runtime extraction", () -> testRuntimeExtraction());
        return new SelfTestReport(results);
    }

    private void run(String name, TestTask test) {
        try {
            test.execute();
            results.add(new TestResult(name, true, null));
        } catch (Throwable t) {
            results.add(new TestResult(name, false, t.getClass().getSimpleName() + ": " + t.getMessage()));
            LOG.warn("Test '{}' failed: {}", name, t.getMessage());
        }
    }

    @FunctionalInterface
    private interface TestTask {
        void execute() throws Exception;
    }

    private void testJarExists() throws Exception {
        CodeSource source = InstallerSelfTest.class.getProtectionDomain().getCodeSource();
        if (source == null) throw new AssertionError("No code source found for InstallerSelfTest");
        Path jarPath = Paths.get(source.getLocation().toURI());
        if (!Files.isRegularFile(jarPath))
            throw new AssertionError("Installer JAR not found at: " + jarPath);
        long size = Files.size(jarPath);
        if (size < 1024)
            throw new AssertionError("Installer JAR is too small: " + size + " bytes");
    }

    private void testManifestMainClass() throws Exception {
        CodeSource source = InstallerSelfTest.class.getProtectionDomain().getCodeSource();
        Path jarPath = Paths.get(source.getLocation().toURI());
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            var entry = zip.getEntry("META-INF/MANIFEST.MF");
            if (entry == null) throw new AssertionError("META-INF/MANIFEST.MF not found in installer JAR");
            try (InputStream is = zip.getInputStream(entry)) {
                Manifest manifest = new Manifest(is);
                String mainClass = manifest.getMainAttributes().getValue("Main-Class");
                if (mainClass == null || mainClass.isBlank())
                    throw new AssertionError("Main-Class not set in manifest");
                if (!"io.github.fv2j3dteam.installer.InstallerMain".equals(mainClass))
                    throw new AssertionError("Wrong Main-Class: " + mainClass);
            }
        }
    }

    private void testSelfContained() throws Exception {
        CodeSource source = InstallerSelfTest.class.getProtectionDomain().getCodeSource();
        Path jarPath = Paths.get(source.getLocation().toURI());
        int embeddedLibs = 0;
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("libs/") && !entry.isDirectory()) embeddedLibs++;
            }
        }
        if (embeddedLibs == 0)
            throw new AssertionError("No embedded Fv2j3 runtime libraries found. Installer JAR is not self-contained.");
    }

    private void testClassesLoad() throws Exception {
        String[] classes = {
            "io.github.fv2j3dteam.installer.InstallerMain",
            "io.github.fv2j3dteam.installer.core.InstallerEngine",
            "io.github.fv2j3dteam.installer.util.SelfContainedExtractor",
            "io.github.fv2j3dteam.installer.ui.InstallerFrame"
        };
        ClassLoader cl = InstallerSelfTest.class.getClassLoader();
        for (String cls : classes) Class.forName(cls, true, cl);
    }

    private void testMinecraftDetector() throws Exception {
        Path detected = InstallerEngine.detectMinecraftDirectory();
        if (detected != null) {
            MinecraftDetector.MinecraftDirectory dir = InstallerEngine.analyze(detected);
            if (dir.path() == null)
                throw new AssertionError("MinecraftDetector.analyze returned null path for detected directory");
        }
        MinecraftDetector.MinecraftDirectory notFound = InstallerEngine.analyze(Paths.get("/nonexistent/path/12345"));
        if (notFound.path() != null)
            throw new AssertionError("MinecraftDetector should return null path for nonexistent directory");
    }

    private void testMinecraft112Detector() throws Exception {
        MinecraftDetector.MinecraftDirectory dir = InstallerEngine.analyze(Paths.get("/nonexistent"));
        if (dir.hasVersion112() || dir.hasVersionJson() || dir.hasVersionJar())
            throw new AssertionError("Nonexistent path should not have version files");
    }

    private void testVersionJsonGenerator() throws Exception {
        Path tempMc = Files.createTempDirectory("fv2j3-selftest");
        try {
            Path versionsDir = tempMc.resolve("versions");
            Files.createDirectories(versionsDir);
            Path version112 = versionsDir.resolve("1.12.2");
            Files.createDirectories(version112);
            Files.writeString(version112.resolve("1.12.2.json"),
                "{\"id\":\"1.12.2\",\"type\":\"release\",\"mainClass\":\"net.minecraft.client.main.Main\",\"libraries\":[],\"arguments\":{\"game\":[],\"jvm\":[]}}");
            Files.writeString(version112.resolve("1.12.2.jar"), "");
            Path tempRuntime = tempMc.resolve("runtime");
            Files.createDirectories(tempRuntime.resolve("libs"));
            Files.writeString(tempRuntime.resolve("libs/loader-api.jar"), "fake");
            Files.writeString(tempRuntime.resolve("libs/loader-core.jar"), "fake");
            Files.writeString(tempRuntime.resolve("libs/loader-runtime.jar"), "fake");
            Files.writeString(tempRuntime.resolve("libs/minecraft-compat.jar"), "fake");
            InstallerEngine.VersionJson vjson = InstallerEngine.generateVersionJson(tempMc, tempRuntime);
            if (!Files.isRegularFile(vjson.versionJson()))
                throw new AssertionError("Version JSON not written: " + vjson.versionJson());
            String content = Files.readString(vjson.versionJson());
            if (!content.contains("\"inheritsFrom\"")) throw new AssertionError("Version JSON missing inheritsFrom");
            if (!content.contains("\"mainClass\"")) throw new AssertionError("Version JSON missing mainClass");
            if (!content.contains("Fv2j3-1.12.2")) throw new AssertionError("Version JSON missing Fv2j3-1.12.2");
        } finally {
            deleteRecursively(tempMc);
        }
    }

    private void testLauncherIntegration() throws Exception {
        Path tempMc = Files.createTempDirectory("fv2j3-selftest");
        try {
            InstallerEngine.ProfileResult result = InstallerEngine.integrateProfile(tempMc);
            if (!result.success()) throw new AssertionError("Profile integration failed: " + result.error());
            if (!Files.isRegularFile(result.profilesFile())) throw new AssertionError("Profiles file not created: " + result.profilesFile());
            InstallerEngine.ProfileRemovalResult removal = InstallerEngine.removeProfile(tempMc);
            if (!removal.success()) throw new AssertionError("Profile removal failed: " + removal.message());
        } finally {
            deleteRecursively(tempMc);
        }
    }

    private void testBootstrapInstaller() throws Exception {
        Path tempRuntime = Files.createTempDirectory("fv2j3-selftest-runtime");
        try {
            List<Path> installed = InstallerEngine.installBootstrap(tempRuntime);
            if (installed.isEmpty()) throw new AssertionError("No bootstrap libraries installed");
        } finally {
            deleteRecursively(tempRuntime);
        }
    }

    private void testRuntimeExtraction() throws Exception {
        Path tempRuntime = Files.createTempDirectory("fv2j3-selftest-runtime");
        try {
            SelfContainedExtractor.extractRuntime(tempRuntime);
        } finally {
            deleteRecursively(tempRuntime);
        }
    }

    private void deleteRecursively(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.walk(path)) {
                    stream.sorted(Comparator.reverseOrder())
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                }
            } else {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {}
    }

    public record TestResult(String name, boolean passed, String error) {}

    public record SelfTestReport(List<TestResult> results) {
        public int passed() { return (int) results.stream().filter(TestResult::passed).count(); }
        public int total() { return results.size(); }
        public boolean allPassed() { return results.stream().allMatch(TestResult::passed); }

        public String render() {
            StringBuilder sb = new StringBuilder();
            sb.append("========================================\n");
            sb.append("Fv2j3 Installer Self-Test\n");
            sb.append("========================================\n\n");
            for (TestResult r : results) {
                sb.append("[").append(r.passed() ? "PASS" : "FAIL").append("] ")
                    .append(r.name());
                if (r.error() != null) sb.append("\n       ").append(r.error());
                sb.append("\n");
            }
            sb.append("\n").append(passed()).append("/").append(total()).append(" tests passed\n");
            sb.append("\n========================================\n");
            sb.append("RESULT: ").append(allPassed() ? "ALL PASS" : "SOME FAILED").append("\n");
            sb.append("========================================\n");
            return sb.toString();
        }
    }
}