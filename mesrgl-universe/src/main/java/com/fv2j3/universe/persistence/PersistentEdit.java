package com.fv2j3.universe.persistence;

import com.fv2j3.universe.identifiers.UniverseId;

/**
 * Persistent edit record. Stores user / gameplay modifications that must survive
 * regeneration of the procedural base.
 */
public record PersistentEdit(
        UniverseId id,
        long timestampMillis,
        Kind kind,
        byte[] payload
) {
    public enum Kind {
        TERRAIN_HEIGHT_DELTA,
        TERRAIN_BLOCK_EDIT,
        STRUCTURE_PLACE,
        STRUCTURE_DESTROY,
        RESOURCE_DEPLETION,
        DISCOVERY,
        SNOW_DEPTH_OVERRIDE,
        FLUID_LEVEL_DELTA,
        FIRE_STATE,
        STRUCTURE_INSTANCE,
        PERSISTENT_GENERIC
    }

    public PersistentEdit {
        if (payload == null) payload = new byte[0];
    }

    public static PersistentEdit simple(UniverseId id, Kind kind, byte[] payload) {
        return new PersistentEdit(id, System.currentTimeMillis(), kind, payload);
    }
}
