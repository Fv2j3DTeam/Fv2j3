package io.github.fv2j3dteam.mesrgl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Loads MesrGL native libraries from the classpath.
 * Supports Linux (x86_64, aarch64), macOS (x86_64, arm64), Windows (x86_64).
 */
public final class MesrGLNativeLoader {

    private static final String LIBRARY_NAME = "MesrGLBridge";
    private static volatile boolean loaded = false;
    private static volatile Throwable loadError = null;

    private MesrGLNativeLoader() {
    }

    /**
     * Loads the MesrGL native library.
     * Must be called before any MesrGL JNI methods are invoked.
     *
     * @throws UnsatisfiedLinkError if the library cannot be loaded
     */
    public static synchronized void load() {
        if (loaded) {
            if (loadError != null) {
                UnsatisfiedLinkError previous = new UnsatisfiedLinkError(
                        "MesrGL native library failed to load previously: " + loadError.getMessage());
                previous.initCause(loadError);
                throw previous;
            }
            return;
        }

        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String osArch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

        String classifier;
        String libName;
        String resourcePrefix;

        if (osName.contains("win")) {
            classifier = "windows-" + normalizeArch(osArch);
            libName = LIBRARY_NAME + ".dll";
            resourcePrefix = "META-INF/native/" + classifier + "/";
        } else if (osName.contains("mac")) {
            classifier = "macos-" + normalizeArch(osArch);
            libName = "lib" + LIBRARY_NAME + ".dylib";
            resourcePrefix = "META-INF/native/" + classifier + "/";
        } else {
            classifier = "linux-" + normalizeArch(osArch);
            libName = "lib" + LIBRARY_NAME + ".so";
            resourcePrefix = "META-INF/native/" + classifier + "/";
        }

        String resourcePath = resourcePrefix + libName;

        try {
            Path tempLib = extractNativeLibrary(resourcePath, libName);
            System.load(tempLib.toAbsolutePath().toString());
            loaded = true;
            System.out.println("[MesrGL] Native library loaded: " + tempLib);
        } catch (IOException | UnsatisfiedLinkError e) {
            loadError = e;
            UnsatisfiedLinkError failure = new UnsatisfiedLinkError(
                    "Failed to load MesrGL native library (" + classifier + "): " + e.getMessage());
            failure.initCause(e);
            throw failure;
        }
    }

    /**
     * Checks if the native library is loaded.
     */
    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * Gets the last load error, if any.
     */
    public static Throwable getLoadError() {
        return loadError;
    }

    private static String normalizeArch(String arch) {
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            return "x86_64";
        }
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return "aarch64";
        }
        if (arch.equals("x86") || arch.equals("i386") || arch.equals("i686")) {
            return "x86";
        }
        return arch;
    }

    private static Path extractNativeLibrary(String resourcePath, String libName) throws IOException {
        ClassLoader cl = MesrGLNativeLoader.class.getClassLoader();
        try (InputStream is = cl.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Native library not found in classpath: " + resourcePath);
            }

            Path tempDir = Files.createTempDirectory("mesrgl-native-");
            tempDir.toFile().deleteOnExit();

            Path tempLib = tempDir.resolve(libName);
            Files.copy(is, tempLib, StandardCopyOption.REPLACE_EXISTING);

            // Make executable on Unix-like systems
            if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                tempLib.toFile().setExecutable(true);
            }

            return tempLib;
        }
    }
}