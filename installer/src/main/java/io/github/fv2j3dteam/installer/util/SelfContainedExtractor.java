package io.github.fv2j3dteam.installer.util;

import io.github.fv2j3dteam.installer.InstallerMain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public final class SelfContainedExtractor {
    private static final Logger LOG = LoggerFactory.getLogger(SelfContainedExtractor.class);
    private static final String RUNTIME_PREFIX = "libs/";

    private static final String RESOURCE_DIR = "libs";
    private SelfContainedExtractor() {
    }

    public static Path extractRuntime(Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        Path libsDir = targetDir.resolve("libs");
        Files.createDirectories(libsDir);
        URL resource = SelfContainedExtractor.class.getClassLoader().getResource(RESOURCE_DIR);
        if (resource == null) {
            extractFromSameJar(targetDir);
            return targetDir;
        }
        if ("file".equals(resource.getProtocol())) {
            try {
                Path source = Paths.get(resource.toURI());
                copyDirectory(source, targetDir);
            } catch (URISyntaxException ex) {
                copyDirectory(Paths.get(resource.getPath()), targetDir);
            }
        } else if ("jar".equals(resource.getProtocol())) {
            extractFromJarUrl(resource, targetDir);
        } else {
            throw new IOException("Unsupported runtime resource protocol: " + resource.getProtocol());
        }
        return targetDir;
    }

    private static void extractFromSameJar(Path targetDir) throws IOException {
        String ownLocation = SelfContainedExtractor.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath();
        if (ownLocation == null || ownLocation.isBlank()) {
            return;
        }
        Path jarPath;
        try {
            jarPath = Paths.get(new URI(ownLocation));
        } catch (URISyntaxException ex) {
            return;
        }
        if (!Files.isRegularFile(jarPath)) {
            return;
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(RESOURCE_DIR + "/")) {
                    continue;
                }
                Path relative = Paths.get(name.substring(RESOURCE_DIR.length() + 1));
                Path target = targetDir.resolve(relative.toString());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent() == null ? targetDir : target.getParent());
                    try (InputStream in = jar.getInputStream(entry);
                         OutputStream out = Files.newOutputStream(target)) {
                        in.transferTo(out);
                    }
                }
            }
        }
    }

    private static void extractFromJarUrl(URL resource, Path targetDir) throws IOException {
        String spec = resource.getFile();
        int separator = spec.indexOf("!/");
        if (separator < 0) {
            return;
        }
        try {
            URL jarUrl = new URI(spec.substring(0, separator)).toURL();
            try (JarFile jar = new JarFile(Paths.get(jarUrl.toURI()).toFile())) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.startsWith(RESOURCE_DIR + "/")) {
                        continue;
                    }
                    Path relative = Paths.get(name.substring(RESOURCE_DIR.length() + 1));
                    Path target = targetDir.resolve(relative.toString());
                    if (entry.isDirectory()) {
                        Files.createDirectories(target);
                    } else {
                        Path parent = target.getParent();
                        if (parent != null) Files.createDirectories(parent);
                        try (InputStream in = jar.getInputStream(entry);
                             OutputStream out = Files.newOutputStream(target)) {
                            in.transferTo(out);
                        }
                    }
                }
            }
        } catch (URISyntaxException ex) {
            throw new IOException("Invalid runtime resource URL: " + resource, ex);
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(source)) return;
        try (var stream = Files.walk(source)) {
            stream.forEach(src -> {
                try {
                    Path rel = source.relativize(src);
                    Path dst = target.resolve(rel.toString());
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dst);
                    } else {
                        if (dst.getParent() != null) Files.createDirectories(dst.getParent());
                        Files.copy(src, dst);
                    }
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        }
    }

    public static int countEmbeddedLibraries() {
        URL resource = SelfContainedExtractor.class.getClassLoader().getResource(RESOURCE_DIR);
        if (resource == null) {
            return countFromSameJar();
        }
        if ("file".equals(resource.getProtocol())) {
            try {
                Path source = Paths.get(resource.toURI());
                if (!Files.isDirectory(source)) {
                    return 0;
                }
                try (var stream = Files.list(source.resolve("libs"))) {
                    return (int) stream.filter(Files::isRegularFile).count();
                }
            } catch (Exception ex) {
                return 0;
            }
        }
        if ("jar".equals(resource.getProtocol())) {
            return countFromJarUrl(resource);
        }
        return 0;
    }

    private static int countFromJarUrl(URL resource) {
        String spec = resource.getFile();
        int separator = spec.indexOf("!/");
        if (separator < 0) return 0;
        try {
            URL jarUrl = new URI(spec.substring(0, separator)).toURL();
            try (JarFile jar = new JarFile(Paths.get(jarUrl.toURI()).toFile())) {
                int count = 0;
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().startsWith("libs/") && !entry.isDirectory()) {
                        count++;
                    }
                }
                return count;
            }
        } catch (Exception ex) {
            return 0;
        }
    }

    private static int countFromSameJar() {
        try {
            String ownLocation = SelfContainedExtractor.class.getProtectionDomain()
                    .getCodeSource().getLocation().getPath();
            if (ownLocation == null || ownLocation.isBlank()) return 0;
            Path jarPath = Paths.get(new URI(ownLocation));
            if (!Files.isRegularFile(jarPath)) return 0;
            try (JarFile jar = new JarFile(jarPath.toFile())) {
                int count = 0;
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().startsWith("libs/") && !entry.isDirectory()) {
                        count++;
                    }
                }
                return count;
            }
        } catch (Exception ex) {
            return 0;
        }
    }

    public static boolean isEmbedded() {
        return countEmbeddedLibraries() > 0;
    }
}
