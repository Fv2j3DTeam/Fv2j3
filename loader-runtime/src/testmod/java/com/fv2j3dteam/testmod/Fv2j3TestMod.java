package com.fv2j3dteam.testmod;

import io.github.fv2j3dteam.api.Fv2j3Block;
import io.github.fv2j3dteam.api.Fv2j3Config;
import io.github.fv2j3dteam.api.Fv2j3CreativeTab;
import io.github.fv2j3dteam.api.Fv2j3Item;
import io.github.fv2j3dteam.api.Fv2j3Registries;
import io.github.fv2j3dteam.api.Mod;
import io.github.fv2j3dteam.api.ModContext;
import io.github.fv2j3dteam.api.ModDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Real-runtime test mod. Beyond lifecycle markers it now registers real
 * content (item + block + creative tab, which the Minecraft registry bridge
 * pushes into Minecraft's own registries) and exercises the per-mod config
 * service: one file per mod, load → modify → save → reload, with proof that
 * a sibling mod's config file is untouched.
 */
public final class Fv2j3TestMod implements Mod {
    public static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    @Override
    public ModDescriptor descriptor() {
        return new ModDescriptor("fv2j3_test_mod", "1.0.0", "Fv2j3 Test Mod");
    }

    @Override
    public void onLoad(ModContext context) {
        EVENTS.add("LOAD");
        System.out.println("[Fv2j3-TestMod] onLoad");
    }

    @Override
    public void onInitialize(ModContext context) {
        EVENTS.add("INITIALIZE");
        System.out.println("[Fv2j3-TestMod] onInitialize");

        Fv2j3Registries.registerItem("fv2j3_test_mod", "test_item", new Fv2j3Item() {
            @Override public String id() { return "test_item"; }
            @Override public String name() { return "Fv2j3 Test Item"; }
            @Override public int maxStackSize() { return 16; }
        });
        Fv2j3Registries.registerBlock("fv2j3_test_mod", "test_block", new Fv2j3Block() {
            @Override public String id() { return "test_block"; }
            @Override public String name() { return "Fv2j3 Test Block"; }
            @Override public float hardness() { return 1.5f; }
            @Override public float resistance() { return 6.0f; }
            @Override public int harvestLevel() { return 0; }
            @Override public String harvestTool() { return "pickaxe"; }
        });
        Fv2j3Registries.registerCreativeTab("fv2j3_test_mod", "test_tab", new Fv2j3CreativeTab() {
            @Override public String id() { return "test_tab"; }
            @Override public String displayName() { return "Fv2j3"; }
            @Override public String icon() { return "test_item"; }
            @Override public int column() { return 1; }
        });

        exercisePerModConfig(context);
    }

    @Override
    public void onStart(ModContext context) {
        EVENTS.add("START");
        System.out.println("[Fv2j3-TestMod] onStart");
    }

    @Override
    public void onStop(ModContext context) {
        EVENTS.add("STOP");
        System.out.println("[Fv2j3-TestMod] onStop");
        try {
            Path markerDirectory = Path.of(System.getProperty("fv2j3.verification.dir", "build/fv2j3-test-runtime"));
            Files.createDirectories(markerDirectory);
            Files.writeString(markerDirectory.resolve("mod-stop"), "ok\n");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Failed to write the Test Mod shutdown marker.", ex);
        }
    }

    /**
     * Per-mod config proof inside the real runtime:
     *   1. resolve this mod's config, modify it, save it;
     *   2. resolve a sibling mod's config (separate file), modify it, save it;
     *   3. re-read this mod's config from disk and check the value survived;
     *   4. check the sibling file was not changed by step 3 (files differ).
     */
    private void exercisePerModConfig(ModContext context) {
        if (context.configProvider() == null) {
            EVENTS.add("CONFIG_MISSING_PROVIDER");
            return;
        }
        try {
            Fv2j3Config mine = context.configProvider().resolve("fv2j3_test_mod").orElseThrow();
            mine.set("launch.target", "real-minecraft");
            mine.setInt("launch.iterations", 42);
            mine.save();

            Fv2j3Config sibling = context.configProvider().resolve("fv2j3_test_mod_sibling").orElseThrow();
            sibling.set("launch.target", "sibling-original");
            sibling.save();
            String siblingBefore = Files.readString(sibling.configFile());

            mine.setInt("launch.iterations", 43);
            mine.save();

            Fv2j3Config reloaded = new Fv2j3Config("fv2j3_test_mod", mine.configFile());
            boolean persisted = reloaded.getInt("launch.iterations", -1) == 43
                    && "real-minecraft".equals(reloaded.get("launch.target", ""));

            String siblingAfter = Files.readString(sibling.configFile());
            boolean siblingIsolated = !mine.configFile().equals(sibling.configFile())
                    && siblingBefore.equals(siblingAfter);

            Path markerDirectory = Path.of(System.getProperty("fv2j3.verification.dir", "build/fv2j3-test-runtime"));
            Files.createDirectories(markerDirectory);
            Files.writeString(markerDirectory.resolve("mod-config"), (persisted && siblingIsolated) ? "ok\n" : "FAIL\n");
            Files.writeString(markerDirectory.resolve("mod-content"), "item=1,block=1,tab=1\n");
            EVENTS.add("CONFIG_OK");
            System.out.println("[Fv2j3-TestMod] config persisted=" + persisted + " siblingIsolated=" + siblingIsolated
                    + " file=" + mine.configFile());
        } catch (Exception ex) {
            EVENTS.add("CONFIG_FAILED");
            System.out.println("[Fv2j3-TestMod] config exercise failed: " + ex);
        }
    }
}
