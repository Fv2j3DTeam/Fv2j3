package com.fv2j3.loader.core;

import static org.junit.jupiter.api.Assertions.*;

import com.fv2j3.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistryAndEventTest {

    @BeforeEach
    void reset() {
        Fv2j3Registries.reset();
        Fv2j3Events.reset();
    }

    @Test
    void registerSingleItem() {
        Fv2j3Item item = new Fv2j3Item() {
            public String id() { return "test_item"; }
            public String name() { return "Test"; }
            public int maxStackSize() { return 64; }
        };
        Fv2j3Registries.registerItem("testmod", "test_item", item);
        assertEquals(1, Fv2j3Registries.itemCount());
        assertSame(item, Fv2j3Registries.getItem("testmod", "test_item"));
    }

    @Test
    void registerDuplicateItemFails() {
        Fv2j3Item item = new Fv2j3Item() {
            public String id() { return "test"; }
            public String name() { return "Test"; }
            public int maxStackSize() { return 64; }
        };
        Fv2j3Registries.registerItem("testmod", "test", item);
        assertThrows(IllegalStateException.class,
            () -> Fv2j3Registries.registerItem("testmod", "test", item));
    }

    @Test
    void registerManyItems() {
        for (int i = 0; i < 1000; i++) {
            final int idx = i;
            Fv2j3Item item = new Fv2j3Item() {
                public String id() { return "item_" + idx; }
                public String name() { return "Item " + idx; }
                public int maxStackSize() { return 64; }
            };
            Fv2j3Registries.registerItem("mod" + (i / 100), "item_" + i, item);
        }
        assertEquals(1000, Fv2j3Registries.itemCount());
    }

    @Test
    void registerSingleBlock() {
        Fv2j3Block block = new Fv2j3Block() {
            public String id() { return "test_block"; }
            public String name() { return "Test Block"; }
            public float hardness() { return 1.0f; }
            public float resistance() { return 5.0f; }
            public int harvestLevel() { return 0; }
            public String harvestTool() { return "pickaxe"; }
        };
        Fv2j3Registries.registerBlock("testmod", "test_block", block);
        assertEquals(1, Fv2j3Registries.blockCount());
        assertSame(block, Fv2j3Registries.getBlock("testmod", "test_block"));
    }

    @Test
    void registerDuplicateBlockFails() {
        Fv2j3Block block = new Fv2j3Block() {
            public String id() { return "test"; }
            public String name() { return "Test"; }
            public float hardness() { return 1.0f; }
            public float resistance() { return 5.0f; }
            public int harvestLevel() { return 0; }
            public String harvestTool() { return "pickaxe"; }
        };
        Fv2j3Registries.registerBlock("testmod", "test", block);
        assertThrows(IllegalStateException.class,
            () -> Fv2j3Registries.registerBlock("testmod", "test", block));
    }

    @Test
    void registerManyBlocks() {
        for (int i = 0; i < 1000; i++) {
            final int idx = i;
            Fv2j3Block block = new Fv2j3Block() {
                public String id() { return "block_" + idx; }
                public String name() { return "Block " + idx; }
                public float hardness() { return 1.0f; }
                public float resistance() { return 5.0f; }
                public int harvestLevel() { return 0; }
                public String harvestTool() { return "pickaxe"; }
            };
            Fv2j3Registries.registerBlock("mod" + (i / 100), "block_" + i, block);
        }
        assertEquals(1000, Fv2j3Registries.blockCount());
    }

    @Test
    void registerSingleCreativeTab() {
        Fv2j3CreativeTab tab = new Fv2j3CreativeTab() {
            public String id() { return "test_tab"; }
            public String displayName() { return "Test Tab"; }
            public String icon() { return "test_item"; }
            public int column() { return 0; }
        };
        Fv2j3Registries.registerCreativeTab("testmod", "test_tab", tab);
        assertEquals(1, Fv2j3Registries.creativeTabCount());
        assertSame(tab, Fv2j3Registries.getCreativeTab("testmod", "test_tab"));
    }

    @Test
    void registerDuplicateCreativeTabFails() {
        Fv2j3CreativeTab tab = new Fv2j3CreativeTab() {
            public String id() { return "test"; }
            public String displayName() { return "Test"; }
            public String icon() { return "icon"; }
            public int column() { return 0; }
        };
        Fv2j3Registries.registerCreativeTab("testmod", "test", tab);
        assertThrows(IllegalStateException.class,
            () -> Fv2j3Registries.registerCreativeTab("testmod", "test", tab));
    }

    @Test
    void namespacedItems() {
        Fv2j3Item item1 = new Fv2j3Item() {
            public String id() { return "shared_name"; }
            public String name() { return "A"; }
            public int maxStackSize() { return 64; }
        };
        Fv2j3Item item2 = new Fv2j3Item() {
            public String id() { return "shared_name"; }
            public String name() { return "B"; }
            public int maxStackSize() { return 64; }
        };
        Fv2j3Registries.registerItem("mod_a", "shared_name", item1);
        Fv2j3Registries.registerItem("mod_b", "shared_name", item2);
        assertNotSame(
            Fv2j3Registries.getItem("mod_a", "shared_name"),
            Fv2j3Registries.getItem("mod_b", "shared_name")
        );
    }

    @Test
    void resetClearsAllRegistries() {
        Fv2j3Item item = new Fv2j3Item() {
            public String id() { return "x"; }
            public String name() { return "X"; }
            public int maxStackSize() { return 64; }
        };
        Fv2j3Block block = new Fv2j3Block() {
            public String id() { return "x"; }
            public String name() { return "X"; }
            public float hardness() { return 1.0f; }
            public float resistance() { return 5.0f; }
            public int harvestLevel() { return 0; }
            public String harvestTool() { return "pickaxe"; }
        };
        Fv2j3CreativeTab tab = new Fv2j3CreativeTab() {
            public String id() { return "x"; }
            public String displayName() { return "X"; }
            public String icon() { return "x"; }
            public int column() { return 0; }
        };
        Fv2j3Registries.registerItem("m", "x", item);
        Fv2j3Registries.registerBlock("m", "x", block);
        Fv2j3Registries.registerCreativeTab("m", "x", tab);
        Fv2j3Registries.reset();
        assertEquals(0, Fv2j3Registries.itemCount());
        assertEquals(0, Fv2j3Registries.blockCount());
        assertEquals(0, Fv2j3Registries.creativeTabCount());
    }

    @Test
    void subscribeToClientStartingEvent() {
        final boolean[] fired = {false};
        Fv2j3Events.onClientStarting(e -> fired[0] = true);
        Fv2j3Events.fireClientStarting(new Fv2j3Event("test", "client_starting"));
        assertTrue(fired[0]);
    }

    @Test
    void subscribeToAllEventTypes() {
        Fv2j3Events.onClientStarting(e -> {});
        Fv2j3Events.onClientStarted(e -> {});
        Fv2j3Events.onClientStopping(e -> {});
        Fv2j3Events.onServerStarting(e -> {});
        Fv2j3Events.onServerStarted(e -> {});
        Fv2j3Events.onServerStopping(e -> {});
        Fv2j3Events.onRegistryBuild(e -> {});
        assertEquals(1, Fv2j3Events.clientStartingCount());
        assertEquals(1, Fv2j3Events.clientStartedCount());
        assertEquals(1, Fv2j3Events.clientStoppingCount());
        assertEquals(1, Fv2j3Events.serverStartingCount());
        assertEquals(1, Fv2j3Events.serverStartedCount());
        assertEquals(1, Fv2j3Events.serverStoppingCount());
        assertEquals(1, Fv2j3Events.registryBuildCount());
    }

    @Test
    void multipleListenersFire() {
        final int[] count = {0};
        Fv2j3Events.onClientStarting(e -> count[0]++);
        Fv2j3Events.onClientStarting(e -> count[0]++);
        Fv2j3Events.onClientStarting(e -> count[0]++);
        Fv2j3Events.fireClientStarting(new Fv2j3Event("test", "starting"));
        assertEquals(3, count[0]);
    }

    @Test
    void listenerExceptionsDoNotStopOtherListeners() {
        final int[] count = {0};
        Fv2j3Events.onClientStarting(e -> { throw new RuntimeException("oops"); });
        Fv2j3Events.onClientStarting(e -> count[0]++);
        Fv2j3Events.fireClientStarting(new Fv2j3Event("test", "starting"));
        assertEquals(1, count[0]);
    }

    @Test
    void fv2j3EventProperties() {
        Fv2j3Event e = new Fv2j3Event("mymod", "starting");
        assertEquals("mymod", e.modId());
        assertEquals("starting", e.phase());
        assertTrue(e.timestamp() > 0);
    }

    @Test
    void configRoundTrip() throws Exception {
        java.nio.file.Path tempConfig = java.nio.file.Files.createTempFile("test", ".cfg");
        Fv2j3Config config = new Fv2j3Config("testmod", tempConfig);
        config.set("key1", "value1");
        config.setInt("key2", 42);
        config.setBool("key3", true);
        config.save();
        Fv2j3Config loaded = new Fv2j3Config("testmod", tempConfig);
        assertEquals("value1", loaded.get("key1", ""));
        assertEquals(42, loaded.getInt("key2", 0));
        assertTrue(loaded.getBool("key3", false));
        java.nio.file.Files.deleteIfExists(tempConfig);
    }

    @Test
    void configDefaultValues() throws Exception {
        java.nio.file.Path tempConfig = java.nio.file.Files.createTempFile("test", ".cfg");
        java.nio.file.Files.deleteIfExists(tempConfig);
        Fv2j3Config config = new Fv2j3Config("testmod", tempConfig);
        assertEquals("default", config.get("missing", "default"));
        assertEquals(99, config.getInt("missing", 99));
        assertFalse(config.getBool("missing", false));
        java.nio.file.Files.deleteIfExists(tempConfig);
    }
}
