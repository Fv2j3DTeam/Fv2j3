package com.fv2j3.minecraft.compat;

import java.util.Locale;

public final class MinecraftCompatibility {
    public static final String MINECRAFT_VERSION = "1.12.2";
    public static final String MINECRAFT_PACKAGE = "net.minecraft";
    public static final String Fv2J3_API_PACKAGE = "com.fv2j3.api";
    public static final String[] SUPPORTED_PLATFORMS = {"linux", "windows", "macos", "mac os x"};

    private MinecraftCompatibility() {
    }

    public static boolean isSupportedPlatform(String osName) {
        if (osName == null || osName.isBlank()) {
            return false;
        }
        String normalized = osName.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        for (String supported : SUPPORTED_PLATFORMS) {
            String normalizedSupported = supported.toLowerCase(Locale.ROOT).replace(' ', '_');
            if (normalizedSupported.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isMinecraftPackage(String className) {
        if (className == null || className.isBlank()) {
            return false;
        }
        return className.startsWith(MINECRAFT_PACKAGE + ".") || className.equals(MINECRAFT_PACKAGE);
    }

    public static boolean isFv2j3ApiPackage(String className) {
        if (className == null || className.isBlank()) {
            return false;
        }
        return className.startsWith(Fv2J3_API_PACKAGE + ".") || className.equals(Fv2J3_API_PACKAGE);
    }
}
