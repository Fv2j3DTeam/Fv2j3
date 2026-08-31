package com.fv2j3.installer.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class InstallationResult {
    private final boolean success;
    private final Path minecraftHome;
    private final Path fv2j3VersionDir;
    private final Path fv2j3VersionJson;
    private final Path runtimeDir;
    private final Path modsDir;
    private final boolean launcherIntegrated;
    private final List<String> steps;
    private final List<String> warnings;
    private final String failureReason;

    private InstallationResult(Builder b) {
        this.success = b.success;
        this.minecraftHome = b.minecraftHome;
        this.fv2j3VersionDir = b.fv2j3VersionDir;
        this.fv2j3VersionJson = b.fv2j3VersionJson;
        this.runtimeDir = b.runtimeDir;
        this.modsDir = b.modsDir;
        this.launcherIntegrated = b.launcherIntegrated;
        this.steps = Collections.unmodifiableList(new ArrayList<>(b.steps));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(b.warnings));
        this.failureReason = b.failureReason;
    }

    public static Builder builder() { return new Builder(); }
    public static InstallationResult failure(String reason) {
        return new Builder().success(false).failureReason(reason).build();
    }

    public boolean success() { return success; }
    public Path minecraftHome() { return minecraftHome; }
    public Path fv2j3VersionDir() { return fv2j3VersionDir; }
    public Path fv2j3VersionJson() { return fv2j3VersionJson; }
    public Path runtimeDir() { return runtimeDir; }
    public Path modsDir() { return modsDir; }
    public boolean launcherIntegrated() { return launcherIntegrated; }
    public List<String> steps() { return steps; }
    public List<String> warnings() { return warnings; }
    public String failureReason() { return failureReason; }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Installation ").append(success ? "SUCCEEDED" : "FAILED").append("\n");
        sb.append("Minecraft directory: ").append(minecraftHome).append("\n");
        sb.append("Fv2j3 version dir: ").append(fv2j3VersionDir).append("\n");
        sb.append("Fv2j3 version JSON: ").append(fv2j3VersionJson).append("\n");
        sb.append("Runtime directory: ").append(runtimeDir).append("\n");
        sb.append("Mods directory: ").append(modsDir).append("\n");
        sb.append("Launcher integrated: ").append(launcherIntegrated ? "yes" : "no").append("\n");
        sb.append("Steps:\n");
        for (String s : steps) {
            sb.append("  - ").append(s).append("\n");
        }
        if (!warnings.isEmpty()) {
            sb.append("Warnings:\n");
            for (String w : warnings) {
                sb.append("  ! ").append(w).append("\n");
            }
        }
        if (failureReason != null) {
            sb.append("Failure: ").append(failureReason).append("\n");
        }
        return sb.toString();
    }

    public static final class Builder {
        private boolean success;
        private Path minecraftHome;
        private Path fv2j3VersionDir;
        private Path fv2j3VersionJson;
        private Path runtimeDir;
        private Path modsDir;
        private boolean launcherIntegrated;
        private final List<String> steps = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private String failureReason;

        public Builder success(boolean v) { this.success = v; return this; }
        public Builder minecraftHome(Path v) { this.minecraftHome = v; return this; }
        public Builder fv2j3VersionDir(Path v) { this.fv2j3VersionDir = v; return this; }
        public Builder fv2j3VersionJson(Path v) { this.fv2j3VersionJson = v; return this; }
        public Builder runtimeDir(Path v) { this.runtimeDir = v; return this; }
        public Builder modsDir(Path v) { this.modsDir = v; return this; }
        public Builder launcherIntegrated(boolean v) { this.launcherIntegrated = v; return this; }
        public Builder addStep(String s) { if (s != null) steps.add(s); return this; }
        public Builder addWarning(String s) { if (s != null) warnings.add(s); return this; }
        public Builder failureReason(String s) { this.failureReason = s; return this; }

        public InstallationResult build() {
            return new InstallationResult(this);
        }
    }
}
