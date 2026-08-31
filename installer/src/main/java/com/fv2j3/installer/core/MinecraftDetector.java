package com.fv2j3.installer.core;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class MinecraftDetector {
    private static final String MINECRAFT_VERSION = "1.12.2";

    private MinecraftDetector() {
    }

    public static Path detectMinecraftDirectory() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path detected;
        if (os.contains("win")) {
            detected = detectWindows();
        } else if (os.contains("mac")) {
            detected = detectMac();
        } else {
            detected = detectLinux();
        }
        if (detected != null && Files.isDirectory(detected)) {
            return detected.toAbsolutePath().normalize();
        }
        return null;
    }

    private static Path detectWindows() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            Path dotMinecraft = Paths.get(appData, ".minecraft");
            if (Files.isDirectory(dotMinecraft)) {
                return dotMinecraft;
            }
        }
        return null;
    }

    private static Path detectMac() {
        Path minecraft = Paths.get(System.getProperty("user.home"),
                "Library", "Application Support", "minecraft");
        if (Files.isDirectory(minecraft)) {
            return minecraft;
        }
        return null;
    }

    private static Path detectLinux() {
        Path minecraft = Paths.get(System.getProperty("user.home"), ".minecraft");
        if (Files.isDirectory(minecraft)) {
            return minecraft;
        }
        return null;
    }

    public static MinecraftDirectory analyze(Path minecraftHome) {
        if (minecraftHome == null || !Files.isDirectory(minecraftHome)) {
            return MinecraftDirectory.notFound();
        }
        Path versionsDir = minecraftHome.resolve("versions");
        boolean hasVersionsDir = Files.isDirectory(versionsDir);
        List<Path> foundVersions = new ArrayList<>();
        if (hasVersionsDir) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionsDir)) {
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        foundVersions.add(entry);
                    }
                }
            } catch (IOException ex) {
                // ignore
            }
        }
        boolean hasVersion112 = foundVersions.stream()
                .anyMatch(p -> MINECRAFT_VERSION.equals(p.getFileName().toString()));
        Path versionRoot = hasVersion112
                ? versionsDir.resolve(MINECRAFT_VERSION)
                : null;
        boolean hasVersionJson = versionRoot != null
                && Files.isRegularFile(versionRoot.resolve(MINECRAFT_VERSION + ".json"));
        boolean hasVersionJar = versionRoot != null
                && Files.isRegularFile(versionRoot.resolve(MINECRAFT_VERSION + ".jar"));
        return new MinecraftDirectory(
                minecraftHome.toAbsolutePath().normalize(),
                hasVersionsDir,
                List.copyOf(foundVersions),
                MINECRAFT_VERSION,
                hasVersion112,
                hasVersionJson,
                hasVersionJar
        );
    }

    public static boolean isValidMinecraftInstallation(Path minecraftHome) {
        MinecraftDirectory dir = analyze(minecraftHome);
        return dir.hasVersionsDirectory()
                && dir.hasVersion112()
                && dir.hasVersionJson()
                && dir.hasVersionJar();
    }

    public record MinecraftDirectory(
            Path path,
            boolean hasVersionsDirectory,
            List<Path> availableVersions,
            String targetVersion,
            boolean hasVersion112,
            boolean hasVersionJson,
            boolean hasVersionJar
    ) {
        public static MinecraftDirectory notFound() {
            return new MinecraftDirectory(null, false, List.of(), MINECRAFT_VERSION, false, false, false);
        }

        public boolean isValid() {
            return path != null && hasVersionsDirectory && hasVersion112 && hasVersionJson && hasVersionJar;
        }

        public String describe() {
            if (path == null) {
                return "Minecraft directory not found";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Minecraft directory: ").append(path).append("\n");
            sb.append("Versions directory: ").append(hasVersionsDirectory ? "present" : "missing").append("\n");
            sb.append("Available versions: ").append(availableVersions.size()).append("\n");
            sb.append("Minecraft ").append(targetVersion).append(": ");
            if (hasVersion112) {
                sb.append("found");
                if (hasVersionJson && hasVersionJar) {
                    sb.append(" (complete)");
                } else {
                    sb.append(" (incomplete)");
                }
            } else {
                sb.append("not found");
            }
            return sb.toString();
        }
    }
}
