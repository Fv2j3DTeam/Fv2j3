package io.github.fv2j3dteam.loader.core;

import java.util.Objects;

public record LoaderEnvironment(
        String javaVersion,
        String operatingSystem,
        String architecture,
        String minecraftVersion,
        String fv2j3Version,
        LoaderSide side
) {
    public LoaderEnvironment {
        javaVersion = Objects.requireNonNullElse(javaVersion, "unknown");
        operatingSystem = Objects.requireNonNullElse(operatingSystem, "unknown");
        architecture = Objects.requireNonNullElse(architecture, "unknown");
        minecraftVersion = Objects.requireNonNullElse(minecraftVersion, "unknown");
        fv2j3Version = Objects.requireNonNullElse(fv2j3Version, "unknown");
        side = side != null ? side : LoaderSide.BOTH;
    }

    public static LoaderEnvironment detectCurrent(String minecraftVersion, String fv2j3Version) {
        return new LoaderEnvironment(
                System.getProperty("java.version", "unknown"),
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.arch", "unknown"),
                minecraftVersion,
                fv2j3Version,
                LoaderSide.BOTH
        );
    }

    public static LoaderEnvironment detectCurrent(String minecraftVersion, String fv2j3Version, LoaderSide side) {
        return new LoaderEnvironment(
                System.getProperty("java.version", "unknown"),
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.arch", "unknown"),
                minecraftVersion,
                fv2j3Version,
                side
        );
    }

    public int javaMajorVersion() {
        String trimmed = javaVersion.strip();
        int start = 0;
        while (start < trimmed.length() && !Character.isDigit(trimmed.charAt(start))) {
            start++;
        }
        if (start >= trimmed.length()) {
            return -1;
        }
        int end = start;
        while (end < trimmed.length() && Character.isDigit(trimmed.charAt(end))) {
            end++;
        }
        if (end == start) {
            return -1;
        }
        try {
            return Integer.parseInt(trimmed.substring(start, end));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    public boolean isJava26() {
        return javaMajorVersion() == 26;
    }

    public void validateJava26() {
        if (!isJava26()) {
            throw new IllegalStateException(
                    "Fv2j3 requires Java 26, but detected Java " + javaVersion + "."
            );
        }
    }

    public String platformSummary() {
        return "Minecraft " + minecraftVersion + " / Java " + javaVersion + " / OS " + operatingSystem + " / Arch " + architecture;
    }
}
