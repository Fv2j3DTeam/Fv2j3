package io.github.fv2j3dteam.loader.runtime;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Entry point for the Gradle {@code cleanTestWorld} task. Reads the
 * runtime / project / user-home paths from system properties (set by
 * the Gradle wrapper) and delegates the actual work to
 * {@link TestWorldCleaner}.
 *
 * <p>System properties consumed:
 * <ul>
 *   <li>{@code fv2j3.cleaner.runtimeHome} - required</li>
 *   <li>{@code fv2j3.cleaner.projectRoot} - required</li>
 *   <li>{@code fv2j3.cleaner.userHome}    - optional (empty ok)</li>
 * </ul>
 *
 * <p>Exit code 0 on success, 1 on safety refusal, 2 on I/O failure.
 * The verification block is printed to standard output.
 */
public final class CleanerMain {

    private CleanerMain() {}

    public static void main(String[] args) {
        String runtime = System.getProperty("fv2j3.cleaner.runtimeHome");
        String projectRoot = System.getProperty("fv2j3.cleaner.projectRoot");
        String userHome = System.getProperty("fv2j3.cleaner.userHome", "");
        if (runtime == null || runtime.isEmpty() || projectRoot == null || projectRoot.isEmpty()) {
            System.err.println("cleanTestWorld: missing required system properties "
                    + "(fv2j3.cleaner.runtimeHome, fv2j3.cleaner.projectRoot)");
            System.exit(1);
        }
        TestWorldCleaner.Result result = null;
        try {
            result = TestWorldCleaner.clean(
                    Paths.get(runtime), Paths.get(projectRoot), userHome);
        } catch (SecurityException ex) {
            System.err.println("cleanTestWorld: " + ex.getMessage());
            System.exit(1);
        } catch (Exception ex) {
            System.err.println("cleanTestWorld I/O failure: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(2);
        }
        if (result == null) {
            System.err.println("cleanTestWorld: cleaner returned no result");
            System.exit(2);
        }
        System.out.print(TestWorldCleaner.formatResult(result));
        if (!result.success) {
            System.err.println("cleanTestWorld: failed; " + result.worldsAfter + " worlds remain");
            System.exit(1);
        }
    }
}
