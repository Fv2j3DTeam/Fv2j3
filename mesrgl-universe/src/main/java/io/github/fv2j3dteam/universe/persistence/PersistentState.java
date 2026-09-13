package io.github.fv2j3dteam.universe.persistence;

import io.github.fv2j3dteam.universe.identifiers.UniverseId;

import java.util.List;
import java.util.Map;

/**
 * Top-level persistent state for a universe: identity, generator versions, schema
 * version, and per-region edit lists. Fully serialisable.
 */
public record PersistentState(
        long universeRootSeed,
        int generatorVersion,
        int schemaVersion,
        long timestampMillis,
        Map<UniverseId, List<PersistentEdit>> editsById
) {
    public PersistentState {
        editsById = editsById == null ? Map.of() : Map.copyOf(editsById);
    }
}
