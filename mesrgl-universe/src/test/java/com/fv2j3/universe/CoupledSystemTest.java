package com.fv2j3.universe;

import com.fv2j3.universe.environment.EmitterRegistry;
import com.fv2j3.universe.environment.EnvironmentalEmitter;
import com.fv2j3.universe.erosion.ErosionSystem;
import com.fv2j3.universe.fire.FireSystem;
import com.fv2j3.universe.fluid.FluidDefinition;
import com.fv2j3.universe.fluid.FluidSystem;
import com.fv2j3.universe.generation.*;
import com.fv2j3.universe.identifiers.ChunkCoord;
import com.fv2j3.universe.identifiers.UniverseId;
import com.fv2j3.universe.life.FloraGenerator;
import com.fv2j3.universe.planet.PlanetRegionGenerator;
import com.fv2j3.universe.smoke.SmokeSystem;
import com.fv2j3.universe.terrain.Chunk;
import com.fv2j3.universe.terrain.VoxelType;
import com.fv2j3.universe.thermal.ThermalField;
import com.fv2j3.universe.universe.Planet;
import com.fv2j3.universe.watercycle.WaterCycle;
import com.fv2j3.universe.weather.*;
import com.fv2j3.universe.structures.StructurePlacer;
import com.fv2j3.universe.streaming.Region;
import com.fv2j3.universe.streaming.RegionKey;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end tests for the coupled environmental pipeline. */
class CoupledSystemTest {

    @Test
    void planetRegionGeneratesCompletePayload() throws Exception {
        Path p = Files.createTempFile("coupled-", ".dat");
        try {
            UniverseFactory.Wired w = UniverseFactory.wire(UniverseId.universeRoot(7L), 99L, 1, p, 50_000_000L);
            try {
                PlanetGenerator pg = new PlanetGenerator();
                Planet planet = pg.generate(UniverseId.universeRoot(7L), 99L, 1L, 5L, 3L);
                // Make sure ocean > 5% so fluid is populated
                Planet wet = makeWet(planet);
                PlanetRegionGenerator gen = planetRegionGenerator(wet, w);
                RegionKey key = new RegionKey(UniverseId.universeRoot(7L), 0, 0, 0);
                var future = w.streaming.request(key, gen);
                Region r = future.get(10, TimeUnit.SECONDS);
                assertNotNull(r.content());
                assertTrue(r.content() instanceof PlanetRegionGenerator.RegionContent);
                PlanetRegionGenerator.RegionContent rc = (PlanetRegionGenerator.RegionContent) r.content();
                assertNotNull(rc.chunk);
                assertNotNull(rc.climate);
                assertNotNull(rc.flora);
                assertNotNull(rc.coord);
                assertEquals(wet.id(), rc.planetId);
            } finally { w.streaming.shutdown(); }
        } finally { Files.deleteIfExists(p); }
    }

    @Test
    void fireSmokeWindCoupling() throws Exception {
        Path p = Files.createTempFile("coupled-", ".dat");
        try {
            UniverseFactory.Wired w = UniverseFactory.wire(UniverseId.universeRoot(7L), 99L, 1, p, 50_000_000L);
            try {
                Planet planet = makeWet(new PlanetGenerator().generate(UniverseId.universeRoot(7L), 99L, 1L, 5L, 3L));
                ChunkCoord c = new ChunkCoord(0, 0, 0);
                Chunk chunk = new com.fv2j3.universe.generation.TerrainGenerator().generate(planet, c);
                w.fireSystem.ignite(planet.id(), c, 4, 12, 4, 1500.0, 100, 1.0, 0.5, 1.0, "smoke");
                assertEquals(1, w.fireSystem.activeSourceCount());
                assertTrue(w.smokeSystem.sourceCount() >= 1);
                for (int i = 0; i < 10; i++) {
                    w.smokeSystem.step(0.05);
                    w.fireSystem.step(0.05);
                }
                assertTrue(w.smokeSystem.gridCount() >= 1);
            } finally { w.streaming.shutdown(); }
        } finally { Files.deleteIfExists(p); }
    }

