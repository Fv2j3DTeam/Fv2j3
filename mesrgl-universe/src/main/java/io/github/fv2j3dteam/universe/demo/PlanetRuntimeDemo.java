package io.github.fv2j3dteam.universe.demo;

import io.github.fv2j3dteam.universe.UniverseFactory;
import io.github.fv2j3dteam.universe.environment.EmitterRegistry;
import io.github.fv2j3dteam.universe.erosion.ErosionSystem;
import io.github.fv2j3dteam.universe.fire.FireSystem;
import io.github.fv2j3dteam.universe.fluid.FluidDefinition;
import io.github.fv2j3dteam.universe.fluid.FluidSystem;
import io.github.fv2j3dteam.universe.generation.*;
import io.github.fv2j3dteam.universe.identifiers.ChunkCoord;
import io.github.fv2j3dteam.universe.identifiers.UniverseId;
import io.github.fv2j3dteam.universe.life.FloraGenerator;
import io.github.fv2j3dteam.universe.persistence.PersistenceManager;
import io.github.fv2j3dteam.universe.planet.PlanetRegionGenerator;
import io.github.fv2j3dteam.universe.smoke.SmokeSystem;
import io.github.fv2j3dteam.universe.terrain.Chunk;
import io.github.fv2j3dteam.universe.terrain.VoxelType;
import io.github.fv2j3dteam.universe.thermal.ThermalField;
import io.github.fv2j3dteam.universe.universe.Planet;
import io.github.fv2j3dteam.universe.watercycle.WaterCycle;
import io.github.fv2j3dteam.universe.weather.*;
import io.github.fv2j3dteam.universe.structures.StructurePlacer;
import io.github.fv2j3dteam.universe.streaming.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Real runtime demo (§42). Spawns a planet, generates terrain/atmosphere/
 * biome/flora/structures, ignites fire, runs fluid, runs rain, runs wind
 * transport, runs erosion, runs the water cycle, and prints observable
 * state to stdout.
 *
 * Run with:
 * <pre>
 *   ./gradlew :mesrgl-universe:runDemo
 * </pre>
 */
