package com.fv2j3test;

import com.fv2j3.api.Mod;
import com.fv2j3.api.ModContext;
import com.fv2j3.api.ModDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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
}
