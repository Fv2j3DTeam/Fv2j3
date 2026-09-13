package io.github.fv2j3dteam.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Fv2j3ConfigTest {

    enum Difficulty { EASY, NORMAL, HARD }

    @Test
    void defaultsAreReturnedWhenKeyMissing(@TempDir Path tmp) {
        Fv2j3Config cfg = new Fv2j3Config("test_mod", tmp.resolve("test_mod.properties"));
        assertEquals("default", cfg.get("missing", "default"));
        assertEquals(42, cfg.getInt("missing", 42));
        assertEquals(42L, cfg.getLong("missing", 42L));
        assertEquals(1.5f, cfg.getFloat("missing", 1.5f), 1e-6f);
        assertEquals(2.5, cfg.getDouble("missing", 2.5), 1e-9);
        assertTrue(cfg.getBool("missing", true));
        assertEquals(Difficulty.NORMAL, cfg.getEnum("missing", Difficulty.class, Difficulty.NORMAL));
        assertEquals(List.of("a", "b"), cfg.getList("missing", List.of("a", "b")));
    }

    @Test
    void allPrimitiveTypesRoundTrip(@TempDir Path tmp) {
        Fv2j3Config cfg = new Fv2j3Config("test_mod", tmp.resolve("test_mod.properties"));
        cfg.set("name", "hello world");
        cfg.setInt("i", 7);
        cfg.setLong("l", 1_234_567_890_123L);
        cfg.setFloat("f", 0.5f);
        cfg.setDouble("d", 3.14159265);
        cfg.setBool("b", true);
        cfg.setEnum("diff", Difficulty.HARD);
        cfg.setList("tags", List.of("a", "b", "c"));
        cfg.save();
        assertTrue(Files.exists(tmp.resolve("test_mod.properties")));

        // Reload: a fresh config reading the same file must return the
        // same values, proving persistence + parsing are real (not stubs).
        Fv2j3Config reloaded = new Fv2j3Config("test_mod", tmp.resolve("test_mod.properties"));
        assertEquals("hello world", reloaded.get("name", ""));
        assertEquals(7, reloaded.getInt("i", 0));
        assertEquals(1_234_567_890_123L, reloaded.getLong("l", 0L));
        assertEquals(0.5f, reloaded.getFloat("f", 0f), 1e-6f);
        assertEquals(3.14159265, reloaded.getDouble("d", 0.0), 1e-9);
        assertTrue(reloaded.getBool("b", false));
        assertEquals(Difficulty.HARD, reloaded.getEnum("diff", Difficulty.class, Difficulty.EASY));
        assertEquals(List.of("a", "b", "c"), reloaded.getList("tags", List.of()));
    }

    @Test
    void malformedValueFallsBackToDefault(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("bad.properties");
        Files.writeString(file, "i=not_a_number\nb=maybe\ndiff=ELITE\n");
        Fv2j3Config cfg = new Fv2j3Config("bad", file);
        assertEquals(0, cfg.getInt("i", 0), "malformed int must fall back");
        assertFalse(cfg.getBool("b", false), "malformed bool must fall back");
        assertEquals(Difficulty.EASY,
                cfg.getEnum("diff", Difficulty.class, Difficulty.EASY),
                "unknown enum value must fall back");
    }

    @Test
    void enumLookupIsCaseInsensitive(@TempDir Path tmp) {
        Fv2j3Config cfg = new Fv2j3Config("test_mod", tmp.resolve("c.properties"));
        cfg.setEnum("diff", Difficulty.HARD);
        cfg.save();
        Fv2j3Config reloaded = new Fv2j3Config("test_mod", tmp.resolve("c.properties"));
        assertEquals(Difficulty.HARD, reloaded.getEnum("diff", Difficulty.class, Difficulty.EASY));
        // Manually rewrite the file in a different case.
        try {
            Files.writeString(tmp.resolve("c.properties"), "diff=hard\n");
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        Fv2j3Config caseInsensitive = new Fv2j3Config("test_mod", tmp.resolve("c.properties"));
        assertEquals(Difficulty.HARD,
                caseInsensitive.getEnum("diff", Difficulty.class, Difficulty.EASY));
    }

    @Test
    void listSkipsEmptyEntriesAndTrims(@TempDir Path tmp) {
        Fv2j3Config cfg = new Fv2j3Config("m", tmp.resolve("m.properties"));
        cfg.setList("tags", List.of("  a  ", "", "b", "  ", "c"));
        cfg.save();
        Fv2j3Config reloaded = new Fv2j3Config("m", tmp.resolve("m.properties"));
        assertEquals(List.of("a", "b", "c"), reloaded.getList("tags", List.of()));
    }

    @Test
    void dirtyFlagClearsOnSave(@TempDir Path tmp) {
        Fv2j3Config cfg = new Fv2j3Config("m", tmp.resolve("m.properties"));
        assertFalse(cfg.isDirty());
        cfg.setInt("i", 1);
        assertTrue(cfg.isDirty());
        cfg.save();
        assertFalse(cfg.isDirty());
    }

    @Test
    void perModIsolation(@TempDir Path tmp) {
        Fv2j3Config a = new Fv2j3Config("mod_a", tmp.resolve("mod_a.properties"));
        Fv2j3Config b = new Fv2j3Config("mod_b", tmp.resolve("mod_b.properties"));
        a.setInt("v", 1);
        b.setInt("v", 2);
        a.save();
        b.save();
        Fv2j3Config aReloaded = new Fv2j3Config("mod_a", tmp.resolve("mod_a.properties"));
        Fv2j3Config bReloaded = new Fv2j3Config("mod_b", tmp.resolve("mod_b.properties"));
        assertEquals(1, aReloaded.getInt("v", -1));
        assertEquals(2, bReloaded.getInt("v", -1));
        assertNotEquals(aReloaded.getInt("v", 0), bReloaded.getInt("v", 0));
    }
}
