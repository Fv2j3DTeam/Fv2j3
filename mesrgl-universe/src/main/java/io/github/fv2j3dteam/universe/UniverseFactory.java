package io.github.fv2j3dteam.universe;

import io.github.fv2j3dteam.universe.biome.BiomeRegistry;
import io.github.fv2j3dteam.universe.cache.CacheManager;
import io.github.fv2j3dteam.universe.diagnostics.DefaultDiagnostics;
import io.github.fv2j3dteam.universe.diagnostics.Diagnostics;
import io.github.fv2j3dteam.universe.diagnostics.Profiler;
import io.github.fv2j3dteam.universe.environment.EnvironmentalForceQuery;
import io.github.fv2j3dteam.universe.events.EventBus;
import io.github.fv2j3dteam.universe.fire.FireSystem;
import io.github.fv2j3dteam.universe.fluid.FluidRegistry;
import io.github.fv2j3dteam.universe.fluid.FluidSystem;
import io.github.fv2j3dteam.universe.fluid.OceanSystem;
import io.github.fv2j3dteam.universe.fluid.WaveSystem;
import io.github.fv2j3dteam.universe.identifiers.UniverseId;
import io.github.fv2j3dteam.universe.life.FloraGenerator;
import io.github.fv2j3dteam.universe.life.SpeciesRegistry;
import io.github.fv2j3dteam.universe.materials.MaterialRegistry;
import io.github.fv2j3dteam.universe.persistence.PersistenceManager;
import io.github.fv2j3dteam.universe.resources.ResourceRegistry;
import io.github.fv2j3dteam.universe.seeds.SeedDerivation;
import io.github.fv2j3dteam.universe.smoke.GasRegistry;
import io.github.fv2j3dteam.universe.smoke.GasSystem;
import io.github.fv2j3dteam.universe.smoke.SmokeSystem;
import io.github.fv2j3dteam.universe.streaming.MemoryBudget;
import io.github.fv2j3dteam.universe.streaming.StreamingManager;
import io.github.fv2j3dteam.universe.structures.StructurePlacer;
import io.github.fv2j3dteam.universe.structures.StructureRegistry;
import io.github.fv2j3dteam.universe.thermal.PhaseTransitions;
import io.github.fv2j3dteam.universe.thermal.ThermalField;
import io.github.fv2j3dteam.universe.universe.Universe;
import io.github.fv2j3dteam.universe.weather.PrecipitationSystem;
import io.github.fv2j3dteam.universe.weather.SnowSystem;
import io.github.fv2j3dteam.universe.weather.WeatherRegistry;
import io.github.fv2j3dteam.universe.weather.WeatherSystem;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Top-level wiring: a single call wires the entire environmental framework.
 * External systems only need this entry point plus the public APIs.
 */
public final class UniverseFactory {

    public static final class Wired {
        public final Universe universe;
        public final EventBus events;
        public final Diagnostics diagnostics;
        public final Profiler profiler;
        public final CacheManager cache;
        public final StreamingManager streaming;
        public final PersistenceManager persistence;
        public final BiomeRegistry biomeRegistry;
        public final ResourceRegistry resourceRegistry;
        public final FluidRegistry fluidRegistry;
        public final GasRegistry gasRegistry;
        public final MaterialRegistry materialRegistry;
        public final WeatherRegistry weatherRegistry;
        public final StructureRegistry structureRegistry;
        public final SpeciesRegistry speciesRegistry;
        public final FluidSystem fluidSystem;
        public final SmokeSystem smokeSystem;
        public final GasSystem gasSystem;
        public final FireSystem fireSystem;
        public final OceanSystem oceanSystem;
        public final WaveSystem waveSystem;
        public final WeatherSystem weatherSystem;
        public final PrecipitationSystem precipitationSystem;
        public final SnowSystem snowSystem;
        public final ThermalField thermalField;
        public final PhaseTransitions phaseTransitions;
        public final EnvironmentalForceQuery forceQuery;
        public final FloraGenerator floraGenerator;
        public final StructurePlacer structurePlacer;
        public final MemoryBudget memoryBudget;
        public Wired(Universe universe, EventBus events, Diagnostics diagnostics, Profiler profiler,
                     CacheManager cache, StreamingManager streaming, PersistenceManager persistence,
                     BiomeRegistry biomeRegistry, ResourceRegistry resourceRegistry,
                     FluidRegistry fluidRegistry, GasRegistry gasRegistry,
                     MaterialRegistry materialRegistry, WeatherRegistry weatherRegistry,
                     StructureRegistry structureRegistry, SpeciesRegistry speciesRegistry,
                     FluidSystem fluidSystem, SmokeSystem smokeSystem, GasSystem gasSystem,
                     FireSystem fireSystem, OceanSystem oceanSystem, WaveSystem waveSystem,
                     WeatherSystem weatherSystem, PrecipitationSystem precipitationSystem,
                     SnowSystem snowSystem, ThermalField thermalField, PhaseTransitions phaseTransitions,
                     EnvironmentalForceQuery forceQuery, FloraGenerator floraGenerator,
                     StructurePlacer structurePlacer, MemoryBudget memoryBudget) {
            this.universe = universe; this.events = events; this.diagnostics = diagnostics; this.profiler = profiler;
            this.cache = cache; this.streaming = streaming; this.persistence = persistence;
            this.biomeRegistry = biomeRegistry; this.resourceRegistry = resourceRegistry;
            this.fluidRegistry = fluidRegistry; this.gasRegistry = gasRegistry;
            this.materialRegistry = materialRegistry; this.weatherRegistry = weatherRegistry;
            this.structureRegistry = structureRegistry; this.speciesRegistry = speciesRegistry;
            this.fluidSystem = fluidSystem; this.smokeSystem = smokeSystem; this.gasSystem = gasSystem;
            this.fireSystem = fireSystem; this.oceanSystem = oceanSystem; this.waveSystem = waveSystem;
            this.weatherSystem = weatherSystem; this.precipitationSystem = precipitationSystem;
            this.snowSystem = snowSystem; this.thermalField = thermalField;
            this.phaseTransitions = phaseTransitions; this.forceQuery = forceQuery;
            this.floraGenerator = floraGenerator; this.structurePlacer = structurePlacer;
            this.memoryBudget = memoryBudget;
        }
    }

