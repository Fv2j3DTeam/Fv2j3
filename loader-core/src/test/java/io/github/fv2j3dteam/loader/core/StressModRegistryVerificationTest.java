package io.github.fv2j3dteam.loader.core;

import io.github.fv2j3dteam.api.Fv2j3Registries;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 47.1 acceptance test: real stress mods loaded by the real
 * Fv2j3Loader must result in the canonical registry IDs being present.
 *
 * The spec calls this "Minecraft Registry Verification": the loader
 * cannot satisfy the requirement by reporting SUCCESS; we read the
 * registry and assert specific entries (testmod_001:item_001,
 * testmod_005:creative_tab) actually exist.
 *
 * We use a small number of mods (5) to keep the test under a second
 * while still going through the entire real pipeline: javac compile
 * of the generated source, real JAR packaging, real Fv2j3Loader with
 * dependency resolution, real Fv2j3Registries.
 */
class StressModRegistryVerificationTest {

    private static final int TEST_MOD_COUNT = 5;

    @Test
    void realStressModsRegisterSpecificIds(@TempDir Path tmp) throws Exception {
        Path outDir = tmp.resolve("src");
        Path jarsDir = tmp.resolve("jars");
        Files.createDirectories(outDir);
        Files.createDirectories(jarsDir);

        // 1. Generate real stress mods (real .class files compiled by
        //    the in-process JDK compiler, packaged into real JARs).
        Path loaderApiJar = locateLoaderApiJar();
        io.github.fv2j3dteam.loader.runtime.StressModGenerator.Summary summary =
                io.github.fv2j3dteam.loader.runtime.StressModGenerator.generate(outDir, jarsDir, loaderApiJar, TEST_MOD_COUNT);
        assertEquals(TEST_MOD_COUNT, summary.mods());
        assertEquals(TEST_MOD_COUNT * 5, summary.items());
        assertEquals(TEST_MOD_COUNT * 5, summary.blocks());
        assertEquals(TEST_MOD_COUNT, summary.creativeTabs());

        List<Path> jars;
        try (Stream<Path> s = Files.list(jarsDir)) {
            jars = s.filter(p -> p.toString().endsWith(".jar")).sorted().toList();
        }
        assertEquals(TEST_MOD_COUNT, jars.size(),
                "StressModGenerator must produce one JAR per mod");

        // 2. Run the real Fv2j3Loader against the real JARs.
        Fv2j3Registries.reset();
        Fv2j3Loader loader = new Fv2j3Loader(
                LoaderEnvironment.detectCurrent("1.12.2", "0.1.0", LoaderSide.CLIENT));
        loader.initialize(jarsDir);
        loader.start();
        try {
            // 3. Verify SPECIFIC registry IDs are present.
            //    testmod001:stress_item_1 must exist, not just "there
            //    are some items". The loader's SUCCESS log is not
            //    sufficient; we read the registry directly. The
            //    generator registers items/blocks with 1-based ids.
            for (int i = 1; i <= TEST_MOD_COUNT; i++) {
                String modId = String.format("testmod%03d", i);
                for (int j = 1; j <= 5; j++) {
                    String itemKey = modId + ":stress_item_" + j;
                    String blockKey = modId + ":stress_block_" + j;
                    assertNotNull(Fv2j3Registries.getItem(modId, "stress_item_" + j),
                            "missing item: " + itemKey);
                    assertNotNull(Fv2j3Registries.getBlock(modId, "stress_block_" + j),
                            "missing block: " + blockKey);
                }
                assertNotNull(Fv2j3Registries.getCreativeTab(modId, "stress_tab"),
                        "missing creative tab for mod " + modId);
            }
            // 4. Aggregate counts must match exactly.
            assertEquals(TEST_MOD_COUNT * 5, Fv2j3Registries.itemCount(),
                    "item count must equal mods * itemsPerMod");
            assertEquals(TEST_MOD_COUNT * 5, Fv2j3Registries.blockCount(),
                    "block count must equal mods * blocksPerMod");
            assertEquals(TEST_MOD_COUNT, Fv2j3Registries.creativeTabCount(),
                    "creative tab count must equal mods");
        } finally {
            try { loader.stop(); } catch (Exception ignored) { }
            Fv2j3Registries.reset();
        }
    }

    /** Find the loader-api JAR on Gradle's runtime classpath. */
    private static Path locateLoaderApiJar() {
        Path buildRoot = locateBuildRoot();
        Path libs = buildRoot.resolve("loader-api/build/libs");
        if (!Files.isDirectory(libs)) {
            throw new IllegalStateException("loader-api libs dir missing: " + libs);
        }
        try (Stream<Path> s = Files.list(libs)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().contains("sources"))
                    .filter(p -> !p.getFileName().toString().contains("javadoc"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("loader-api JAR not found in " + libs));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Path locateBuildRoot() {
        String cwd = System.getProperty("user.dir");
        Path cwdPath = Path.of(cwd);
        // The cwd for :loader-core:test is loader-core/, so build root
        // is the parent.
        for (Path candidate : List.of(cwdPath, cwdPath.getParent(),
                cwdPath.getParent() == null ? null : cwdPath.getParent().getParent())) {
            if (candidate != null && Files.isDirectory(candidate.resolve("loader-api"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("build root not found (cwd=" + cwd + ")");
    }
}
