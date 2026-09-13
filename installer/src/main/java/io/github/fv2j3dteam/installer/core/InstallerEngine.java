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

    // ---- Constants (merged from InstallerConstants) ----
    private static final String MINECRAFT_VERSION = "1.12.2";
    private static final String FV2J3_VERSION_ID = "Fv2j3-" + MINECRAFT_VERSION;
    private static final String FV2J3_PROFILE_NAME = "Fv2j3 1.12.2";
    private static final String FV2J3_MAIN_CLASS = "io.github.fv2j3dteam.loader.runtime.Bootstrap";

    // ---- MinecraftDetector (merged) ----
    public static Path detectMinecraftDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path detected = switch (true) {
            case os.contains("win") -> detectWindows();
            case os.contains("mac") -> detectMac();
            default -> detectLinux();
        };
        return (detected != null && Files.isDirectory(detected)) ? detected.toAbsolutePath().normalize() : null;
    }

    private static Path detectWindows() {
        String appData = System.getenv("APPDATA");
        return (appData != null && !appData.isBlank()) ? Paths.get(appData, ".minecraft") : null;
    }

    private static Path detectMac() {
        Path p = Paths.get(System.getProperty("user.home"), "Library", "Application Support", "minecraft");
        return Files.isDirectory(p) ? p : null;
    }

    private static Path detectLinux() {
        Path p = Paths.get(System.getProperty("user.home"), ".minecraft");
        return Files.isDirectory(p) ? p : null;
    }

    public static MinecraftDirectory analyze(Path minecraftHome) {
        if (minecraftHome == null || !Files.isDirectory(minecraftHome)) return MinecraftDirectory.notFound();
        Path versionsDir = minecraftHome.resolve("versions");
        boolean hasVersionsDir = Files.isDirectory(versionsDir);
        List<Path> foundVersions = new ArrayList<>();
        if (hasVersionsDir) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionsDir)) {
                for (Path entry : stream) if (Files.isDirectory(entry)) foundVersions.add(entry);
            } catch (IOException ignored) {}
        }
        boolean hasVersion112 = foundVersions.stream().anyMatch(p -> MINECRAFT_VERSION.equals(p.getFileName().toString()));
        Path versionRoot = hasVersion112 ? versionsDir.resolve(MINECRAFT_VERSION) : null;
        boolean hasVersionJson = versionRoot != null && Files.isRegularFile(versionRoot.resolve(MINECRAFT_VERSION + ".json"));
        boolean hasVersionJar = versionRoot != null && Files.isRegularFile(versionRoot.resolve(MINECRAFT_VERSION + ".jar"));
        return new MinecraftDirectory(
            minecraftHome.toAbsolutePath().normalize(), hasVersionsDir, List.copyOf(foundVersions),
            MINECRAFT_VERSION, hasVersion112, hasVersionJson, hasVersionJar);
    }

    public static boolean isValidMinecraftInstallation(Path minecraftHome) {
        MinecraftDirectory dir = analyze(minecraftHome);
        return dir.hasVersionsDirectory() && dir.hasVersion112() && dir.hasVersionJson() && dir.hasVersionJar();
    }

    public record MinecraftDirectory(
        Path path, boolean hasVersionsDirectory, List<Path> availableVersions, String targetVersion,
        boolean hasVersion112, boolean hasVersionJson, boolean hasVersionJar) {
        public static MinecraftDirectory notFound() {
            return new MinecraftDirectory(null, false, List.of(), MINECRAFT_VERSION, false, false, false);
        }
        public boolean isValid() { return path != null && hasVersionsDirectory && hasVersion112 && hasVersionJson && hasVersionJar; }
        public String describe() {
            if (path == null) return "Minecraft directory not found";
            StringBuilder sb = new StringBuilder();
            sb.append("Minecraft directory: ").append(path).append("\n");
            sb.append("Versions directory: ").append(hasVersionsDirectory ? "present" : "missing").append("\n");
            sb.append("Available versions: ").append(availableVersions.size()).append("\n");
            sb.append("Minecraft ").append(targetVersion).append(": ");
            if (hasVersion112) {
                sb.append("found");
                if (hasVersionJson && hasVersionJar) sb.append(" (complete)");
                else sb.append(" (incomplete)");
            } else sb.append("not found");
            return sb.toString();
        }
    }

    // ---- InstallationPlan (merged) ----
    public static final class InstallationPlan {
        private final Path minecraftHome, fv2j3VersionDir, runtimeDir;
        private final boolean extractRuntime, installClient, integrateLauncher, createModsDir;

        private InstallationPlan(Builder b) {
            this.minecraftHome = Objects.requireNonNull(b.minecraftHome, "minecraftHome");
            this.fv2j3VersionDir = b.fv2j3VersionDir != null ? b.fv2j3VersionDir : minecraftHome.resolve("versions").resolve(FV2J3_VERSION_ID);
            this.runtimeDir = b.runtimeDir != null ? b.runtimeDir : getRuntimeDir();
            this.extractRuntime = b.extractRuntime;
            this.installClient = b.installClient;
            this.integrateLauncher = b.integrateLauncher;
            this.createModsDir = b.createModsDir;
        }

        public static Builder builder(Path minecraftHome) { return new Builder(minecraftHome); }

        public Path minecraftHome() { return minecraftHome; }
        public Path fv2j3VersionDir() { return fv2j3VersionDir; }
        public Path runtimeDir() { return runtimeDir; }
        public boolean extractRuntime() { return extractRuntime; }
        public boolean installClient() { return installClient; }
        public boolean integrateLauncher() { return integrateLauncher; }
        public boolean createModsDir() { return createModsDir; }
        public Path fv2j3VersionJson() { return fv2j3VersionDir.resolve(FV2J3_VERSION_ID + ".json"); }
        public Path modsDir() { return minecraftHome.resolve("mods"); }
        public Path launcherProfilesJson() { return minecraftHome.resolve("launcher_profiles.json"); }
        public Path runtimeLibsDir() { return runtimeDir.resolve("libs"); }

        public String summary() {
            return String.format("InstallationPlan[mc=%s, fv2j3VersionDir=%s, runtimeDir=%s, extractRuntime=%s, installClient=%s, integrateLauncher=%s, createModsDir=%s]",
                minecraftHome, fv2j3VersionDir, runtimeDir, extractRuntime, installClient, integrateLauncher, createModsDir);
        }

        public static final class Builder {
            private Path minecraftHome, fv2j3VersionDir, runtimeDir;
            private boolean extractRuntime = true, installClient = true, integrateLauncher = true, createModsDir = true;
            private Builder(Path minecraftHome) { this.minecraftHome = minecraftHome; }
            public Builder fv2j3VersionDir(Path v) { this.fv2j3VersionDir = v; return this; }
            public Builder runtimeDir(Path v) { this.runtimeDir = v; return this; }
            public Builder extractRuntime(boolean v) { this.extractRuntime = v; return this; }
            public Builder installClient(boolean v) { this.installClient = v; return this; }
            public Builder integrateLauncher(boolean v) { this.integrateLauncher = v; return this; }
            public Builder createModsDir(boolean v) { this.createModsDir = v; return this; }
            public InstallationPlan build() { return new InstallationPlan(this); }
        }
    }

    // ---- InstallationResult (merged) ----
    public static final class InstallationResult {
        private final boolean success;
        private final Path minecraftHome, fv2j3VersionDir, fv2j3VersionJson, runtimeDir, modsDir;
        private final boolean launcherIntegrated;
        private final List<String> steps, warnings;
        private final String failureReason;

        private InstallationResult(Builder b) {
            this.success = b.success;
            this.minecraftHome = b.minecraftHome;
            this.fv2j3VersionDir = b.fv2j3VersionDir;
            this.fv2j3VersionJson = b.fv2j3VersionJson;
            this.runtimeDir = b.runtimeDir;
            this.modsDir = b.modsDir;
            this.launcherIntegrated = b.launcherIntegrated;
            this.steps = Collections.unmodifiableList(new ArrayList<>(b.steps));
            this.warnings = Collections.unmodifiableList(new ArrayList<>(b.warnings));
            this.failureReason = b.failureReason;
        }

        public static Builder builder() { return new Builder(); }
        public static InstallationResult failure(String reason) { return new Builder().success(false).failureReason(reason).build(); }

        public boolean success() { return success; }
        public Path minecraftHome() { return minecraftHome; }
        public Path fv2j3VersionDir() { return fv2j3VersionDir; }
        public Path fv2j3VersionJson() { return fv2j3VersionJson; }
        public Path runtimeDir() { return runtimeDir; }
        public Path modsDir() { return modsDir; }
        public boolean launcherIntegrated() { return launcherIntegrated; }
        public List<String> steps() { return steps; }
        public List<String> warnings() { return warnings; }
        public String failureReason() { return failureReason; }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("Installation ").append(success ? "SUCCEEDED" : "FAILED").append("\n");
            sb.append("Minecraft directory: ").append(minecraftHome).append("\n");
            sb.append("Fv2j3 version dir: ").append(fv2j3VersionDir).append("\n");
            sb.append("Fv2j3 version JSON: ").append(fv2j3VersionJson).append("\n");
            sb.append("Runtime directory: ").append(runtimeDir).append("\n");
            sb.append("Mods directory: ").append(modsDir).append("\n");
            sb.append("Launcher integrated: ").append(launcherIntegrated ? "yes" : "no").append("\n");
            sb.append("Steps:\n");
            for (String s : steps) sb.append("  - ").append(s).append("\n");
            if (!warnings.isEmpty()) {
                sb.append("Warnings:\n");
                for (String w : warnings) sb.append("  ! ").append(w).append("\n");
            }
            if (failureReason != null) sb.append("Failure: ").append(failureReason).append("\n");
            return sb.toString();
        }

        public static final class Builder {
            private boolean success;
            private Path minecraftHome, fv2j3VersionDir, fv2j3VersionJson, runtimeDir, modsDir;
            private boolean launcherIntegrated;
            private final List<String> steps = new ArrayList<>(), warnings = new ArrayList<>();
            private String failureReason;
            public Builder success(boolean v) { this.success = v; return this; }
            public Builder minecraftHome(Path v) { this.minecraftHome = v; return this; }
            public Builder fv2j3VersionDir(Path v) { this.fv2j3VersionDir = v; return this; }
            public Builder fv2j3VersionJson(Path v) { this.fv2j3VersionJson = v; return this; }
            public Builder runtimeDir(Path v) { this.runtimeDir = v; return this; }
            public Builder modsDir(Path v) { this.modsDir = v; return this; }
            public Builder launcherIntegrated(boolean v) { this.launcherIntegrated = v; return this; }
            public Builder addStep(String s) { if (s != null) steps.add(s); return this; }
            public Builder addWarning(String s) { if (s != null) warnings.add(s); return this; }
            public Builder failureReason(String s) { this.failureReason = s; return this; }
            public InstallationResult build() { return new InstallationResult(this); }
        }
    }

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
            MinecraftDirectory mcDir = analyze(plan.minecraftHome());
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