package com.fv2j3.installer.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class VersionJsonGenerator {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static final String FV2J3_VERSION_ID = "Fv2j3-1.12.2";

    private VersionJsonGenerator() {
    }

    public static VersionJson generate(Path minecraftHome, Path runtimeDir) throws IOException {
        Objects.requireNonNull(minecraftHome, "minecraftHome");
        Objects.requireNonNull(runtimeDir, "runtimeDir");
        Path baseVersionDir = minecraftHome.resolve("versions").resolve(InstallerConstants.MINECRAFT_VERSION);
        Path baseVersionJson = baseVersionDir.resolve(InstallerConstants.MINECRAFT_VERSION + ".json");
        if (!Files.isRegularFile(baseVersionJson)) {
            throw new IOException("Base Minecraft 1.12.2 version JSON not found: " + baseVersionJson);
        }
        JsonNode base = MAPPER.readTree(Files.newInputStream(baseVersionJson));
        ObjectNode root = MAPPER.createObjectNode();
        root.put("id", FV2J3_VERSION_ID);
        root.put("inheritsFrom", InstallerConstants.MINECRAFT_VERSION);
        root.put("type", "release");
        root.put("mainClass", InstallerConstants.FV2J3_MAIN_CLASS);
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
        ArrayNode game = MAPPER.createArrayNode();
        ArrayNode jvm = MAPPER.createArrayNode();
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
        if (baseLibs != null && baseLibs.isArray()) {
            for (JsonNode lib : baseLibs) {
                libraries.add(lib.deepCopy());
            }
        }
    }

    private static void copyBaseGameArguments(ArrayNode game, JsonNode base) {
        JsonNode baseArgs = base.path("arguments");
        if (baseArgs != null && baseArgs.isMissingNode()) {
            return;
        }
        JsonNode baseGame = base.path("arguments").path("game");
        if (baseGame != null && baseGame.isArray()) {
            for (JsonNode arg : baseGame) {
                game.add(arg.deepCopy());
            }
        } else {
            JsonNode minecraftArgs = base.path("minecraftArguments");
            if (minecraftArgs != null && minecraftArgs.isTextual()) {
                for (String s : minecraftArgs.asText().split(" ")) {
                    game.add(s);
                }
            }
        }
    }

    private static void copyBaseJvmArguments(ArrayNode jvm, JsonNode base) {
        JsonNode baseJvm = base.path("arguments").path("jvm");
        if (baseJvm != null && baseJvm.isArray()) {
            for (JsonNode arg : baseJvm) {
                jvm.add(arg.deepCopy());
            }
        }
    }

    private static void addFv2j3Libraries(ArrayNode libraries, Path runtimeDir) {
        Path libsDir = runtimeDir.resolve("libs");
        if (!Files.isDirectory(libsDir)) {
            return;
        }
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
                        try {
                            size = Files.size(jar);
                        } catch (IOException e) {
                            size = 0L;
                        }
                        artifact.put("size", size);
                        downloads.set("artifact", artifact);
                        lib.set("downloads", downloads);
                        String name = "com.fv2j3:" + stripExtension(fileName) + ":runtime";
                        lib.put("name", name);
                        libraries.add(lib);
                    });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to enumerate runtime libraries: " + libsDir, ex);
        }
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    public record VersionJson(
            @JsonProperty("id") String id,
            @JsonProperty("directory") Path directory,
            @JsonProperty("versionJson") Path versionJson,
            @JsonProperty("root") ObjectNode root
    ) {
    }
}
