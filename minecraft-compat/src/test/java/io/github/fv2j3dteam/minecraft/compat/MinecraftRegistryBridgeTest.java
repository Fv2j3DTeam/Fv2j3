package io.github.fv2j3dteam.minecraft.compat;

import io.github.fv2j3dteam.api.Fv2j3Block;
import io.github.fv2j3dteam.api.Fv2j3CreativeTab;
import io.github.fv2j3dteam.api.Fv2j3Item;
import io.github.fv2j3dteam.api.Fv2j3Registries;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real registry bridge against the real vanilla Minecraft 1.12.2
 * jar (build/minecraft-runtime), in the same order the real launch uses:
 * patch Bootstrap.register and RUN it (vanilla registration), then patch
 * CreativeTabs, then push mod content through RegistryNamespaced.register
 * and read it back out of Minecraft's own registry objects.
 */
class MinecraftRegistryBridgeTest {

    @TempDir
    static Path verificationDir;

    static URLClassLoaderExt mcLoader;

    /**
     * Mirrors the real launch loader: every class is defined from raw bytes
     * without the jar's CodeSource, so patched vanilla classes do not hit
     * package signer mismatches (the real client loader does the same).
     */
    static final class URLClassLoaderExt extends java.net.URLClassLoader {
        URLClassLoaderExt(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            URL resource = findResource(name.replace('.', '/') + ".class");
            if (resource == null) {
                throw new ClassNotFoundException(name);
            }
            try (var in = resource.openStream()) {
                byte[] bytes = in.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }
        }

