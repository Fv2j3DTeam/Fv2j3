package io.github.fv2j3dteam.loader.core;

import io.github.fv2j3dteam.api.Fv2j3Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ModConfigRegistryTest {

    @Test
    void returnsEmptyForNullOrUnsafeId(@TempDir Path tmp) {
        ModConfigRegistry registry = new ModConfigRegistry(tmp);
        assertTrue(registry.resolve(null).isEmpty());
        // Path-traversal attempts: a mod id with ".." or "/" must be rejected.
        assertTrue(registry.resolve("../escape").isEmpty());
        assertTrue(registry.resolve("mod/evil").isEmpty());
        assertTrue(registry.resolve("").isEmpty());
    }

    @Test
    void cachesByModId(@TempDir Path tmp) {
        ModConfigRegistry registry = new ModConfigRegistry(tmp);
        Optional<Fv2j3Config> a1 = registry.resolve("alpha");
        Optional<Fv2j3Config> a2 = registry.resolve("alpha");
        assertTrue(a1.isPresent());
        assertSame(a1.get(), a2.get(), "second resolve must return the cached instance");
    }

    @Test
    void createsPerModFileOnDisk(@TempDir Path tmp) {
        ModConfigRegistry registry = new ModConfigRegistry(tmp);
        registry.resolve("alpha").orElseThrow().setInt("v", 1);
        registry.resolve("beta").orElseThrow().setInt("v", 2);
        registry.saveAll();
        assertTrue(Files.exists(tmp.resolve("alpha.properties")));
        assertTrue(Files.exists(tmp.resolve("beta.properties")));
    }

    @Test
    void saveAllPersistsOnlyDirtyConfigs(@TempDir Path tmp) throws IOException {
        ModConfigRegistry registry = new ModConfigRegistry(tmp);
        Fv2j3Config a = registry.resolve("alpha").orElseThrow();
        a.setInt("v", 42);
        a.save();
        long aSizeBefore = Files.size(tmp.resolve("alpha.properties"));
        // No change to A; B is dirty.
        registry.resolve("beta").orElseThrow().setInt("v", 7);
        registry.saveAll();
        long aSizeAfter = Files.size(tmp.resolve("alpha.properties"));
        assertEquals(aSizeBefore, aSizeAfter, "alpha was clean; file must not be rewritten");
    }

    @Test
    void fileSurvivesProcess(@TempDir Path tmp) throws IOException {
        ModConfigRegistry registry = new ModConfigRegistry(tmp);
        Fv2j3Config a = registry.resolve("alpha").orElseThrow();
        a.set("name", "hello");
        a.save();
        // New registry, same dir, same mod id: must return the persisted value.
        ModConfigRegistry second = new ModConfigRegistry(tmp);
        Fv2j3Config reloaded = second.resolve("alpha").orElseThrow();
        assertEquals("hello", reloaded.get("name", ""));
    }
}