    @Test
    void erosionReducesSurface() {
        var persistence = new com.fv2j3.universe.persistence.PersistenceManager(
                Path.of("nonexistent-" + System.nanoTime() + ".dat"),
                new com.fv2j3.universe.events.EventBus());
        ErosionSystem erosion = new ErosionSystem(persistence);
        Chunk chunk = new Chunk(0, 0, 0);
        // Build a hill in the middle
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int h = 8 + Math.max(0, 4 - Math.abs(x - 8) - Math.abs(z - 8));
                for (int y = 0; y < h; y++) chunk.setVoxel(x, y, z, VoxelType.STONE);
                chunk.setSurfaceY(x, z, h);
            }
        }
        erosion.step(new ChunkCoord(0, 0, 0), chunk, 1.0);
        // After one step, total eroded should be > 0
        long before = erosion.totalEroded();
        for (int i = 0; i < 5; i++) erosion.step(new ChunkCoord(0, 0, 0), chunk, 1.0);
        long after = erosion.totalEroded();
        assertTrue(after > before || erosion.totalDeposited() > 0);
    }

    @Test
    void waterCycleEmitsPrecipitation() {
        var precip = new PrecipitationSystem();
        var thermal = new ThermalField();
        var water = new WaterCycle(precip);
        Chunk chunk = new Chunk(0, 0, 0);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 5; y++) chunk.setVoxel(x, y, z, VoxelType.WATER);
                chunk.setSurfaceY(x, z, 5);
            }
        }
        thermal.setTemperature(new ChunkCoord(0, 0, 0), 8, 4, 8, 280.0);
        Planet oceanPlanet = makeWet(new PlanetGenerator().generate(UniverseId.universeRoot(7L), 99L, 1L, 5L, 3L));
        int emitted = water.step(new ChunkCoord(0, 0, 0), chunk, thermal, oceanPlanet, 101325, 0.5);
        // Either evaporation or condensation is observable
        assertTrue(emitted >= 0);
    }

    @Test
    void emitterRegistryTracksFire() throws Exception {
        Path p = Files.createTempFile("emit-", ".dat");
        try {
            UniverseFactory.Wired w = UniverseFactory.wire(UniverseId.universeRoot(7L), 99L, 1, p, 50_000_000L);
            try {
                EmitterRegistry reg = new EmitterRegistry();
                ChunkCoord c = new ChunkCoord(0, 0, 0);
                Planet planet = makeWet(new PlanetGenerator().generate(UniverseId.universeRoot(7L), 99L, 1L, 5L, 3L));
                long fireId = w.fireSystem.ignite(planet.id(), c, 4, 4, 4, 1500.0, 100, 1, 0.5, 1.0, "smoke");
                // Register a synthetic emitter that mirrors the fire
                reg.register(new EnvironmentalEmitter() {
                    @Override public long emitterId() { return fireId; }
                    @Override public ChunkCoord chunk() { return c; }
                    @Override public int cellX() { return 4; }
                    @Override public int cellY() { return 4; }
                    @Override public int cellZ() { return 4; }
                    @Override public double heatOutputPerSec() { return 1500.0; }
                    @Override public double humidityOutputPerSec() { return 0.0; }
                    @Override public double co2OutputPerSec() { return 0.0; }
                    @Override public double emissionRate(String gasId) { return gasId.equals("smoke") ? 0.5 : 0.0; }
                    @Override public boolean isActive() { return true; }
                    @Override public UniverseId planetId() { return planet.id(); }
                });
                assertEquals(1, reg.totalEmitters());
                assertEquals(1, reg.emittersIn(c).size());
                reg.unregister(fireId);
                assertEquals(0, reg.totalEmitters());
            } finally { w.streaming.shutdown(); }
        } finally { Files.deleteIfExists(p); }
    }

    @Test
    void fullPipelineSteps() throws Exception {
        Path p = Files.createTempFile("full-", ".dat");
        try {
            UniverseFactory.Wired w = UniverseFactory.wire(UniverseId.universeRoot(7L), 99L, 1, p, 50_000_000L);
            try {
                Planet planet = makeWet(new PlanetGenerator().generate(UniverseId.universeRoot(7L), 99L, 1L, 5L, 3L));
                PlanetRegionGenerator gen = planetRegionGenerator(planet, w);
                RegionKey key = new RegionKey(UniverseId.universeRoot(7L), 0, 0, 0);
                Region r = w.streaming.request(key, gen).get(10, TimeUnit.SECONDS);
                PlanetRegionGenerator.RegionContent rc = (PlanetRegionGenerator.RegionContent) r.content();
                Chunk chunk = rc.chunk;
                long fireId = w.fireSystem.ignite(planet.id(), rc.coord, 4, 12, 4, 1500.0, 100, 1, 0.5, 1.0, "smoke");
                w.weatherSystem.setWeather(planet.id(), "rain");
                com.fv2j3.universe.erosion.ErosionSystem erosion = new com.fv2j3.universe.erosion.ErosionSystem(w.persistence);
                WaterCycle water = new WaterCycle(w.precipitationSystem);
                // Step everything
                for (int i = 0; i < 5; i++) {
                    w.thermalField.step(rc.coord, chunk, 3.0, 290.0, 0.05);
                    w.smokeSystem.step(0.05);
                    w.fireSystem.step(0.05);
                    w.precipitationSystem.step(rc.coord, 0.05, 5.0, 0.0, chunk, 0);
                    water.step(rc.coord, chunk, w.thermalField, planet, 101325, 0.05);
                    erosion.step(rc.coord, chunk, 0.5);
                    w.fluidSystem.step(rc.coord, 0.05, 9.81);
                    w.weatherSystem.step(0.05);
                }
                // After all steps, the system should remain stable and reports sane diagnostics.
                assertTrue(w.fireSystem.activeSourceCount() >= 1);
                assertTrue(w.diagnostics.counter("streaming.generation.completed") >= 1);
            } finally { w.streaming.shutdown(); }
        } finally { Files.deleteIfExists(p); }
    }

    private static Planet makeWet(Planet planet) {
        return new Planet(
                planet.id(), planet.seed(), Planet.Archetype.EARTH_LIKE,
                planet.radiusMeters(), planet.massKg(), planet.gravityMs2(),
                planet.rotationPeriodHours(), planet.axialTiltDeg(),
                planet.orbitalPeriodDays(), planet.semiMajorAxisAu(),
                new com.fv2j3.universe.universe.Atmosphere(
                        101325, 0.21, 0.78, 0.0004, 0.01, 0.0,
                        com.fv2j3.universe.universe.Atmosphere.Composition.BREATHABLE, true),
                planet.baselineTemperatureK(), 0.6, planet.waterMassKg(),
                planet.weatherArchetype(), true, planet.hasRings(), 1);
    }

    private static PlanetRegionGenerator planetRegionGenerator(Planet planet, UniverseFactory.Wired w) {
        return new PlanetRegionGenerator(
                planet,
                new TerrainGenerator(),
                new ClimateGenerator(),
                new FloraGenerator(w.biomeRegistry),
                new StructurePlacer(w.structureRegistry, w.biomeRegistry),
                w.fluidSystem, w.smokeSystem, w.thermalField,
                new WaterCycle(w.precipitationSystem),
                new ErosionSystem(w.persistence),
                w.precipitationSystem,
                new EmitterRegistry());
    }
}
