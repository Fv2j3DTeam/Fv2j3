package io.github.fv2j3dteam.loader.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModLoaderRuntimeTest {
    @Test
    void createsExpectedEnvironment() {
        ModLoaderRuntime runtime = ModLoaderRuntime.createDefault();

        assertEquals("1.12.2", runtime.minecraftVersion());
        assertEquals(26, runtime.javaTargetVersion());
        assertTrue(runtime.platformSummary().contains("Minecraft 1.12.2"));
    }
}
