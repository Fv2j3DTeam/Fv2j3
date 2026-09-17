package io.github.fv2j3dteam.installer.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.fv2j3dteam.installer.util.SelfContainedExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public final class InstallerEngine {
    private static final Logger LOG = LoggerFactory.getLogger(InstallerEngine.class);

    // ---- InstallProgress (merged) ----
    public interface InstallProgress {
        void onStep(String name, int percent, String message);
        static InstallProgress noop() { return (name, percent, message) -> {}; }
        static InstallProgress console() { return (name, percent, message) -> System.out.printf("[%3d%%] %-30s %s%n", percent, name, message); }
    }

    // ---- Constants - use InstallerConstants ---
    private static final String MINECRAFT_VERSION = InstallerConstants.MINECRAFT_VERSION;
    private static final String FV2J3_VERSION_ID = InstallerConstants.FV2J3_VERSION_ID;
    private static final String FV2J3_PROFILE_NAME = InstallerConstants.FV2J3_PROFILE_NAME;
    private static final String FV2J3_MAIN_CLASS = InstallerConstants.FV2J3_MAIN_CLASS;

    // ---- MinecraftDetector - use MinecraftDetector class ---
    public static Path detectMinecraftDirectory() {
        return MinecraftDetector.detectMinecraftDirectory();
    }

    public static MinecraftDetector.MinecraftDirectory analyze(Path minecraftHome) {
        return MinecraftDetector.analyze(minecraftHome);
    }

    public static boolean isValidMinecraftInstallation(Path minecraftHome) {
        return MinecraftDetector.isValidMinecraftInstallation(minecraftHome);
    }

    // ---- InstallationPlan - use InstallationPlan class ---
    // ---- InstallationResult - use InstallationResult class ---

    // ---- BootstrapInstaller (merged) ----
    private static final String[] RUNTIME_JARS = {
        "loader-api-0.1.0-SNAPSHOT.jar", "loader-core-0.1.0-SNAPSHOT.jar",
        "loader-runtime-0.1.0-SNAPSHOT.jar", "minecraft-compat-0.1.0-SNAPSHOT.jar",
        "jackson-databind-2.17.2.jar", "jackson-core-2.17.2.jar",
        "jackson-annotations-2.17.2.jar", "asm-9.8.jar"
    };

    public static List<Path> installBootstrap(Path runtimeDir) throws IOException {
        List<Path> installed = new ArrayList<>();
        Path libsDir = runtimeDir.resolve("libs");
        Files.createDirectories(libsDir);
        for (String jarName : RUNTIME_JARS) {
            Path target = libsDir.resolve(jarName);
            if (Files.exists(target)) { installed.add(target); continue; }
            Path source = findEmbeddedJar(jarName);
            if (source != null && Files.isRegularFile(source)) {
                Files.copy(source, target);
                installed.add(target);
            }
        }
        return installed;
    }

    static Path findEmbeddedJar(String jarName) {
        var url = InstallerEngine.class.getResource("/libs/" + jarName);
        if (url == null) return null;
        try { return Paths.get(url.toURI()); } catch (Exception ex) { return null; }
    }

    public static int countInstalledLibraries(Path runtimeDir) {
        Path libsDir = runtimeDir.resolve("libs");
        if (!Files.isDirectory(libsDir)) return 0;
        try (var stream = Files.list(libsDir)) { return (int) stream.filter(Files::isRegularFile).count(); }
        catch (IOException ex) { return 0; }
    }

    // ---- VersionJsonGenerator (merged) ----
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static VersionJson generateVersionJson(Path minecraftHome, Path runtimeDir) throws IOException {
        Objects.requireNonNull(minecraftHome, "minecraftHome");
        Objects.requireNonNull(runtimeDir, "runtimeDir");
        Path baseVersionDir = minecraftHome.resolve("versions").resolve(MINECRAFT_VERSION);
        Path baseVersionJson = baseVersionDir.resolve(MINECRAFT_VERSION + ".json");
        if (!Files.isRegularFile(baseVersionJson))
            throw new IOException("Base Minecraft 1.12.2 version JSON not found: " + baseVersionJson);
        JsonNode base = MAPPER.readTree(Files.newInputStream(baseVersionJson));
        ObjectNode root = MAPPER.createObjectNode();
        root.put("id", FV2J3_VERSION_ID);
        root.put("inheritsFrom", MINECRAFT_VERSION);
        root.put("type", "release");
        root.put("mainClass", FV2J3_MAIN_CLASS);
        long buildTime = System.currentTimeMillis();
        root.put("buildTime", buildTime);
        root.put("releaseTime", buildTime);
        root.put("minimumLauncherVersion", 18);
        root.put("javaVersion", base.path("javaVersion").deepCopy());
        ArrayNode libraries = MAPPER.createArrayNode();
        copyBaseLibraries(libraries, base);
        addFv2j3Libraries(libraries, runtimeDir);
        root.set("libraries", libraries);
        ObjectNode arguments = MAPPER.createObjectNode();
        ArrayNode game = MAPPER.createArrayNode(), jvm = MAPPER.createArrayNode();
        copyBaseGameArguments(game, base);
        copyBaseJvmArguments(jvm, base);
        arguments.set("game", game);
        arguments.set("jvm", jvm);
        root.set("arguments", arguments);
        Path targetDir = minecraftHome.resolve("versions").resolve(FV2J3_VERSION_ID);
        Files.createDirectories(targetDir);
        Path targetJson = targetDir.resolve(FV2J3_VERSION_ID + ".json");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(targetJson.toFile(), root);
        return new VersionJson(FV2J3_VERSION_ID, targetDir, targetJson, root);
    }

    private static void copyBaseLibraries(ArrayNode libraries, JsonNode base) {
        JsonNode baseLibs = base.path("libraries");
        if (baseLibs != null && baseLibs.isArray())
            for (JsonNode lib : baseLibs) libraries.add(lib.deepCopy());
    }

    private static void copyBaseGameArguments(ArrayNode game, JsonNode base) {
        JsonNode baseGame = base.path("arguments").path("game");
        if (baseGame != null && baseGame.isArray())
            for (JsonNode arg : baseGame) game.add(arg.deepCopy());
        else {
            JsonNode minecraftArgs = base.path("minecraftArguments");
            if (minecraftArgs != null && minecraftArgs.isTextual())
                for (String s : minecraftArgs.asText().split(" ")) game.add(s);
        }
    }

    private static void copyBaseJvmArguments(ArrayNode jvm, JsonNode base) {
        JsonNode baseJvm = base.path("arguments").path("jvm");
        if (baseJvm != null && baseJvm.isArray())
            for (JsonNode arg : baseJvm) jvm.add(arg.deepCopy());
    }

    private static void addFv2j3Libraries(ArrayNode libraries, Path runtimeDir) {
        Path libsDir = runtimeDir.resolve("libs");
        if (!Files.isDirectory(libsDir)) return;
        try (var stream = Files.list(libsDir)) {
            stream.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar"))
                .sorted()
                .forEach(jar -> {
                    ObjectNode lib = MAPPER.createObjectNode();
                    ObjectNode downloads = MAPPER.createObjectNode();
                    ObjectNode artifact = MAPPER.createObjectNode();
                    String fileName = jar.getFileName().toString();
                    artifact.put("path", "fv2j3/libs/" + fileName);
                    artifact.put("url", jar.toUri().toString());
                    long size;
                    try { size = Files.size(jar); } catch (IOException e) { size = 0L; }
                    artifact.put("size", size);
                    downloads.set("artifact", artifact);
                    lib.set("downloads", downloads);
                    lib.put("name", "io.github.fv2j3dteam:" + stripExtension(fileName) + ":runtime");
                    libraries.add(lib);
                });
        } catch (IOException ex) { throw new IllegalStateException("Failed to enumerate runtime libraries: " + libsDir, ex); }
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    public record VersionJson(
        @JsonProperty("id") String id,
        @JsonProperty("directory") Path directory,
        @JsonProperty("versionJson") Path versionJson,
        @JsonProperty("root") ObjectNode root) {}

    // ---- LauncherProfileIntegrator (merged) ----
    private static final ObjectMapper PROFILE_MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static ProfileResult integrateProfile(Path minecraftHome) throws IOException {
        Objects.requireNonNull(minecraftHome, "minecraftHome");
        Path profilesFile = minecraftHome.resolve("launcher_profiles.json");
        JsonNode root;
        if (Files.isRegularFile(profilesFile)) {
            try { root = PROFILE_MAPPER.readTree(Files.newInputStream(profilesFile)); }
            catch (Exception ex) { root = PROFILE_MAPPER.createObjectNode(); }
        } else root = PROFILE_MAPPER.createObjectNode();
        if (!root.has("profiles")) ((ObjectNode) root).putObject("profiles");
        ObjectNode profiles = (ObjectNode) root.path("profiles");
        ObjectNode fv2j3Profile = PROFILE_MAPPER.createObjectNode();
        fv2j3Profile.put("name", FV2J3_PROFILE_NAME);
        fv2j3Profile.put("type", "latest-release");
        fv2j3Profile.put("lastVersionId", FV2J3_VERSION_ID);
        String now = Instant.now().toString();
        fv2j3Profile.put("created", now);
        fv2j3Profile.put("lastUsed", now);
        fv2j3Profile.put("icon", "Furnace");
        fv2j3Profile.put("javaArgs", "-Xmx2G");
        fv2j3Profile.put("javaDir", findSystemJava());
        ObjectNode fv2j3Settings = PROFILE_MAPPER.createObjectNode();
        fv2j3Settings.put("quickPlayOpen", false);
        fv2j3Settings.put("snooper", false);
        fv2j3Profile.set("settings", fv2j3Settings);
        profiles.set(FV2J3_PROFILE_NAME, fv2j3Profile);
        if (!root.has("settings")) ((ObjectNode) root).putObject("settings");
        JsonNode settings = root.path("settings");
        if (settings.isMissingNode() || !settings.has(FV2J3_PROFILE_NAME)) ((ObjectNode) settings).putObject(FV2J3_PROFILE_NAME);
        if (!root.has("profileCache")) ((ObjectNode) root).putObject("profileCache");
        JsonNode profileCache = root.path("profileCache");
        if (profileCache.isMissingNode() || !profileCache.has(FV2J3_PROFILE_NAME)) {
            ObjectNode cachedProfile = PROFILE_MAPPER.createObjectNode();
            cachedProfile.put("name", FV2J3_PROFILE_NAME);
            cachedProfile.put("version", FV2J3_VERSION_ID);
            cachedProfile.put("icon", "Furnace");
            cachedProfile.put("iconUrl", (String) null);
            cachedProfile.put("type", "latest-release");
            ((ObjectNode) profileCache).set(FV2J3_PROFILE_NAME, cachedProfile);
        }
        List<String> profileOrder = new ArrayList<>();
        JsonNode orderNode = root.path("profileOrder");
        if (orderNode.isArray()) for (JsonNode n : orderNode) if (n.isTextual()) profileOrder.add(n.asText());
        if (!profileOrder.contains(FV2J3_PROFILE_NAME)) {
            ArrayNode newOrder = PROFILE_MAPPER.createArrayNode();
            for (String p : profileOrder) newOrder.add(p);
            newOrder.add(FV2J3_PROFILE_NAME);
            ((ObjectNode) root).set("profileOrder", newOrder);
        }
        Files.createDirectories(profilesFile.getParent());
        backupProfile(profilesFile);
        PROFILE_MAPPER.writeValue(profilesFile.toFile(), root);
        return new ProfileResult(FV2J3_PROFILE_NAME, profilesFile, true, null);
    }

    private static String findSystemJava() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isBlank()) {
            Path javaBin = Path.of(javaHome, "bin", "java");
            if (Files.isRegularFile(javaBin) || Files.isRegularFile(Path.of(javaHome, "bin", "java.exe"))) return javaHome;
        }
        String javaCmd = findJavaExecutable();
        if (javaCmd != null) {
            Path javaPath = Path.of(javaCmd).toAbsolutePath().normalize();
            Path parent = javaPath.getParent();
            if (parent != null) {
                Path grandParent = parent.getParent();
                if (grandParent != null && Files.isDirectory(grandParent)) return grandParent.toString();
            }
            return parent != null ? parent.toString() : javaCmd;
        }
        return javaHome != null ? javaHome : "";
    }

    private static String findJavaExecutable() {
        String path = System.getenv("PATH");
        if (path == null) return null;
        String[] dirs = path.split(System.getProperty("path.separator", ":"));
        String javaExe = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        for (String dir : dirs) {
            Path candidate = Path.of(dir, javaExe);
            if (Files.isRegularFile(candidate)) return candidate.toString();
        }
        return null;
    }

    private static void backupProfile(Path profilesFile) throws IOException {
        if (!Files.isRegularFile(profilesFile)) return;
        Path backup = profilesFile.resolveSibling(profilesFile.getFileName() + ".fv2j3.bak");
        Files.copy(profilesFile, backup);
    }

    public static boolean profileExists(Path minecraftHome) {
        Path profilesFile = minecraftHome.resolve("launcher_profiles.json");
        if (!Files.isRegularFile(profilesFile)) return false;
        try {
            JsonNode root = PROFILE_MAPPER.readTree(Files.newInputStream(profilesFile));
            JsonNode profiles = root.path("profiles");
            if (!profiles.has(FV2J3_PROFILE_NAME)) return false;
            JsonNode profile = profiles.path(FV2J3_PROFILE_NAME);
            String lastVersion = profile.path("lastVersionId").asText(null);
            return FV2J3_VERSION_ID.equals(lastVersion);
        } catch (Exception ex) { return false; }
    }

    public static ProfileRemovalResult removeProfile(Path minecraftHome) throws IOException {
        Path profilesFile = minecraftHome.resolve("launcher_profiles.json");
        if (!Files.isRegularFile(profilesFile)) return new ProfileRemovalResult(true, "No profiles file found", null);
        JsonNode root = PROFILE_MAPPER.readTree(Files.newInputStream(profilesFile));
        if (!root.has("profiles")) return new ProfileRemovalResult(true, "No profiles found", null);
        ObjectNode profiles = (ObjectNode) root.path("profiles");
        if (!profiles.has(FV2J3_PROFILE_NAME)) return new ProfileRemovalResult(true, "Fv2j3 profile not found", null);
        profiles.remove(FV2J3_PROFILE_NAME);
        if (root.has("profileCache")) ((ObjectNode) root.path("profileCache")).remove(FV2J3_PROFILE_NAME);
        if (root.has("profileOrder")) {
            ArrayNode order = (ArrayNode) root.path("profileOrder");
            for (var it = order.iterator(); it.hasNext(); ) if (FV2J3_PROFILE_NAME.equals(it.next().asText())) { it.remove(); break; }
        }
        backupProfile(profilesFile);
        PROFILE_MAPPER.writeValue(profilesFile.toFile(), root);
        return new ProfileRemovalResult(true, "Profile removed", profilesFile);
    }

    public record ProfileResult(String profileName, Path profilesFile, boolean success, String error) {}
    public record ProfileRemovalResult(boolean success, String message, Path modifiedFile) {}

    // ---- InstallerEngine core methods ----
    public InstallationPlan buildPlan(Path minecraftHome) {
        return InstallationPlan.builder(minecraftHome)
            .extractRuntime(true).installClient(true).integrateLauncher(true).createModsDir(true).build();
    }

    public InstallationResult execute(InstallationPlan plan) {
        InstallationResult.Builder result = InstallationResult.builder()
            .minecraftHome(plan.minecraftHome()).runtimeDir(plan.runtimeDir());
        try {
            MinecraftDetector.MinecraftDirectory mcDir = analyze(plan.minecraftHome());
            if (!mcDir.isValid()) {
                return result.success(false)
                    .failureReason("Minecraft 1.12.2 not found at " + plan.minecraftHome()
                        + ". Please launch Minecraft 1.12.2 once using the official Minecraft Launcher.")
                    .build();
            }
            result.addStep("Minecraft 1.12.2 detected: " + mcDir.path());

            if (plan.extractRuntime()) {
                SelfContainedExtractor.extractRuntime(plan.runtimeDir());
                result.addStep("Runtime extracted to " + plan.runtimeDir());
            }
            List<Path> libs = installBootstrap(plan.runtimeDir());
            result.addStep("Bootstrap libraries installed: " + libs.size());

            if (plan.createModsDir()) {
                Files.createDirectories(plan.modsDir());
                result.addStep("Mods directory ensured: " + plan.modsDir());
            }
            result.modsDir(plan.modsDir());

            if (plan.installClient()) {
                VersionJson vjson = generateVersionJson(plan.minecraftHome(), plan.runtimeDir());
                result.fv2j3VersionDir(vjson.directory());
                result.fv2j3VersionJson(vjson.versionJson());
                result.addStep("Fv2j3 version JSON written: " + vjson.versionJson());
            }

            if (plan.integrateLauncher()) {
                ProfileResult profile = integrateProfile(plan.minecraftHome());
                if (profile.success()) {
                    result.launcherIntegrated(true);
                    result.addStep("Launcher profile integrated: " + profile.profileName());
                } else {
                    result.addWarning("Launcher integration failed: " + profile.error());
                }
            }
            return result.success(true).build();
        } catch (IOException ex) {
            LOG.error("Installation failed", ex);
            return result.success(false).failureReason(ex.getClass().getSimpleName() + ": " + ex.getMessage()).build();
        } catch (RuntimeException ex) {
            LOG.error("Installation failed", ex);
            return result.success(false).failureReason(ex.getClass().getSimpleName() + ": " + ex.getMessage()).build();
        }
    }

    public InstallationResult executeHeadless(Path minecraftHome) {
        return execute(buildPlan(minecraftHome));
    }
}