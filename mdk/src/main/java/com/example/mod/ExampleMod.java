package com.example.mod;

import com.fv2j3.api.Mod;
import com.fv2j3.api.ModContext;
import com.fv2j3.api.ModDescriptor;

public final class ExampleMod implements Mod {
    private static final ModDescriptor DESCRIPTOR = new ModDescriptor(
            "example",
            "1.0.0",
            "Example Mod",
            "Example Fv2j3 mod built with the MDK.",
            java.util.List.of("Example"),
            "MIT",
            java.util.List.of(),
            java.util.List.of(),
            ">=0.1.0",
            "com.example.mod.ExampleMod"
    );

    @Override
    public ModDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public void onLoad(ModContext context) {
        if (context.logger() != null) {
            context.logger().info("Example Mod loaded.");
        }
    }
}
