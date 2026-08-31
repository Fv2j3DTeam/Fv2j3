package com.fv2j3.installer.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class LauncherProfileIntegrator {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private LauncherProfileIntegrator() {
    }

    public static ProfileResult integrate(Path minecraftHome) throws IOException {
        Objects.requireNonNull(minecraftHome, "minecraftHome");
        Path profilesFile = minecraftHome.resolve("launcher_profiles.json");
        JsonNode root;
        boolean needsCreate = false;
        if (Files.isRegularFile(profilesFile)) {
            try {
                root = MAPPER.readTree(Files.newInputStream(profilesFile));
            } catch (Exception ex) {
                root = MAPPER.createObjectNode();
                needsCreate = true;
            }
        } else {
            root = MAPPER.createObjectNode();
            needsCreate = true;
        }
        if (!root.has("profiles")) {
            ((ObjectNode) root).putObject("profiles");
        }
        ObjectNode profiles = (ObjectNode) root.path("profiles");
        String profileName = InstallerConstants.FV2J3_PROFILE_NAME;
        ObjectNode fv2j3Profile = MAPPER.createObjectNode();
        fv2j3Profile.put("name", profileName);
        fv2j3Profile.put("type", "latest-release");
        fv2j3Profile.put("lastVersionId", InstallerConstants.FV2J3_VERSION_ID);
        fv2j3Profile.put("created", Instant.now().toString());
        fv2j3Profile.put("lastUsed", Instant.now().toString());
        fv2j3Profile.put("icon", "Furnace");
        fv2j3Profile.put("javaArgs", "-Xmx2G");
        fv2j3Profile.put("javaDir", findSystemJava());
        ObjectNode fv2j3Settings = MAPPER.createObjectNode();
        fv2j3Settings.put("quickPlayOpen", false);
        fv2j3Settings.put("snooper", false);
        fv2j3Profile.set("settings", fv2j3Settings);
        profiles.set(profileName, fv2j3Profile);
        JsonNode profilesNode = root.path("profiles");
        if (!root.has("settings")) {
            ((ObjectNode) root).putObject("settings");
        }
        JsonNode settings = root.path("settings");
        if (settings.isMissingNode() || !settings.has(profileName)) {
            ((ObjectNode) settings).putObject(profileName);
        }
        if (!root.has("profileCache")) {
            ((ObjectNode) root).putObject("profileCache");
        }
        JsonNode profileCache = root.path("profileCache");
        if (profileCache.isMissingNode() || !profileCache.has(profileName)) {
            ObjectNode cachedProfile = MAPPER.createObjectNode();
            cachedProfile.put("name", profileName);
            cachedProfile.put("version", InstallerConstants.FV2J3_VERSION_ID);
            cachedProfile.put("icon", "Furnace");
            cachedProfile.put("iconUrl", (String) null);
            cachedProfile.put("type", "latest-release");
            ((ObjectNode) profileCache).set(profileName, cachedProfile);
        }
        List<String> profileOrder = new ArrayList<>();
        JsonNode orderNode = root.path("profileOrder");
        if (orderNode.isArray()) {
            for (JsonNode n : orderNode) {
                if (n.isTextual()) {
                    profileOrder.add(n.asText());
                }
            }
        }
        if (!profileOrder.contains(profileName)) {
            ArrayNode newOrder = MAPPER.createArrayNode();
            for (String p : profileOrder) {
                newOrder.add(p);
            }
            newOrder.add(profileName);
            ((ObjectNode) root).set("profileOrder", newOrder);
        }
        Files.createDirectories(profilesFile.getParent());
        backupProfile(profilesFile);
        MAPPER.writeValue(profilesFile.toFile(), root);
        return new ProfileResult(profileName, profilesFile, true, null);
    }

    private static String findSystemJava() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isBlank()) {
            Path javaBin = Path.of(javaHome, "bin", "java");
            if (Files.isRegularFile(javaBin) || Files.isRegularFile(Path.of(javaHome, "bin", "java.exe"))) {
                return javaHome;
            }
        }
        String javaCmd = findJavaExecutable();
        if (javaCmd != null) {
            Path javaPath = Path.of(javaCmd).toAbsolutePath().normalize();
            Path parent = javaPath.getParent();
            if (parent != null) {
                Path grandParent = parent.getParent();
                if (grandParent != null && Files.isDirectory(grandParent)) {
                    return grandParent.toString();
                }
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
            if (Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }

    private static void backupProfile(Path profilesFile) throws IOException {
        if (!Files.isRegularFile(profilesFile)) {
            return;
        }
        Path backup = profilesFile.resolveSibling(profilesFile.getFileName() + ".fv2j3.bak");
        Files.copy(profilesFile, backup);
    }

    public static boolean profileExists(Path minecraftHome) {
        Path profilesFile = minecraftHome.resolve("launcher_profiles.json");
        if (!Files.isRegularFile(profilesFile)) {
            return false;
        }
        try {
            JsonNode root = MAPPER.readTree(Files.newInputStream(profilesFile));
            JsonNode profiles = root.path("profiles");
            if (!profiles.has(InstallerConstants.FV2J3_PROFILE_NAME)) {
                return false;
            }
            JsonNode profile = profiles.path(InstallerConstants.FV2J3_PROFILE_NAME);
            String lastVersion = profile.path("lastVersionId").asText(null);
            return InstallerConstants.FV2J3_VERSION_ID.equals(lastVersion);
        } catch (Exception ex) {
            return false;
        }
    }

    public static ProfileRemovalResult removeProfile(Path minecraftHome) throws IOException {
        Path profilesFile = minecraftHome.resolve("launcher_profiles.json");
        if (!Files.isRegularFile(profilesFile)) {
            return new ProfileRemovalResult(true, "No profiles file found", null);
        }
        JsonNode root = MAPPER.readTree(Files.newInputStream(profilesFile));
        if (!root.has("profiles")) {
            return new ProfileRemovalResult(true, "No profiles found", null);
        }
        ObjectNode profiles = (ObjectNode) root.path("profiles");
        if (!profiles.has(InstallerConstants.FV2J3_PROFILE_NAME)) {
            return new ProfileRemovalResult(true, "Fv2j3 profile not found", null);
        }
        profiles.remove(InstallerConstants.FV2J3_PROFILE_NAME);
        if (root.has("profileCache")) {
            ObjectNode cache = (ObjectNode) root.path("profileCache");
            cache.remove(InstallerConstants.FV2J3_PROFILE_NAME);
        }
        if (root.has("profileOrder")) {
            ArrayNode order = (ArrayNode) root.path("profileOrder");
            for (Iterator<JsonNode> it = order.iterator(); it.hasNext(); ) {
                if (InstallerConstants.FV2J3_PROFILE_NAME.equals(it.next().asText())) {
                    it.remove();
                    break;
                }
            }
        }
        backupProfile(profilesFile);
        MAPPER.writeValue(profilesFile.toFile(), root);
        return new ProfileRemovalResult(true, "Profile removed", profilesFile);
    }

    public record ProfileResult(
            String profileName,
            Path profilesFile,
            boolean success,
            String error
    ) {}

    public record ProfileRemovalResult(
            boolean success,
            String message,
            Path modifiedFile
    ) {}
}