        public Class<?> defineGeneratedClass(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    @BeforeAll
    static void prepare() throws IOException {
        Path runtimeHome = locateRuntimeHome();
        List<URL> urls = new ArrayList<>();
        try (Stream<Path> jars = Files.walk(runtimeHome)) {
            jars.filter(p -> p.toString().endsWith(".jar")).forEach(p -> {
                try {
                    urls.add(p.toUri().toURL());
                } catch (Exception ignored) {
                }
            });
        }
        assertTrue(urls.size() >= 10, "expected the prepared Minecraft runtime jars, found " + urls.size());
        mcLoader = new URLClassLoaderExt(urls.toArray(new URL[0]), MinecraftRegistryBridgeTest.class.getClassLoader());
        MinecraftRegistryBridge.attachClassLoader(mcLoader::defineGeneratedClass);
        System.setProperty("fv2j3.verification.dir", verificationDir.toString());

        Fv2j3Registries.reset();
        for (int i = 1; i <= 3; i++) {
            final int index = i;
            Fv2j3Registries.registerItem("bridgemod", "test_item_" + index, new Fv2j3Item() {
                @Override public String id() { return "test_item_" + index; }
                @Override public String name() { return "Bridge Item " + index; }
                @Override public int maxStackSize() { return 64; }
            });
        }
        for (int i = 1; i <= 2; i++) {
            final int index = i;
            Fv2j3Registries.registerBlock("bridgemod", "test_block_" + index, new Fv2j3Block() {
                @Override public String id() { return "test_block_" + index; }
                @Override public String name() { return "Bridge Block " + index; }
                @Override public float hardness() { return 1.0f; }
                @Override public float resistance() { return 5.0f; }
                @Override public int harvestLevel() { return 0; }
                @Override public String harvestTool() { return "pickaxe"; }
            });
        }
        Fv2j3Registries.registerCreativeTab("bridgemod", "test_tab", new Fv2j3CreativeTab() {
            @Override public String id() { return "test_tab"; }
            @Override public String displayName() { return "Bridge"; }
            @Override public String icon() { return "test_item_1"; }
            @Override public int column() { return 1; }
        });
    }

    @AfterAll
    static void cleanup() throws Exception {
        System.clearProperty("fv2j3.verification.dir");
        Fv2j3Registries.reset();
        if (mcLoader != null) {
            mcLoader.close();
        }
    }

    private static Path locateRuntimeHome() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("build/minecraft-runtime/versions/1.12.2/1.12.2.jar");
            if (Files.exists(candidate)) {
                return dir.resolve("build/minecraft-runtime");
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Minecraft runtime not found: run ./gradlew prepareMinecraftRuntime first.");
    }

    private static byte[] classBytes(String internalName) throws IOException {
        try (var in = mcLoader.getResourceAsStream(internalName + ".class")) {
            assertNotNull(in, "vanilla class not found: " + internalName);
            return in.readAllBytes();
        }
    }

    @Test
    void bridgeEndToEndAgainstVanillaJar() throws Exception {
        // 1. Define patched Bootstrap + CreativeTabs (the real launch loads
        //    both through the patched classloader before Minecraft main).
        byte[] patchedBootstrap = MinecraftRegistryBridge.transform("ni", classBytes("ni"));
        assertTrue(new String(patchedBootstrap, java.nio.charset.StandardCharsets.ISO_8859_1)
                .contains("MinecraftRegistryBridge"), "patched Bootstrap must call the bridge");
        mcLoader.defineGeneratedClass("ni", patchedBootstrap);
        byte[] patchedTabs = MinecraftRegistryBridge.transform("ahp", classBytes("ahp"));
        Class<?> tabClass = mcLoader.defineGeneratedClass("ahp", patchedTabs);

        // 2. Run Bootstrap.register(): vanilla registration completes, then the
        //    injected flush pushes loader content into the real registries —
        //    exactly the sequence of a real launch (loader runs before Minecraft).
        Method register = mcLoader.loadClass("ni").getMethod("c");
        register.invoke(null);

        MinecraftRegistryBridge.FlushResult result = MinecraftRegistryBridge.lastResult;
        assertNotNull(result, "the patched Bootstrap.register must have flushed the bridge");
        assertEquals(3, result.itemsRegistered, "all test items must be registered");
        assertEquals(2, result.blocksRegistered, "all test blocks must be registered");
        assertEquals(1, result.tabsCreated, "one tab for the test namespace");
        assertTrue(result.verifiedItems >= 3, "every mod item must be readable from Item.REGISTRY");
        assertTrue(result.verifiedBlocks >= 2, "every mod block must be readable from Block.REGISTRY");
        assertTrue(result.itemIdSamples.containsKey("bridgemod:test_item_1"));
        int itemId = result.itemIdSamples.get("bridgemod:test_item_1");
        assertTrue(itemId >= 10_000, "mod items get fresh ids above the vanilla range: " + itemId);

        // 3. The tab array must have grown past the 12 vanilla slots and hold
        //    the mod tab (label = namespace).
        Field arrayField = tabClass.getDeclaredField("a");
        arrayField.setAccessible(true);
        Object tabArray = arrayField.get(null);
        int length = java.lang.reflect.Array.getLength(tabArray);
        assertTrue(length >= 13, "tab array must have grown past the 12 vanilla slots, got " + length);
        assertTrue(result.tabArrayNonNull >= 13, "creative tab array must contain vanilla + mod tabs");
        assertTrue(result.tabLabels.contains("bridgemod"), "mod tab must be visible by label");

        // 4. Independent read-back through Minecraft's own registry objects.
        Class<?> blockClass = mcLoader.loadClass("aow");
        Field registryField = blockClass.getDeclaredField("h");
        registryField.setAccessible(true);
        Object blockRegistry = registryField.get(null);
        Class<?> rlClass = mcLoader.loadClass("nf");
        Object rl = rlClass.getConstructor(String.class, String.class).newInstance("bridgemod", "test_block_1");
        Method getObject = mcLoader.loadClass("fh").getMethod("c", Object.class);
        assertNotNull(getObject.invoke(blockRegistry, rl), "Block registry must contain bridgemod:test_block_1");

        // 5. Vanilla content must still be intact beside the mod content.
        Object vanillaStoneItem = getObject.invoke(blockRegistry,
                rlClass.getConstructor(String.class, String.class).newInstance("minecraft", "stone"));
        assertNotNull(vanillaStoneItem, "vanilla stone must survive mod registration");

        // 6. The verification report must exist and record Minecraft-side facts.
        Path report = verificationDir.resolve("minecraft-registry.json");
        assertTrue(Files.exists(report), "verification report must be written");
        String json = Files.readString(report);
        assertTrue(json.contains("\"verifiedItemsInMinecraftRegistry\": " + result.verifiedItems));
        assertTrue(json.contains("\"blocksRegisteredInMinecraft\": " + result.blocksRegistered));
    }
}
