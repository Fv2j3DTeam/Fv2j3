package com.fv2j3.universe.persistence;

import com.fv2j3.universe.identifiers.UniverseId;

import java.util.List;
import java.util.Map;

/**
 * Schema migration (§22, §86). Migrates older minor versions to current.
 * Currently a no-op stub for v1.0; future versions add migrations here.
 */
public final class Migration {
    private Migration() {}

    public static void migrate(Map<UniverseId, List<PersistentEdit>> edits, int fromVersion) {
        int currentMajor = SaveSchema.MAJOR;
        int currentMinor = SaveSchema.MINOR;
        int fromMajor = (fromVersion >> 16) & 0xFFFF;
        int fromMinor = fromVersion & 0xFFFF;
        if (fromMajor > currentMajor) {
            throw new IllegalStateException("cannot migrate from newer major: " + fromMajor);
        }
        if (fromMajor == currentMajor && fromMinor > currentMinor) {
            throw new IllegalStateException("cannot migrate from newer minor: " + fromMinor);
        }
        // forward migrations would go here; v1.0 is initial
    }
}
