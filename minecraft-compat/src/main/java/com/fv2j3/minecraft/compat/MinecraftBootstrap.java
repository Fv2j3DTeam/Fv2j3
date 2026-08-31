package com.fv2j3.minecraft.compat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fv2j3.loader.core.Fv2j3Loader;
import com.fv2j3.loader.core.LoaderEnvironment;
import com.fv2j3.loader.core.LoaderInitializationException;
import com.fv2j3.loader.core.LoaderLogger;
import com.fv2j3.loader.core.LoaderSide;
import com.fv2j3.loader.core.StandardLoaderLogger;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class MinecraftBootstrap {
    public static final String VERSION = "1.12.2";
    public static final String CLIENT_MAIN_CLASS = "net.minecraft.client.main.Main";
    public static final String SERVER_MAIN_CLASS = "net.minecraft.server.MinecraftServer";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MinecraftBootstrap() {
    }

    public static MinecraftBootstrapResult detect(Path configuredHome) {
        Path resolvedHome = resolveHome(configuredHome);
        List<Path> candidates = scan(resolvedHome);
        if (candidates.isEmpty()) {
            return new MinecraftBootstrapResult(
                    VERSION,
                    false,
                    resolvedHome,
                    List.of(),
                    "No Minecraft 1.12.2 runtime was found at '" + resolvedHome + "'. Provide --minecraft-home=/path/to/1.12.2 or a valid runtime directory.",
                    null,
                    List.of(),
                    List.of(),
                    LaunchKind.UNKNOWN
            );
        }

        Path runtimeRoot = resolveRuntimeRoot(resolvedHome, candidates.getFirst());
        LaunchSpec spec = resolveLaunchSpec(resolvedHome, runtimeRoot, LaunchKind.CLIENT).orElse(null);
        if (spec == null) {
            return new MinecraftBootstrapResult(
                    VERSION,
                    false,
                    resolvedHome,
                    candidates,
                    "A Minecraft 1.12.2 installation was found, but its metadata or main class could not be resolved.",
                    null,
                    List.of(),
                    List.of(),
                    LaunchKind.UNKNOWN
            );
        }

        return new MinecraftBootstrapResult(
                VERSION,
                true,
                resolvedHome,
                candidates,
                "Minecraft 1.12.2 runtime detected at '" + resolvedHome + "'.",
                spec.mainClass,
                spec.classpath,
                spec.nativeDirectories,
                spec.kind
        );
    }

    public static void launchClient(Path configuredHome, String[] args) {
        launchClientWithLoaderCallback(configuredHome, args, null);
    }

    public static void launchServer(Path configuredHome, String[] args) {
        launchServerWithLoaderCallback(configuredHome, args, null);
    }

    public static void launch(Path configuredHome, String[] args) {
        launchWithLoaderCallback(configuredHome, args, null);
    }

    public static void launchClientWithLoaderCallback(Path configuredHome, String[] args, LoaderCallback callback) {
        launchWithCallback(configuredHome, args, callback, LaunchKind.CLIENT, LoaderSide.CLIENT);
    }

    public static void launchServerWithLoaderCallback(Path configuredHome, String[] args, LoaderCallback callback) {
        launchWithCallback(configuredHome, args, callback, LaunchKind.SERVER, LoaderSide.DEDICATED_SERVER);
    }

    public static void launchWithLoaderCallback(Path configuredHome, String[] args, LoaderCallback callback) {
        LoaderSide side = LoaderSide.fromProperty("fv2j3.launch.target");
        if (side == LoaderSide.DEDICATED_SERVER) {
            launchWithCallback(configuredHome, args, callback, LaunchKind.SERVER, side);
        } else {
            launchWithCallback(configuredHome, args, callback, LaunchKind.CLIENT, LoaderSide.CLIENT);
        }
    }

    private static void launchWithCallback(Path configuredHome, String[] args, LoaderCallback callback, LaunchKind kind, LoaderSide side) {
        System.setProperty("fv2j3.launch.target", side.value());
        Path resolvedHome = resolveHome(configuredHome);
        Path runtimeRoot = resolveRuntimeRoot(resolvedHome, null);
        LaunchSpec spec = resolveLaunchSpec(resolvedHome, runtimeRoot, kind)
                .orElseThrow(() -> new IllegalStateException("No valid Minecraft 1.12.2 " + kind.name().toLowerCase() + " runtime could be located. Use --minecraft-home or -Dfv2j3.minecraft.home."));
        URL[] urls = spec.classpath.stream().map(MinecraftBootstrap::toUrl).toArray(URL[]::new);
        String nativePath = spec.nativeDirectories.stream().map(Path::toString).collect(java.util.stream.Collectors.joining(File.pathSeparator));
        if (!nativePath.isBlank()) {
            System.setProperty("java.library.path", nativePath);
            String primaryNativePath = spec.nativeDirectories.getFirst().toString();
            System.setProperty("org.lwjgl.librarypath", primaryNativePath);
            System.setProperty("net.java.games.input.librarypath", primaryNativePath);
        }

        Path modsDir = resolveModsDirectory(args);
        LoaderLogger logger = new StandardLoaderLogger("Fv2j3");
        Fv2j3Loader loader = null;
        boolean loaderSuccess = false;
        if (modsDir != null && Files.exists(modsDir)) {
            logger.info("Fv2j3[" + kind + "]: Mod directory: " + modsDir);
            LoaderEnvironment env = LoaderEnvironment.detectCurrent(VERSION, "0.1.0-SNAPSHOT", side);
            loader = new Fv2j3Loader(env, logger);
            // Feed real loader lifecycle events into the progress tracker before
            // initialization starts, so the loading screen and logs see every stage.
            loader.setProgressListener(LoadingProgressTracker.getShared()::onProgress);
            // On the client, signal the mod-menu transformer that the loading
            // screen should display. This is also the trigger the StressTest
            // Runner uses to confirm the real Minecraft Client reached its GUI.
            if (kind == LaunchKind.CLIENT) {
                MinecraftModMenuTransformer.setLoading(true);
            }
            try {
                loader.initialize(modsDir);
                loader.start();
                Fv2j3RuntimeBridge.register(loader);
                loaderSuccess = true;
                logger.info("Fv2j3[" + kind + "]: Discovered " + loader.context().modRegistry().all().size() + " mods in Minecraft runtime");
            } catch (LoaderInitializationException ex) {
                logger.error("Fv2j3[" + kind + "]: Mod loading failed: " + ex.getMessage(), ex);
            }
        } else if (modsDir != null) {
            logger.info("Fv2j3[" + kind + "]: Mod directory does not exist: " + modsDir + " (will skip mod loading)");
        }

        if (callback != null) {
            try { callback.onLoaderReady(loader, loaderSuccess); } catch (Exception e) {
                System.err.println("Loader callback error: " + e.getMessage());
            }
        }

        try (URLClassLoader loader2 = new URLClassLoader(urls, MinecraftBootstrap.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                Class<?> loaded = findLoadedClass(name);
                if (loaded != null) {
                    return loaded;
                }
                String resourceName = name.replace('.', '/') + ".class";
                URL resource = findResource(resourceName);
                if (resource == null) {
                    throw new ClassNotFoundException(name);
                }
                try (var input = resource.openStream()) {
                    byte[] bytes = input.readAllBytes();
                    if (kind == LaunchKind.CLIENT) {
                        byte[] transformed = MinecraftModMenuTransformer.transform(name, bytes);
                        return defineClass(name, transformed, 0, transformed.length);
                    }
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (IOException ex) {
                    throw new ClassNotFoundException(name, ex);
                }
            }
        }) {
            Thread.currentThread().setContextClassLoader(loader2);
            Class<?> mainClass = Class.forName(spec.mainClass, true, loader2);
            Method main = mainClass.getMethod("main", String[].class);
            String[] mcArgs = (kind == LaunchKind.CLIENT)
                    ? minecraftClientArguments(args, resolvedHome)
                    : minecraftServerArguments(args, resolvedHome);
            main.invoke(null, new Object[] { mcArgs });
        } catch (ReflectiveOperationException ex) {
            Throwable cause = ex instanceof InvocationTargetException invocation
                    && invocation.getCause() != null ? invocation.getCause() : ex;
            throw new IllegalStateException("Failed to invoke Minecraft " + kind.name().toLowerCase() + " main class '" + spec.mainClass + "': " + cause, cause);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build the Minecraft launch classpath.", ex);
        }
    }

    public interface LoaderCallback {
        void onLoaderReady(Fv2j3Loader loader, boolean success);
    }

    public enum LaunchKind {
        CLIENT,
        SERVER,
        UNKNOWN
    }

    private static Path resolveModsDirectory(String[] args) {
        if (args == null) return null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--mods".equals(arg) && i + 1 < args.length) {
                return Path.of(args[i + 1]).toAbsolutePath().normalize();
            }
            if (arg.startsWith("--mods=")) {
                return Path.of(arg.substring("--mods=".length())).toAbsolutePath().normalize();
            }
        }
        String prop = System.getProperty("fv2j3.mods.dir");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop).toAbsolutePath().normalize();
        }
        Path defaultMods = Path.of("mods").toAbsolutePath().normalize();
        return defaultMods;
    }

    private static String[] minecraftClientArguments(String[] supplied, Path runtimeHome) {
        List<String> arguments = new ArrayList<>(List.of(supplied == null ? new String[0] : supplied));
        addArgument(arguments, "--username", "Fv2j3");
        addArgument(arguments, "--version", VERSION);
        addArgument(arguments, "--gameDir", runtimeHome.toString());
        addArgument(arguments, "--assetsDir", runtimeHome.resolve("assets").toString());
        addArgument(arguments, "--assetIndex", "1.12");
        addArgument(arguments, "--uuid", "00000000-0000-0000-0000-000000000000");
        addArgument(arguments, "--accessToken", "0");
        addArgument(arguments, "--userType", "legacy");
        return arguments.toArray(String[]::new);
    }

    private static String[] minecraftServerArguments(String[] supplied, Path runtimeHome) {
        List<String> arguments = new ArrayList<>(List.of(supplied == null ? new String[0] : supplied));
        addArgument(arguments, "--gameDir", runtimeHome.toString());
        if (!containsArg(arguments, "--nogui") && !containsArg(arguments, "nogui")) {
            arguments.add("nogui");
        }
        return arguments.toArray(String[]::new);
    }

    private static boolean containsArg(List<String> args, String key) {
        for (String a : args) {
            if (a.equals(key) || a.equals("--" + key)) {
                return true;
            }
        }
        return false;
    }

    private static void addArgument(List<String> arguments, String key, String value) {
        if (!arguments.contains(key)) {
            arguments.add(key);
            arguments.add(value);
        }
    }

    private static Optional<LaunchSpec> resolveLaunchSpec(Path runtimeHome, Path runtimeRoot, LaunchKind kind) {
        if (runtimeRoot == null) {
            return Optional.empty();
        }

        Path versionRoot = runtimeRoot.resolve("versions").resolve(VERSION);
        Path versionJson = versionRoot.resolve(VERSION + ".json");
        Path minecraftJar = versionRoot.resolve(VERSION + ".jar");
        if (!Files.isRegularFile(minecraftJar) && !Files.isRegularFile(versionJson)) {
            return Optional.empty();
        }

        List<Path> classpath = new ArrayList<>();
        if (Files.isRegularFile(minecraftJar)) {
            classpath.add(minecraftJar);
        }
        Path serverJar = versionRoot.resolve("minecraft_server" + ".jar");
        if (kind == LaunchKind.SERVER && Files.isRegularFile(serverJar)) {
            classpath.add(serverJar);
        }

        if (Files.isRegularFile(versionJson)) {
            try {
                JsonNode root = MAPPER.readTree(versionJson.toFile());
                String mainClass;
                if (kind == LaunchKind.SERVER) {
                    mainClass = SERVER_MAIN_CLASS;
                } else {
                    mainClass = root.path("mainClass").asText(CLIENT_MAIN_CLASS);
                }

                JsonNode libraries = root.path("libraries");
                if (libraries != null && libraries.isArray()) {
                    for (JsonNode library : libraries) {
                        Path libraryPath = resolveLibraryPath(runtimeRoot, library);
                        if (libraryPath != null && Files.isRegularFile(libraryPath)) {
                            classpath.add(libraryPath);
                        }
                    }
                }

                List<Path> nativeDirectories = kind == LaunchKind.CLIENT
                        ? resolveNativeDirectories(runtimeRoot, root)
                        : List.of();
                return Optional.of(new LaunchSpec(runtimeHome, mainClass, classpath, nativeDirectories, kind));
            } catch (IOException ex) {
                return Optional.empty();
            }
        }

        String mainClass = kind == LaunchKind.SERVER ? SERVER_MAIN_CLASS : CLIENT_MAIN_CLASS;
        return Optional.of(new LaunchSpec(runtimeHome, mainClass, classpath, List.of(), kind));
    }

    private static Path resolveRuntimeRoot(Path runtimeHome, Path candidate) {
        if (runtimeHome == null) {
            return null;
        }
        if (Files.isDirectory(runtimeHome.resolve("versions").resolve(VERSION))) {
            return runtimeHome;
        }

        if (candidate != null && Files.isRegularFile(candidate)) {
            Path parent = candidate.getParent();
            if (parent != null && parent.getFileName() != null && VERSION.equals(parent.getFileName().toString())) {
                return parent.getParent() == null ? runtimeHome : parent.getParent();
            }
            Path grandParent = parent == null ? null : parent.getParent();
            if (grandParent != null && grandParent.getFileName() != null && "versions".equals(grandParent.getFileName().toString())) {
                return grandParent.getParent() == null ? runtimeHome : grandParent.getParent();
            }
        }

        Path versionsDir = runtimeHome.resolve("versions");
        if (Files.isDirectory(versionsDir)) {
            try (var stream = Files.list(versionsDir)) {
                return stream.filter(Files::isDirectory)
                        .filter(path -> path.getFileName() != null && VERSION.equals(path.getFileName().toString()))
                        .findFirst()
                        .orElse(runtimeHome);
            } catch (IOException ex) {
                return runtimeHome;
            }
        }

        return runtimeHome;
    }

    private static List<Path> resolveNativeDirectories(Path runtimeRoot, JsonNode root) {
        List<Path> nativeDirectories = new ArrayList<>();
        Path versionRoot = runtimeRoot.resolve("versions").resolve(VERSION);
        for (Path candidate : List.of(
                versionRoot.resolve("natives"),
                versionRoot.resolve(VERSION + "-natives"),
                versionRoot.resolve(VERSION + "-natives-linux"),
                versionRoot.resolve(VERSION + "-natives-windows")
        )) {
            if (Files.isDirectory(candidate)) {
                nativeDirectories.add(candidate);
            }
        }

        String currentOs = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String nativeClassifier = switch (currentOs) {
            case String s when s.contains("win") -> "natives-windows";
            case String s when s.contains("mac") -> "natives-macos";
            default -> "natives-linux";
        };

        JsonNode libraries = root.path("libraries");
        if (libraries != null && libraries.isArray()) {
            for (JsonNode library : libraries) {
                JsonNode classifiers = library.path("downloads").path("classifiers");
                if (classifiers != null && classifiers.has(nonNull(nativeClassifier))) {
                    Path nativeJar = resolveLibraryPath(runtimeRoot, library, nativeClassifier);
                    if (nativeJar != null && Files.isRegularFile(nativeJar)) {
                        nativeDirectories.add(nativeJar.getParent());
                    }
                }
            }
        }
        return nativeDirectories.stream().distinct().toList();
    }

    private static Path resolveLibraryPath(Path runtimeRoot, JsonNode library) {
        return resolveLibraryPath(runtimeRoot, library, null);
    }

    private static Path resolveLibraryPath(Path runtimeRoot, JsonNode library, String classifierOverride) {
        JsonNode artifactNode = library.path("downloads").path("artifact");
        if (!artifactNode.isMissingNode() && artifactNode.has("path")) {
            String pathText = artifactNode.path("path").asText(null);
            if (pathText != null && !pathText.isBlank()) {
                return runtimeRoot.resolve(pathText.replace('/', File.separatorChar));
            }
        }

        JsonNode classifierNode = library.path("downloads").path("classifiers");
        if (classifierOverride != null && classifierNode != null && classifierNode.has(classifierOverride)) {
            String pathText = classifierNode.path(classifierOverride).path("path").asText(null);
            if (pathText != null && !pathText.isBlank()) {
                return runtimeRoot.resolve(pathText.replace('/', File.separatorChar));
            }
        }

        String name = library.path("name").asText(null);
        if (name == null || name.isBlank()) {
            return null;
        }
        String[] coords = name.split(":");
        if (coords.length < 3) {
            return null;
        }
        String group = coords[0].replace('.', File.separatorChar);
        String artifact = coords[1];
        String version = coords[2];
        String suffix = classifierOverride == null ? "" : "-" + classifierOverride;
        return runtimeRoot.resolve("libraries").resolve(group).resolve(artifact).resolve(version)
                .resolve(artifact + "-" + version + suffix + ".jar");
    }

    private static Path resolveHome(Path configuredHome) {
        if (configuredHome != null && !configuredHome.toString().isBlank()) {
            return configuredHome.toAbsolutePath().normalize();
        }

        String property = System.getProperty("fv2j3.minecraft.home");
        if (property != null && !property.isBlank()) {
            return Path.of(property).toAbsolutePath().normalize();
        }

        String explicit = System.getProperty("minecraft.home");
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit).toAbsolutePath().normalize();
        }

        return Path.of("minecraft").toAbsolutePath().normalize();
    }

    private static List<Path> scan(Path home) {
        List<Path> candidates = new ArrayList<>();
        if (home == null) {
            return candidates;
        }

        try {
            if (Files.isDirectory(home)) {
                walk(home, candidates);
            }
        } catch (IOException ex) {
            return candidates;
        }

        return List.copyOf(candidates);
    }

    private static void walk(Path root, List<Path> candidates) throws IOException {
        if (Files.isRegularFile(root) && isCandidate(root)) {
            candidates.add(root);
            return;
        }

        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(MinecraftBootstrap::isCandidate)
                    .forEach(candidates::add);
        }
    }

    private static boolean isCandidate(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return (name.contains("minecraft") && name.endsWith(".jar")) || name.equals("1.12.2.jar") || name.equals("minecraft.jar");
    }

    private static URL toUrl(Path path) {
        try {
            return path.toUri().toURL();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to convert path to URL: " + path, ex);
        }
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }

    public record LaunchSpec(Path runtimeHome, String mainClass, List<Path> classpath, List<Path> nativeDirectories, LaunchKind kind) {
        public LaunchSpec {
            Objects.requireNonNull(runtimeHome, "runtimeHome");
            mainClass = Objects.requireNonNullElse(mainClass, "");
            classpath = classpath == null ? List.of() : List.copyOf(classpath);
            nativeDirectories = nativeDirectories == null ? List.of() : List.copyOf(nativeDirectories);
            kind = kind == null ? LaunchKind.CLIENT : kind;
        }
    }

    public record MinecraftBootstrapResult(
            String version,
            boolean available,
            Path runtimeHome,
            List<Path> candidates,
            String message,
            String mainClass,
            List<Path> classpath,
            List<Path> nativeDirectories,
            LaunchKind kind
    ) {
        public MinecraftBootstrapResult {
            Objects.requireNonNull(version, "version");
            runtimeHome = runtimeHome == null ? Path.of("") : runtimeHome;
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            message = message == null ? "" : message;
            classpath = classpath == null ? List.of() : List.copyOf(classpath);
            nativeDirectories = nativeDirectories == null ? List.of() : List.copyOf(nativeDirectories);
            kind = kind == null ? LaunchKind.UNKNOWN : kind;
        }
    }
}
