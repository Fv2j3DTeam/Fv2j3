package com.fv2j3.universe.structures;

import com.fv2j3.universe.identifiers.UniverseId;

/** Structure placement result. */
public record StructurePlacement(UniverseId id, StructureDefinition def, long chunkX, long chunkZ) {}
