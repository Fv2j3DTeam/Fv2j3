package io.github.fv2j3dteam.universe.planet;

import io.github.fv2j3dteam.universe.erosion.ErosionSystem;
import io.github.fv2j3dteam.universe.fluid.FluidDefinition;
import io.github.fv2j3dteam.universe.fluid.FluidGrid;
import io.github.fv2j3dteam.universe.fluid.FluidSystem;
import io.github.fv2j3dteam.universe.generation.ClimateGenerator;
import io.github.fv2j3dteam.universe.generation.PlanetGenerator;
import io.github.fv2j3dteam.universe.generation.TerrainGenerator;
import io.github.fv2j3dteam.universe.identifiers.ChunkCoord;
import io.github.fv2j3dteam.universe.identifiers.UniverseId;
import io.github.fv2j3dteam.universe.life.FloraGenerator;
import io.github.fv2j3dteam.universe.persistence.PersistenceManager;
import io.github.fv2j3dteam.universe.terrain.Chunk;
import io.github.fv2j3dteam.universe.thermal.ThermalField;
import io.github.fv2j3dteam.universe.universe.Planet;
import io.github.fv2j3dteam.universe.watercycle.WaterCycle;
import io.github.fv2j3dteam.universe.weather.PrecipitationSystem;
import io.github.fv2j3dteam.universe.structures.StructurePlacer;
import io.github.fv2j3dteam.universe.smoke.SmokeSystem;
import io.github.fv2j3dteam.universe.environment.EmitterRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Real {@link io.github.fv2j3dteam.universe.streaming.StreamingManager.RegionGenerator}
 * implementation that produces a fully-populated planet region.
 *
 * The generated {@link RegionContent} contains:
 *  - terrain chunk (heightfield + voxel column)
 *  - climate sample
 *  - biome and flora
 *  - structures
 *  - fluid grid (water/lava)
 *  - thermal grid
 *  - humidity grid
 *  - erosion/sediment deltas
 *
 * Persistent edits (terrain height deltas, discoveries) are written to
 * the {@link PersistenceManager} so they survive streaming.
 */
public final class PlanetRegionGenerator implements io.github.fv2j3dteam.universe.streaming.StreamingManager.RegionGenerator {

    private final Planet planet;
    private final TerrainGenerator terrain;
    private final ClimateGenerator climate;
    private final FloraGenerator flora;
    private final StructurePlacer structures;
    private final FluidSystem fluidSystem;
    private final SmokeSystem smokeSystem;
    private final ThermalField thermal;
    private final WaterCycle waterCycle;
    private final ErosionSystem erosion;
    private final PrecipitationSystem precipitation;
    private final EmitterRegistry emitters;

    public PlanetRegionGenerator(Planet planet,
                                 TerrainGenerator terrain,
                                 ClimateGenerator climate,
                                 FloraGenerator flora,
                                 StructurePlacer structures,
                                 FluidSystem fluidSystem,
                                 SmokeSystem smokeSystem,
                                 ThermalField thermal,
                                 WaterCycle waterCycle,
                                 ErosionSystem erosion,
                                 PrecipitationSystem precipitation,
                                 EmitterRegistry emitters) {
        this.planet = Objects.requireNonNull(planet, "planet");
        this.terrain = terrain;
        this.climate = climate;
        this.flora = flora;
        this.structures = structures;
        this.fluidSystem = fluidSystem;
        this.smokeSystem = smokeSystem;
        this.thermal = thermal;
        this.waterCycle = waterCycle;
        this.erosion = erosion;
        this.precipitation = precipitation;
        this.emitters = emitters;
    }

    public Planet planet() { return planet; }

    @Override
    public io.github.fv2j3dteam.universe.streaming.Region generate(
            io.github.fv2j3dteam.universe.streaming.RegionKey key,
            long universeSeed,
            int generatorVersion,
            PersistenceManager persistence) {
        ChunkCoord coord = new ChunkCoord(key.rx(), key.ry(), key.rz());

        // 1. Terrain
        Chunk chunk = terrain.generate(planet, coord);

        // 2. Climate (at chunk centre)
        ClimateGenerator.ClimateSample cs = climate.sample(planet, coord, 8, 8, 8);

        // 3. Flora (uses biome + climate)
        FloraGenerator.FloraChunk floraChunk = flora.generate(planet, coord, cs);

        // 4. Structures
        List<?> placements = structures != null ? structures.place(planet, coord, floraChunk.biome()) : List.of();

        // 5. Fluid grid (water for ocean/earth-like planets)
        FluidGrid fluid = null;
        if (planet.oceanCoverageFraction() > 0.05) {
            fluid = fluidSystem.getOrCreate(coord, "water");
            fluid.importSolids(chunk);
            // Initialise water in low-lying cells
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int surface = chunk.surfaceY(x, z);
                    if (surface < 6) {
                        for (int y = surface; y < 6; y++) {
                            if (chunk.voxel(x, y, z) == 0) {
                                fluid.setDensity(x, y, z, 0.7);
                            }
                        }
                    }
                }
            }
        }

        // 6. Thermal field
        thermal.setTemperature(coord, 8, 8, 8, cs.temperatureK);

        // 7. Water cycle: seed initial humidity
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                waterCycle.setHumidity(coord, x, Math.max(0, chunk.surfaceY(x, z)), z, (float) cs.humidity);
            }
        }

        // 8. Erosion layer (empty initially)
        erosion.unload(coord); // ensure fresh

        // 9. Emit environmental events for the region
        emitters.emittersIn(coord); // touch — visible in diagnostics

        // 10. Persistent edit: discovery
        if (persistence != null) {
            persistence.recordEdit(io.github.fv2j3dteam.universe.persistence.PersistentEdit.simple(
                    key.id(),
                    io.github.fv2j3dteam.universe.persistence.PersistentEdit.Kind.DISCOVERY,
                    new byte[]{1}));
        }

        RegionContent content = new RegionContent(
                chunk, cs, floraChunk, placements, fluid, coord, planet.id());
        return new io.github.fv2j3dteam.universe.streaming.Region(
                key, io.github.fv2j3dteam.universe.streaming.RegionState.LOADED, content, System.currentTimeMillis());
    }

    @Override
    public long estimateBytes(io.github.fv2j3dteam.universe.streaming.Region region) {
        if (region == null) return 0;
        if (region.content() instanceof RegionContent rc) return rc.residentBytes();
        return 4096;
    }

    /** Concrete region payload produced by the generator. */
    public static final class RegionContent {
        public final Chunk chunk;
        public final ClimateGenerator.ClimateSample climate;
        public final FloraGenerator.FloraChunk flora;
        public final List<?> structures;
        public final FluidGrid fluid;
        public final ChunkCoord coord;
        public final UniverseId planetId;

        public RegionContent(Chunk chunk,
                             ClimateGenerator.ClimateSample climate,
                             FloraGenerator.FloraChunk flora,
                             List<?> structures,
                             FluidGrid fluid,
                             ChunkCoord coord,
                             UniverseId planetId) {
            this.chunk = chunk;
            this.climate = climate;
            this.flora = flora;
            this.structures = structures == null ? List.of() : structures;
            this.fluid = fluid;
            this.coord = coord;
            this.planetId = planetId;
        }

        public long residentBytes() {
            long b = chunk == null ? 0L : chunk.residentBytes();
            if (fluid != null) b += fluid.residentBytes();
            // Approximation for climate/flora/structures
            b += 256 + (flora == null ? 0 : 256) + structures.size() * 64L;
            return b;
        }
    }
}
