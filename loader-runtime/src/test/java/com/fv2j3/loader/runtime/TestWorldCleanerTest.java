package com.fv2j3.loader.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TestWorldCleanerTest {

    @Test
    void cleansEightWorldsWithRealisticStructure(@TempDir Path tmp) throws Exception {
        Path runtime = tmp.resolve("minecraft-runtime");
        Path saves = runtime.resolve("saves");
        Path mods = runtime.resolve("mods");
        Path config = runtime.resolve("config");
        Files.createDirectories(saves);
        Files.createDirectories(mods);
        Files.createDirectories(config);

        // Plant 8 worlds with realistic MC world contents.
        for (int i = 0; i < 8; i++) {
            Path world = saves.resolve("World" + i);
            makeWorld(world);
        }
        // Plant critical files that must NOT be deleted.
        Files.writeString(mods.resolve("important.mod"), "critical");
        Files.writeString(config.resolve("important.cfg"), "critical");
        Path loose = saves.resolve("loose_root_file.txt");
        Files.writeString(loose, "loose file content");

        TestWorldCleaner.Result r = TestWorldCleaner.clean(runtime, tmp, "/nonexistent-home");

        assertEquals(8, r.worldsBefore, "must find 8 worlds before");
        assertEquals(0, r.worldsAfter, "all worlds must be deleted");
        assertEquals(8, r.deletedWorlds);
        assertTrue(r.success, "result.success must be true");
        assertTrue(r.skipped.isEmpty(), "no skips expected; got " + r.skipped);
        // Loose file should also be gone.
        assertFalse(Files.exists(loose), "loose files inside saves/ must be deleted");
        // Critical files survived.
        assertTrue(Files.exists(mods.resolve("important.mod")), "mods/important.mod must survive");
        assertTrue(Files.exists(config.resolve("important.cfg")), "config/important.cfg must survive");
        // The saves/ directory itself still exists (the cleaner must
        // never delete the parent).
        assertTrue(Files.isDirectory(saves), "saves/ parent must survive");
    }

    @Test
    void cleansThreeWorldsWithSpacesInNames(@TempDir Path tmp) throws Exception {
        Path runtime = tmp.resolve("minecraft-runtime");
        Path saves = runtime.resolve("saves");
        Files.createDirectories(saves);
        for (String name : new String[]{"TestWorld1", "TestWorld2", "Weird Name"}) {
            makeWorld(saves.resolve(name));
        }
        TestWorldCleaner.Result r = TestWorldCleaner.clean(runtime, tmp, "/nonexistent-home");
        assertEquals(3, r.worldsBefore);
        assertEquals(0, r.worldsAfter);
        assertTrue(r.success);
    }

    @Test
    void emptySavesIsAZeroWorldNoOp(@TempDir Path tmp) throws Exception {
        Path runtime = tmp.resolve("minecraft-runtime");
        Path saves = runtime.resolve("saves");
        Files.createDirectories(saves);
        TestWorldCleaner.Result r = TestWorldCleaner.clean(runtime, tmp, "/nonexistent-home");
        assertEquals(0, r.worldsBefore);
        assertEquals(0, r.worldsAfter);
        assertTrue(r.success);
    }

    @Test
    void missingSavesDirectoryIsOk(@TempDir Path tmp) throws Exception {
        Path runtime = tmp.resolve("minecraft-runtime");
        Files.createDirectories(runtime);
        // No saves/ subdir exists.
        TestWorldCleaner.Result r = TestWorldCleaner.clean(runtime, tmp, "/nonexistent-home");
        assertEquals(0, r.worldsBefore);
        assertEquals(0, r.worldsAfter);
        assertTrue(r.success);
    }

    @Test
    void refusesToCleanOutsideProjectTree(@TempDir Path tmp) throws Exception {
        Path outside = tmp.resolve("outside-runtime");
        Files.createDirectories(outside);
        SecurityException ex = assertThrows(SecurityException.class,
                () -> TestWorldCleaner.clean(outside, tmp.resolve("project"), "/nonexistent-home"));
        assertTrue(ex.getMessage().contains("outside project tree"), ex.getMessage());
    }

    @Test
    void refusesToCleanUserHomeMinecraft(@TempDir Path tmp) throws Exception {
        // Realistic scenario: the runtime IS the user's normal
        // ~/.minecraft (e.g. operator set the wrong fv2j3.minecraft.home).
        // Build a "project" subdir as the project root and a separate
        // "fakeHome" as user.home so the runtime lives outside the
        // project tree but matches the user-home path.
        Path fakeHome = Files.createTempDirectory("cleaner-fake-home-");
        Path userMc = fakeHome.resolve(".minecraft");
        Files.createDirectories(userMc.resolve("saves"));
        Files.writeString(userMc.resolve("saves").resolve("PlayerSave"), "data");

        Path projectRoot = tmp.resolve("project");
        Files.createDirectories(projectRoot);
        try {
            SecurityException ex = assertThrows(SecurityException.class,
                    () -> TestWorldCleaner.clean(userMc, projectRoot, fakeHome.toString()));
            // Either message is acceptable: outside project tree OR
            // user's normal Minecraft install. Both are valid safety
            // signals.
            String msg = ex.getMessage();
            assertTrue(msg.contains("user's normal Minecraft install")
                            || msg.contains("outside project tree"),
                    "expected a safety message; got: " + msg);
            // And critically: the save MUST still exist (we refused).
            assertTrue(Files.exists(userMc.resolve("saves").resolve("PlayerSave")),
                    "refusal must not have deleted the user's save");
        } finally {
            // Best-effort cleanup of the fake home dir.
            if (Files.exists(fakeHome)) {
                deleteRecursively(fakeHome);
            }
        }
    }

    @Test
    void refusesToCleanNonexistentRuntime(@TempDir Path tmp) {
        Path runtime = tmp.resolve("does-not-exist");
        assertThrows(SecurityException.class,
                () -> TestWorldCleaner.clean(runtime, tmp, "/nonexistent-home"));
    }

    @Test
    void refusesNullRuntime(@TempDir Path tmp) {
        assertThrows(SecurityException.class,
                () -> TestWorldCleaner.clean((Path) null, tmp, "/nonexistent-home"));
    }

    @Test
    void secondRunOnCleanStateReportsZeroWorlds(@TempDir Path tmp) throws Exception {
        Path runtime = tmp.resolve("minecraft-runtime");
        Path saves = runtime.resolve("saves");
        Files.createDirectories(saves);
        // Plant 5 worlds, clean, then clean again.
        for (int i = 0; i < 5; i++) {
            makeWorld(saves.resolve("World" + i));
        }
        TestWorldCleaner.Result first = TestWorldCleaner.clean(runtime, tmp, "/nonexistent-home");
        assertEquals(5, first.worldsBefore);
        assertEquals(0, first.worldsAfter);
        TestWorldCleaner.Result second = TestWorldCleaner.clean(runtime, tmp, "/nonexistent-home");
        assertEquals(0, second.worldsBefore, "second run on a clean tree must report 0 worlds");
        assertEquals(0, second.worldsAfter);
        assertTrue(second.success);
    }

    @Test
    void formatResultRendersAllFields(@TempDir Path tmp) throws Exception {
        Path runtime = tmp.resolve("minecraft-runtime");
        Path saves = runtime.resolve("saves");
        Files.createDirectories(saves);
        makeWorld(saves.resolve("W"));
        TestWorldCleaner.Result r = TestWorldCleaner.clean(runtime, tmp, "/nonexistent-home");
        String formatted = TestWorldCleaner.formatResult(r);
        assertTrue(formatted.contains("[Fv2j3 Test Environment]"));
        assertTrue(formatted.contains("Existing test worlds: 1"));
        assertTrue(formatted.contains("Save cleanup: SUCCESS"));
        assertTrue(formatted.contains("Remaining test worlds: 0"));
        assertTrue(formatted.contains(runtime.toString()));
    }

    /** Make a realistic MC world directory inside the given path. */
    private static void makeWorld(Path world) throws IOException {
        Files.createDirectories(world);
        for (String sub : new String[]{
                "region", "data", "datapacks/foo",
                "DIM-1/region", "DIM1/region",
                "playerdata", "stats", "advancements", "poi"
        }) {
            Files.createDirectories(world.resolve(sub));
        }
        Files.writeString(world.resolve("region/r.0.0.mca"), "region data");
        Files.writeString(world.resolve("session.lock"), "lock");
        Files.writeString(world.resolve("level.dat"), "level data");
        Files.writeString(world.resolve("level.dat_old"), "old level");
        Files.writeString(world.resolve("data/map_0.dat"), "map data");
        Files.writeString(world.resolve("playerdata/player.dat"), "player");
        Files.writeString(world.resolve("stats/player.json"), "{}");
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (Files.isDirectory(root)) {
            try (java.nio.file.DirectoryStream<Path> s = Files.newDirectoryStream(root)) {
                for (Path c : s) {
                    deleteRecursively(c);
                }
            }
        }
        Files.deleteIfExists(root);
    }
}
