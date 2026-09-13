package io.github.fv2j3dteam.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class Fv2j3Registries {
    private static final Map<String, Fv2j3Item> ITEMS = new LinkedHashMap<>();
    private static final Map<String, Fv2j3Block> BLOCKS = new LinkedHashMap<>();
    private static final Map<String, Fv2j3CreativeTab> CREATIVE_TABS = new LinkedHashMap<>();

    private Fv2j3Registries() {}

    public static void registerItem(String namespace, String id, Fv2j3Item item) {
        String key = namespace + ":" + id;
        if (ITEMS.containsKey(key)) {
            throw new IllegalStateException("Duplicate item ID: " + key);
        }
        ITEMS.put(key, Objects.requireNonNull(item, "item"));
    }

    public static Fv2j3Item getItem(String namespace, String id) {
        return ITEMS.get(namespace + ":" + id);
    }

    public static Map<String, Fv2j3Item> getAllItems() {
        return Map.copyOf(ITEMS);
    }

    public static void registerBlock(String namespace, String id, Fv2j3Block block) {
        String key = namespace + ":" + id;
        if (BLOCKS.containsKey(key)) {
            throw new IllegalStateException("Duplicate block ID: " + key);
        }
        BLOCKS.put(key, Objects.requireNonNull(block, "block"));
    }

    public static Fv2j3Block getBlock(String namespace, String id) {
        return BLOCKS.get(namespace + ":" + id);
    }

    public static Map<String, Fv2j3Block> getAllBlocks() {
        return Map.copyOf(BLOCKS);
    }

    public static void registerCreativeTab(String namespace, String id, Fv2j3CreativeTab tab) {
        String key = namespace + ":" + id;
        if (CREATIVE_TABS.containsKey(key)) {
            throw new IllegalStateException("Duplicate creative tab ID: " + key);
        }
        CREATIVE_TABS.put(key, Objects.requireNonNull(tab, "tab"));
    }

    public static Fv2j3CreativeTab getCreativeTab(String namespace, String id) {
        return CREATIVE_TABS.get(namespace + ":" + id);
    }

    public static Map<String, Fv2j3CreativeTab> getAllCreativeTabs() {
        return Map.copyOf(CREATIVE_TABS);
    }

    public static int itemCount() { return ITEMS.size(); }
    public static int blockCount() { return BLOCKS.size(); }
    public static int creativeTabCount() { return CREATIVE_TABS.size(); }

    public static void reset() {
        ITEMS.clear();
        BLOCKS.clear();
        CREATIVE_TABS.clear();
    }
}
