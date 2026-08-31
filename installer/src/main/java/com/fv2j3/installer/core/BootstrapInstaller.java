package com.fv2j3.installer.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class BootstrapInstaller {
    private static final String[] RUNTIME_JARS = {
            "loader-api-0.1.0-SNAPSHOT.jar",
            "loader-core-0.1.0-SNAPSHOT.jar",
            "loader-runtime-0.1.0-SNAPSHOT.jar",
            "minecraft-compat-0.1.0-SNAPSHOT.jar",
            "jackson-databind-2.17.2.jar",
            "jackson-core-2.17.2.jar",
            "jackson-annotations-2.17.2.jar",
            "asm-9.8.jar"
    };

    private BootstrapInstaller() {
    }

    public static List<Path> installBootstrap(Path runtimeDir) throws IOException {
        List<Path> installed = new ArrayList<>();
        Path libsDir = runtimeDir.resolve("libs");
        Files.createDirectories(libsDir);
        for (String jarName : RUNTIME_JARS) {
            Path target = libsDir.resolve(jarName);
            if (Files.exists(target)) {
                installed.add(target);
                continue;
            }
            Path source = findEmbeddedJar(jarName);
            if (source != null && Files.isRegularFile(source)) {
                Files.copy(source, target);
                installed.add(target);
            }
        }
        return installed;
    }

    public static Path findEmbeddedJar(String jarName) {
        String resourcePath = "/libs/" + jarName;
        var url = BootstrapInstaller.class.getResource(resourcePath);
        if (url == null) {
            return null;
        }
        try {
            return Paths.get(url.toURI());
        } catch (Exception ex) {
            return null;
        }
    }

    public static int countInstalledLibraries(Path runtimeDir) {
        Path libsDir = runtimeDir.resolve("libs");
        if (!Files.isDirectory(libsDir)) {
            return 0;
        }
        int count = 0;
        try (var stream = Files.list(libsDir)) {
            count = (int) stream.filter(Files::isRegularFile).count();
        } catch (IOException ex) {
            return 0;
        }
        return count;
    }
}
