package com.fv2j3.installer.core;

import java.nio.file.Path;
import java.util.Objects;

public final class InstallationPlan {
    private final Path minecraftHome;
    private final Path fv2j3VersionDir;
    private final Path runtimeDir;
    private final boolean extractRuntime;
    private final boolean installClient;
    private final boolean integrateLauncher;
    private final boolean createModsDir;

    private InstallationPlan(Builder b) {
        this.minecraftHome = Objects.requireNonNull(b.minecraftHome, "minecraftHome");
        this.fv2j3VersionDir = b.fv2j3VersionDir != null
                ? b.fv2j3VersionDir
                : minecraftHome.resolve("versions").resolve(InstallerConstants.FV2J3_VERSION_ID);
        this.runtimeDir = b.runtimeDir != null
                ? b.runtimeDir
                : InstallerConstants.getRuntimeDir();
        this.extractRuntime = b.extractRuntime;
        this.installClient = b.installClient;
        this.integrateLauncher = b.integrateLauncher;
        this.createModsDir = b.createModsDir;
    }

    public static Builder builder(Path minecraftHome) {
        return new Builder(minecraftHome);
    }

    public Path minecraftHome() { return minecraftHome; }
    public Path fv2j3VersionDir() { return fv2j3VersionDir; }
    public Path runtimeDir() { return runtimeDir; }
    public boolean extractRuntime() { return extractRuntime; }
    public boolean installClient() { return installClient; }
    public boolean integrateLauncher() { return integrateLauncher; }
    public boolean createModsDir() { return createModsDir; }

    public Path fv2j3VersionJson() {
        return fv2j3VersionDir.resolve(InstallerConstants.FV2J3_VERSION_ID + ".json");
    }

    public Path modsDir() {
        return minecraftHome.resolve("mods");
    }

    public Path launcherProfilesJson() {
        return minecraftHome.resolve("launcher_profiles.json");
    }

    public Path runtimeLibsDir() {
        return runtimeDir.resolve("libs");
    }

    public String summary() {
        return String.format(
                "InstallationPlan[mc=%s, fv2j3VersionDir=%s, runtimeDir=%s, extractRuntime=%s, installClient=%s, integrateLauncher=%s, createModsDir=%s]",
                minecraftHome, fv2j3VersionDir, runtimeDir, extractRuntime, installClient, integrateLauncher, createModsDir
        );
    }

    public static final class Builder {
        private Path minecraftHome;
        private Path fv2j3VersionDir;
        private Path runtimeDir;
        private boolean extractRuntime = true;
        private boolean installClient = true;
        private boolean integrateLauncher = true;
        private boolean createModsDir = true;

        private Builder(Path minecraftHome) {
            this.minecraftHome = minecraftHome;
        }

        public Builder fv2j3VersionDir(Path v) { this.fv2j3VersionDir = v; return this; }
        public Builder runtimeDir(Path v) { this.runtimeDir = v; return this; }
        public Builder extractRuntime(boolean v) { this.extractRuntime = v; return this; }
        public Builder installClient(boolean v) { this.installClient = v; return this; }
        public Builder integrateLauncher(boolean v) { this.integrateLauncher = v; return this; }
        public Builder createModsDir(boolean v) { this.createModsDir = v; return this; }

        public InstallationPlan build() {
            return new InstallationPlan(this);
        }
    }
}
