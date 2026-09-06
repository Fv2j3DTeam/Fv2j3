package com.fv2j3.loader.core;

import com.fv2j3.api.Fv2j3Config;
import com.fv2j3.api.ModConfigProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-mod configuration registry (Phase 47.1 - mod config service).
 *
 * The loader creates one {@link Fv2j3Config} per mod, backed by
 * {@code <configDir>/<modId>.properties}. Two mods never share a file.
 * The registry is exposed to mods through the {@link ModConfigProvider}
 * functional interface; mods call {@code configProvider().resolve(myId)}
 * inside their context.
 *
 * All registered configs share the same on-disk directory so the
 * Mod Menu can list them by filename, and so a per-mod config reset
 * is a single file delete.
 */
public final class ModConfigRegistry implements ModConfigProvider {

    private final Path configDir;
    private final Map<String, Fv2j3Config> byModId = new LinkedHashMap<>();

    public ModConfigRegistry(Path configDir) {
        this.configDir = Objects.requireNonNull(configDir, "configDir");
    }

    /** Resolves a mod's config, creating the file lazily on first access. */
    @Override
    public Optional<Fv2j3Config> resolve(String modId) {
        if (modId == null) {
            return Optional.empty();
        }
        Fv2j3Config cached = byModId.get(modId);
        if (cached != null) {
            return Optional.of(cached);
        }
        if (!isSafeModId(modId)) {
            return Optional.empty();
        }
        try {
            Files.createDirectories(configDir);
        } catch (Exception ex) {
            return Optional.empty();
        }
        Fv2j3Config cfg = new Fv2j3Config(modId, configDir.resolve(modId + ".properties"));
        byModId.put(modId, cfg);
        return Optional.of(cfg);
    }

    public Path configDir() {
        return configDir;
    }

    /** All configs that have been resolved (in resolution order). */
    public Map<String, Fv2j3Config> all() {
        return Map.copyOf(byModId);
    }

    /**
     * Persists every dirty config. The loader calls this after every
     * successful pre-init/init lifecycle step so a crash never loses
     * the user's last set of values.
     */
    public void saveAll() {
        for (Fv2j3Config cfg : byModId.values()) {
            if (cfg.isDirty()) {
                cfg.save();
            }
        }
    }

    /** Drops the in-memory cache (does NOT delete the files). */
    public void clear() {
        byModId.clear();
    }

    /**
     * Mod ids may only contain [a-z0-9_.-] (the same rule the loader
     * uses for resource namespaces). Anything else is rejected to
     * prevent a malicious mod id from escaping the config dir.
     */
    private static boolean isSafeModId(String id) {
        if (id.isEmpty() || id.length() > 64) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