    private UniverseFactory() {}

    /**
     * Wire a fully-functional universe. {@code memoryBudgetBytes} controls
     * total resident budget; distant regions will be evicted automatically.
     */
    public static Wired wire(UniverseId universeId, long universeRootSeed, int generatorVersion,
                             Path persistenceFile, long memoryBudgetBytes) {
        Objects.requireNonNull(universeId, "universeId");
        Objects.requireNonNull(persistenceFile, "persistenceFile");
        Diagnostics diagnostics = new DefaultDiagnostics();
        Profiler profiler = new Profiler(diagnostics);
        EventBus events = new EventBus();
        MemoryBudget budget = new MemoryBudget(memoryBudgetBytes);
        CacheManager cache = new CacheManager(budget);
        PersistenceManager persistence = new PersistenceManager(persistenceFile, events);
        StreamingManager streaming = new StreamingManager(universeId, SeedDerivation.deriveUniverseSeed(universeRootSeed),
                generatorVersion, cache, persistence, events, diagnostics);
        Universe universe = new Universe(universeId, universeRootSeed, generatorVersion,
                streaming, persistence, diagnostics, events, cache);
        BiomeRegistry biomeRegistry = new BiomeRegistry();
        ResourceRegistry resourceRegistry = new ResourceRegistry();
        FluidRegistry fluidRegistry = new FluidRegistry();
        GasRegistry gasRegistry = new GasRegistry();
        MaterialRegistry materialRegistry = new MaterialRegistry();
        WeatherRegistry weatherRegistry = new WeatherRegistry();
        StructureRegistry structureRegistry = new StructureRegistry();
        SpeciesRegistry speciesRegistry = new SpeciesRegistry();
        FluidSystem fluidSystem = new FluidSystem(fluidRegistry);
        SmokeSystem smokeSystem = new SmokeSystem(gasRegistry);
        GasSystem gasSystem = new GasSystem(gasRegistry, smokeSystem);
        FireSystem fireSystem = new FireSystem(events, smokeSystem);
        OceanSystem oceanSystem = new OceanSystem();
        WaveSystem waveSystem = new WaveSystem(oceanSystem);
        WeatherSystem weatherSystem = new WeatherSystem(weatherRegistry, events);
        PrecipitationSystem precipitationSystem = new PrecipitationSystem();
        SnowSystem snowSystem = new SnowSystem();
        ThermalField thermalField = new ThermalField();
        PhaseTransitions phaseTransitions = new PhaseTransitions();
        EnvironmentalForceQuery forceQuery = new EnvironmentalForceQuery();
        FloraGenerator floraGenerator = new FloraGenerator(biomeRegistry);
        StructurePlacer structurePlacer = new StructurePlacer(structureRegistry, biomeRegistry);
        return new Wired(universe, events, diagnostics, profiler, cache, streaming, persistence,
                biomeRegistry, resourceRegistry, fluidRegistry, gasRegistry, materialRegistry,
                weatherRegistry, structureRegistry, speciesRegistry, fluidSystem, smokeSystem,
                gasSystem, fireSystem, oceanSystem, waveSystem, weatherSystem, precipitationSystem,
                snowSystem, thermalField, phaseTransitions, forceQuery, floraGenerator, structurePlacer,
                budget);
    }
}
