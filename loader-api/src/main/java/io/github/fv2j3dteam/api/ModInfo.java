package io.github.fv2j3dteam.api;

public record ModInfo(String id, String name, String version, ModRuntimeState state) {
    public ModInfo {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Mod id must not be blank.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Mod name must not be blank.");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Mod version must not be blank.");
        }
        if (state == null) {
            throw new IllegalArgumentException("Mod state must not be null.");
        }
    }
}