public final class PlanetRuntimeDemo {

    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempFile("mesrgl-demo-", ".dat");
        try {
            System.out.println("=== MESRGL Planet Runtime Demo ===");
            System.out.println("Persistence: " + tmp);
            UniverseFactory.Wired w = UniverseFactory.wire(
                    UniverseId.universeRoot(42L), 12345L,
                    io.github.fv2j3dteam.universe.seeds.SeedDerivation.GENERATOR_VERSION,
                    tmp, 50_000_000L);

            PlanetGenerator pg = new PlanetGenerator();
            TerrainGenerator tg = new TerrainGenerator();
            ClimateGenerator cg = new ClimateGenerator();
            FloraGenerator fg = new FloraGenerator(w.biomeRegistry);
            StructurePlacer sp = new StructurePlacer(w.structureRegistry, w.biomeRegistry);
            EmitterRegistry emitters = new EmitterRegistry();
            ErosionSystem erosion = new ErosionSystem(w.persistence);
            WaterCycle waterCycle = new WaterCycle(w.precipitationSystem);

            // Pick a planet with some water and atmosphere
            Planet planet = pg.generate(UniverseId.universeRoot(42L), 12345L, 1L, 5L, 3L);
            // Forcibly make it wet so we get water/life/weather coupling
            planet = new Planet(
                    planet.id(), planet.seed(), Planet.Archetype.EARTH_LIKE,
                    planet.radiusMeters(), planet.massKg(), planet.gravityMs2(),
                    planet.rotationPeriodHours(), planet.axialTiltDeg(),
                    planet.orbitalPeriodDays(), planet.semiMajorAxisAu(),
                    new io.github.fv2j3dteam.universe.universe.Atmosphere(
                            101325, 0.21, 0.78, 0.0004, 0.01, 0.0,
                            io.github.fv2j3dteam.universe.universe.Atmosphere.Composition.BREATHABLE, true),
                    planet.baselineTemperatureK(), 0.6, planet.waterMassKg(),
                    planet.weatherArchetype(), true, planet.hasRings(), 1);
            System.out.printf("Planet: %s, radius=%.0f m, baseline T=%.1f K, ocean=%.0f%%%n",
                    planet.archetype(), planet.radiusMeters(), planet.baselineTemperatureK(),
                    planet.oceanCoverageFraction() * 100);

            PlanetRegionGenerator regionGen = new PlanetRegionGenerator(
                    planet, tg, cg, fg, sp,
                    w.fluidSystem, w.smokeSystem,
                    w.thermalField, waterCycle, erosion, w.precipitationSystem, emitters);

            // Request a planet region
            RegionKey rk = new RegionKey(UniverseId.universeRoot(42L), 0, 0, 0);
            System.out.println("Requesting region " + rk);
            var f = w.streaming.request(rk, regionGen);
            io.github.fv2j3dteam.universe.streaming.Region region = f.get(10, TimeUnit.SECONDS);
            System.out.println("Region state: " + region.state());

            if (region.content() instanceof PlanetRegionGenerator.RegionContent rc) {
                Chunk c = rc.chunk;
                int solid = 0;
                for (int y = 0; y < 16; y++)
                    for (int z = 0; z < 16; z++)
                        for (int x = 0; x < 16; x++)
                            if (VoxelType.isSolid(c.voxel(x, y, z))) solid++;
                System.out.printf("Chunk voxels: %d solid, %d total, climate T=%.1f K, humidity=%.2f%n",
                        solid, 16*16*16, rc.climate.temperatureK, rc.climate.humidity);
                System.out.printf("Flora: biome=%s, density=%.2f, species=%d%n",
                        rc.flora.biome(), rc.flora.density(), rc.flora.species().size());

                // Ignite a fire on the surface
                long fireId = w.fireSystem.ignite(planet.id(), new ChunkCoord(0, 0, 0),
                        4, 12, 4, 1200.0, 50.0, 0.5, 0.3, 1.0, "smoke");
                System.out.printf("Fire #%d ignited (T=1200K, fuel=50)%n", fireId);

                // Set weather to rain
                w.weatherSystem.setWeather(planet.id(), "rain");
                System.out.println("Weather set: rain");

                // Step the simulation 30 ticks
                for (int i = 0; i < 30; i++) {
                    w.thermalField.step(rc.coord, c, 5.0, rc.climate.temperatureK, 0.05);
                    w.smokeSystem.step(0.05);
                    w.fireSystem.step(0.05);
                    w.precipitationSystem.step(rc.coord, 0.05, 5.0, 0.0, c, 0);
                    waterCycle.step(rc.coord, c, w.thermalField, planet, 101325, 0.05);
                    erosion.step(rc.coord, c, 0.5);
                    w.fluidSystem.step(rc.coord, 0.05, planet.gravityMs2());
                    w.weatherSystem.step(0.05);
                }
                System.out.printf("After 30 sim steps: fire_active=%d, smoke_sources=%d, precip_collisions=%d, fluid_resident=%d B%n",
                        w.fireSystem.activeSourceCount(),
                        w.smokeSystem.sourceCount(),
                        w.precipitationSystem.totalCollided(),
                        w.fluidSystem.activeChunks() > 0 ? 1 : 0);

                // Erosion diagnostics
                System.out.printf("Eroded voxels: %d, deposited: %d%n",
                        erosion.totalEroded(), erosion.totalDeposited());

                // Save
                io.github.fv2j3dteam.universe.error.Result<Path> save = w.persistence.save(12345L, 1);
                System.out.println("Save: " + (save.isOk() ? "OK -> " + save.value() : "FAILED " + save.error()));
            }

            w.streaming.shutdown();
            System.out.println("=== Demo complete ===");
        } finally {
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
        }
    }
}
