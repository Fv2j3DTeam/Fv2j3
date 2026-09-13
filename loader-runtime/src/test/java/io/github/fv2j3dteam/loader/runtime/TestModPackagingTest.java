package io.github.fv2j3dteam.loader.runtime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class TestModPackagingTest {
    @Test
    void testModJarContainsRealMetadataAndEntrypoint() throws Exception {
        Path jarPath = Path.of("build", "libs", "fv2j3-test-mod.jar");
        assertTrue(Files.isRegularFile(jarPath), "Expected packaged mod jar at " + jarPath);

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            assertNotNull(jarFile.getJarEntry("META-INF/fv2j3.mod.json"));
            assertNotNull(jarFile.getJarEntry("com/fv2j3dteam/testmod/Fv2j3TestMod.class"));
        }
    }
}
