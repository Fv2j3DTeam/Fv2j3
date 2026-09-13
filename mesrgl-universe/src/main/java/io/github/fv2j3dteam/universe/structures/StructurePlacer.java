package io.github.fv2j3dteam.universe.structures;

import io.github.fv2j3dteam.universe.biome.BiomeRegistry;
import io.github.fv2j3dteam.universe.identifiers.ChunkCoord;
import io.github.fv2j3dteam.universe.identifiers.UniverseId;
import io.github.fv2j3dteam.universe.math.XorShift64;
import io.github.fv2j3dteam.universe.seeds.SeedDerivation;
import io.github.fv2j3dteam.universe.universe.Planet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Structure placement (§16). */
public final class StructurePlacer {
    private final StructureRegistry registry;
    private final BiomeRegistry biomeRegistry;

    public StructurePlacer(StructureRegistry registry, BiomeRegistry biomeRegistry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.biomeRegistry = Objects.requireNonNull(biomeRegistry, "biomeRegistry");
    }

    public List<StructurePlacement> place(Planet planet, ChunkCoord coord, String biomeId) {
        List<StructurePlacement> out = new ArrayList<>();
        UniverseId id = planet.id();
        long seed = SeedDerivation.deriveSubSeed(planet.seed(), coord.x() * 1000003L ^ coord.z() * 1000033L);
        XorShift64 rng = new XorShift64(seed);
        for (StructureDefinition def : registry.all()) {
            if (!def.matchesBiome(biomeId)) continue;
            if (rng.nextDouble() < def.rarity()) {
                out.add(new StructurePlacement(id, def, coord.x(), coord.z()));
            }
        }
        return out;
    }
}
