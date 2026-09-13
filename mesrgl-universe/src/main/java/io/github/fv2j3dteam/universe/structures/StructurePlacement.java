package io.github.fv2j3dteam.universe.structures;

import io.github.fv2j3dteam.universe.identifiers.UniverseId;

/** Structure placement result. */
public record StructurePlacement(UniverseId id, StructureDefinition def, long chunkX, long chunkZ) {}
