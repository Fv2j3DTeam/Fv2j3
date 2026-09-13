package io.github.fv2j3dteam.universe.life;

import io.github.fv2j3dteam.universe.biome.BiomeRegistry;
import io.github.fv2j3dteam.universe.generation.ClimateGenerator;
import io.github.fv2j3dteam.universe.identifiers.ChunkCoord;
import io.github.fv2j3dteam.universe.universe.Planet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Flora generator (§15). Procedurally places vegetation density and species
 * per chunk using climate + biome inputs.
 */
public final class FloraGenerator {

    private final BiomeRegistry biomeRegistry;

    public FloraGenerator(BiomeRegistry biomeRegistry) {
        this.biomeRegistry = Objects.requireNonNull(biomeRegistry, "biomeRegistry");
    }

    public FloraChunk generate(Planet planet, ChunkCoord coord,
                                ClimateGenerator.ClimateSample climate) {
        var biome = biomeRegistry.select(climate.temperatureK, climate.humidity, climate.altitude,
                coord.x() * 73856093L ^ coord.z() * 19349663L);
        double density = biome.vegetationDensity() * climate.humidity;
        if (density < 0) density = 0; if (density > 1) density = 1;
        // Species selection: pick deterministically by chunk + biome
        long seed = planet.seed() ^ coord.x() * 12345L ^ coord.z() * 67890L;
        io.github.fv2j3dteam.universe.math.XorShift64 rng = new io.github.fv2j3dteam.universe.math.XorShift64(seed);
        List<String> species = new ArrayList<>();
        int n = (int) Math.round(density * 3);
        for (int i = 0; i < n; i++) {
            species.add(biome.id() + "-flora-" + rng.nextInt(0, 1000));
        }
        return new FloraChunk(coord, biome.id(), density, species);
    }

    public record FloraChunk(ChunkCoord coord, String biome, double density, List<String> species) {}
}
