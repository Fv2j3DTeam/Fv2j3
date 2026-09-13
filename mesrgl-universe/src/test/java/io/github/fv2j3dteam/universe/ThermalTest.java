package io.github.fv2j3dteam.universe;

import io.github.fv2j3dteam.universe.lod.LOD;
import io.github.fv2j3dteam.universe.thermal.PhaseTransitions;
import io.github.fv2j3dteam.universe.thermal.ThermalField;
import io.github.fv2j3dteam.universe.identifiers.ChunkCoord;
import io.github.fv2j3dteam.universe.terrain.Chunk;
import io.github.fv2j3dteam.universe.terrain.VoxelType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThermalTest {

    @Test
    void conductionEqualisesTemperature() {
        ThermalField tf = new ThermalField();
        Chunk c = new Chunk(0, 0, 0);
        c.fillBox(0, 0, 0, 8, 8, 8, VoxelType.STONE);
        ChunkCoord coord = new ChunkCoord(0, 0, 0);
        for (int y = 0; y < ThermalField.GRID_EDGE; y++) {
            for (int z = 0; z < ThermalField.GRID_EDGE; z++) {
                for (int x = 0; x < ThermalField.GRID_EDGE; x++) {
                    tf.setTemperature(coord, x, y, z, 293.15);
                }
            }
        }
        // Hot spot
        tf.setTemperature(coord, 4, 4, 4, 600.0);
        for (int i = 0; i < 100; i++) tf.step(coord, c, 0, 293.15, 0.05);
        double t = tf.temperatureAt(coord, 4, 4, 4);
        assertTrue(t < 600.0);
        assertTrue(t > 293.15);
    }

    @Test
    void waterFreezesWhenCold() {
        ThermalField tf = new ThermalField();
        Chunk c = new Chunk(0, 0, 0);
        c.fillBox(4, 4, 4, 5, 5, 5, VoxelType.WATER);
        ChunkCoord coord = new ChunkCoord(0, 0, 0);
        tf.setTemperature(coord, 4, 4, 4, 200.0);
        new PhaseTransitions().apply(coord, c, tf);
        assertEquals(VoxelType.ICE, c.voxel(4, 4, 4));
    }

    @Test
    void iceMeltsWhenHot() {
        ThermalField tf = new ThermalField();
        Chunk c = new Chunk(0, 0, 0);
        c.fillBox(4, 4, 4, 5, 5, 5, VoxelType.ICE);
        ChunkCoord coord = new ChunkCoord(0, 0, 0);
        tf.setTemperature(coord, 4, 4, 4, 290.0);
        new PhaseTransitions().apply(coord, c, tf);
        assertEquals(VoxelType.WATER, c.voxel(4, 4, 4));
    }

    @Test
    void noNaNPropagation() {
        ThermalField tf = new ThermalField();
        Chunk c = new Chunk(0, 0, 0);
        c.fillBox(0, 0, 0, 8, 8, 8, VoxelType.STONE);
        ChunkCoord coord = new ChunkCoord(0, 0, 0);
        tf.setTemperature(coord, 0, 0, 0, Double.NaN);
        tf.step(coord, c, 0, 293.15, 0.05);
        assertTrue(Double.isFinite(tf.temperatureAt(coord, 0, 0, 0)));
    }

    @Test
    void lodForDistance() {
        assertEquals(LOD.GLOBAL_CLIMATE, LOD.forDistance(20000));
        assertEquals(LOD.REGIONAL, LOD.forDistance(2000));
        assertEquals(LOD.CHUNK, LOD.forDistance(200));
        assertEquals(LOD.DETAILED, LOD.forDistance(50));
    }
}
