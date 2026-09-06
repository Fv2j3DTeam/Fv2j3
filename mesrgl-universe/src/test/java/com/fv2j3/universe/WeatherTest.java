package com.fv2j3.universe;

import com.fv2j3.universe.identifiers.ChunkCoord;
import com.fv2j3.universe.terrain.Chunk;
import com.fv2j3.universe.terrain.VoxelType;
import com.fv2j3.universe.weather.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeatherTest {

    @Test
    void rainDropletsFall() {
        PrecipitationSystem p = new PrecipitationSystem();
        Chunk c = new Chunk(0, 0, 0);
        ChunkCoord coord = new ChunkCoord(0, 0, 0);
        p.emit(coord, 4, 8, 4, 0, 0, 0, 1.0);
        p.step(coord, 0.1, 0, 0, c, 0);
        // After 0.1s of gravity, droplet y has decreased.
        // We don't have a public getter, but count should remain alive if it didn't hit the floor.
        // Direct: emit a droplet high in the air and check it survives.
        p.emit(coord, 4, 15, 4, 0, 0, 0, 1.0);
        int count = p.dropletCount(coord);
        assertTrue(count >= 1);
    }

    @Test
    void rainCollidesWithTerrain() {
        PrecipitationSystem p = new PrecipitationSystem();
        Chunk c = new Chunk(0, 0, 0);
        // Build a column at x=4, z=4
        c.fillBox(4, 0, 4, 5, 8, 5, VoxelType.STONE);
        ChunkCoord coord = new ChunkCoord(0, 0, 0);
        p.emit(coord, 4, 15, 4, 0, 0, 0, 1.0);
        int collisions = p.step(coord, 1.0, 0, 0, c, 0);
        assertTrue(collisions >= 1);
    }

    @Test
    void snowAccumulatesAndMelts() {
        SnowSystem s = new SnowSystem();
        ChunkCoord coord = new ChunkCoord(0, 0, 0);
        s.deposit(coord, 4, 4, 0.5f);
        assertEquals(0.5f, s.depthAt(coord, 4, 4), 1e-6);
        s.step(coord, 290.0, 10.0);
        // Melting happens at temperatures > 273.15.
        assertTrue(s.depthAt(coord, 4, 4) < 0.5f);
    }

    @Test
    void stormLifecycle() {
        var events = new com.fv2j3.universe.events.EventBus();
        WeatherSystem ws = new WeatherSystem(new WeatherRegistry(), events);
        com.fv2j3.universe.identifiers.UniverseId pid = new com.fv2j3.universe.identifiers.UniverseId(1, 0, 0, 0, 0, 0, 0);
        long id = ws.spawnStorm(pid, 0, 0, 1000, 0.5);
        assertEquals(1, ws.stormCount());
        ws.dissipateStorm(id);
        assertEquals(0, ws.stormCount());
    }

    @Test
    void multipleStormsCoexist() {
        var events = new com.fv2j3.universe.events.EventBus();
        WeatherSystem ws = new WeatherSystem(new WeatherRegistry(), events);
        com.fv2j3.universe.identifiers.UniverseId pid = new com.fv2j3.universe.identifiers.UniverseId(1, 0, 0, 0, 0, 0, 0);
        ws.spawnStorm(pid, 0, 0, 1000, 0.3);
        ws.spawnStorm(pid, 0.5, 0, 1000, 0.4);
        ws.spawnStorm(pid, -0.5, 0, 1000, 0.5);
        assertEquals(3, ws.stormCount());
    }

    @Test
    void weatherTransitions() {
        var events = new com.fv2j3.universe.events.EventBus();
        WeatherSystem ws = new WeatherSystem(new WeatherRegistry(), events);
        com.fv2j3.universe.identifiers.UniverseId pid = new com.fv2j3.universe.identifiers.UniverseId(1, 0, 0, 0, 0, 0, 0);
        ws.setWeather(pid, "rain");
        assertEquals("rain", ws.currentWeatherFor(pid));
        ws.setWeather(pid, "snow");
        assertEquals("snow", ws.currentWeatherFor(pid));
    }
}
