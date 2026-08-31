package com.fv2j3.installer.core;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class InstallerConstants {
    public static final String MINECRAFT_VERSION = "1.12.2";
    public static final String FV2J3_VERSION_ID = "Fv2j3-" + MINECRAFT_VERSION;
    public static final String FV2J3_PROFILE_NAME = "Fv2j3 1.12.2";
    public static final String FV2J3_MAIN_CLASS = "com.fv2j3.loader.runtime.Bootstrap";
    public static final String FV2J3_INSTALLER_VERSION = readVersion();

    private InstallerConstants() {
    }

    public static Path getRuntimeDir() {
        String property = System.getProperty("fv2j3.installer.runtime.dir");
        if (property != null && !property.isBlank()) {
            return Paths.get(property).toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.home"), ".fv2j3", "installer-runtime").toAbsolutePath();
    }

    private static String readVersion() {
        String version = InstallerConstants.class.getPackage().getImplementationVersion();
        if (version != null && !version.isBlank()) {
            return version;
        }
        return System.getProperty("fv2j3.installer.version", "0.1.0-SNAPSHOT");
    }
}
