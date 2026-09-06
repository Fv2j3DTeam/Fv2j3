package com.fv2j3.loader.runtime;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Test-world save cleaner (Phase 47.1).
 *
 * Used by the Gradle {@code cleanTestWorld} task and unit-tested by
 * {@code TestWorldCleanerTest}. The logic is intentionally pure: it
 * accepts a runtime home and a project root, validates safety, and
 * deletes the contents of {@code <runtimeHome>/saves/}. Mods,
 * config, resourcepacks, assets, libraries, native libraries, and
 * the MesrGL / Fv2j3 binaries are never touched.
 *
 * <p>Safety rules (must all hold or the operation refuses):
 * <ol>
 *   <li>{@code runtimeHome} must be set.</li>
 *   <li>{@code runtimeHome} must exist.</li>
 *   <li>{@code runtimeHome} must be inside the project tree (or be
 *       the project tree itself).</li>
 *   <li>{@code runtimeHome} must NOT be the user's normal Minecraft
 *       install (i.e. {@code ~/.minecraft}).</li>
 * </ol>
 */
public final class TestWorldCleaner {

    /** Result of a clean operation, exposed for tests + verification logs. */
    public static final class Result {
        public final Path runtimeHome;
        public final Path savesDir;
        public final int worldsBefore;
        public final int worldsAfter;
        public final int deletedWorlds;
        public final List<String> skipped;
        public final boolean success;
        public Result(Path runtimeHome, Path savesDir, int before, int after,
                     int deleted, List<String> skipped) {
            this.runtimeHome = runtimeHome;
            this.savesDir = savesDir;
            this.worldsBefore = before;
            this.worldsAfter = after;
            this.deletedWorlds = deleted;
            this.skipped = Collections.unmodifiableList(skipped);
            this.success = after == 0;
        }
    }

    private TestWorldCleaner() {}

    /**
     * Cleans the {@code <runtimeHome>/saves/} directory.
     *
     * @param runtimeHome the Fv2j3 test runtime home (same path the
     *                    launcher uses; usually build/minecraft-runtime)
     * @param projectRoot the project root (canonical); used as the
     *                    safety boundary
     * @param userHome    the user's home directory; used to refuse
     *                    the normal {@code ~/.minecraft} install
     * @return the cleanup result
     * @throws IOException on I/O failure
     * @throws SecurityException if any safety rule fails
     */
    public static Result clean(Path runtimeHome, Path projectRoot, String userHome) throws IOException {
        if (runtimeHome == null) {
            throw new SecurityException("runtimeHome is null");
        }
        if (projectRoot == null) {
            throw new SecurityException("projectRoot is null");
        }
        Path canonical = runtimeHome.toAbsolutePath().normalize();
        Path projectCanonical = projectRoot.toAbsolutePath().normalize();
        if (!isInsideOrEqual(canonical, projectCanonical)) {
            throw new SecurityException(String.format(Locale.ROOT,
                    "refusing to clean %s: outside project tree %s",
                    canonical, projectCanonical));
        }
        if (userHome != null && !userHome.isEmpty()) {
            Path userMc = Path.of(userHome, ".minecraft").toAbsolutePath().normalize();
            if (canonical.equals(userMc) || isInsideOrEqual(canonical, userMc)) {
                throw new SecurityException(String.format(Locale.ROOT,
                        "refusing to clean %s: this is the user's normal Minecraft install",
                        canonical));
            }
        }
        if (!Files.isDirectory(canonical)) {
            throw new SecurityException("runtime directory does not exist: " + canonical);
        }
        Path savesDir = canonical.resolve("saves");
        int before = countWorlds(savesDir);
        List<String> skipped = new ArrayList<>();
        int deleted = 0;
        if (Files.isDirectory(savesDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(savesDir)) {
                for (Path entry : stream) {
                    if (!Files.isDirectory(entry)) {
                        // Loose files inside saves/ — delete directly.
                        Files.deleteIfExists(entry);
                        continue;
                    }
                    // Defensive: confirm parent is exactly savesDir.
                    Path entryParent = entry.getParent();
                    if (entryParent == null
                            || !entryParent.toAbsolutePath().normalize()
                                    .equals(savesDir.toAbsolutePath().normalize())) {
                        skipped.add(entry.getFileName().toString());
                        continue;
                    }
                    deleteRecursively(entry);
                    deleted++;
                }
            }
        }
        int after = countWorlds(savesDir);
        return new Result(canonical, savesDir, before, after, deleted, skipped);
    }

    private static boolean isInsideOrEqual(Path child, Path ancestor) {
        if (child.equals(ancestor)) {
            return true;
        }
        return child.startsWith(ancestor);
    }

    private static int countWorlds(Path savesDir) throws IOException {
        if (!Files.isDirectory(savesDir)) {
            return 0;
        }
        int n = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(savesDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    n++;
                }
            }
        }
        return n;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (Files.isDirectory(root)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
                for (Path child : stream) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(root);
    }

    /** Renders the result as a human-readable multi-line block. */
    public static String formatResult(Result r) {
        return new StringBuilder()
                .append("\n[Fv2j3 Test Environment]\n")
                .append("Game directory: ").append(r.runtimeHome).append('\n')
                .append("Save directory: ").append(r.savesDir).append('\n')
                .append("Existing test worlds: ").append(r.worldsBefore).append('\n')
                .append("Cleaning saves...\n")
                .append("Save cleanup: ").append(r.success ? "SUCCESS" : "FAILED").append('\n')
                .append("Skipped (outside saves/): ")
                .append(r.skipped.isEmpty() ? "none" : String.join(", ", r.skipped)).append('\n')
                .append("Remaining test worlds: ").append(r.worldsAfter).append('\n')
                .toString();
    }

    // Compatibility shim used by the Gradle build.gradle: convert a
    // Path-based call into the Groovy-style File-based signature so the
    // task body does not have to know about Path.
    public static Result clean(File runtimeHome, File projectRoot, String userHome) throws IOException {
        return clean(runtimeHome.toPath(), projectRoot.toPath(), userHome);
    }
}
