package com.fv2j3.universe;

import com.fv2j3.universe.generation.ClimateGenerator;
import com.fv2j3.universe.generation.PlanetGenerator;
import com.fv2j3.universe.generation.TerrainGenerator;
import com.fv2j3.universe.identifiers.ChunkCoord;
import com.fv2j3.universe.identifiers.UniverseId;
import com.fv2j3.universe.terrain.Chunk;
import com.fv2j3.universe.terrain.VoxelType;
import com.fv2j3.universe.universe.Planet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerrainTest {

    @Test
    void terrainDeterministic() {
        PlanetGenerator pg = new PlanetGenerator();
        UniverseId root = new UniverseId(1, 0, 0, 0, 0, 0, 0);
        Planet p = pg.generate(root, 42L, 1L, 5L, 1L);
        TerrainGenerator tg = new TerrainGenerator();
        Chunk a = tg.generate(p, new ChunkCoord(7, 0, 9));
        Chunk b = tg.generate(p, new ChunkCoord(7, 0, 9));
        for (int y = 0; y < Chunk.CHUNK_EDGE; y++) {
            for (int z = 0; z < Chunk.CHUNK_EDGE; z++) {
                for (int x = 0; x < Chunk.CHUNK_EDGE; x++) {
                    assertEquals(a.voxel(x, y, z), b.voxel(x, y, z));
                }
            }
        }
    }

    @Test
    void terrainHasSomeSolidVoxels() {
        PlanetGenerator pg = new PlanetGenerator();
        UniverseId root = new UniverseId(1, 0, 0, 0, 0, 0, 0);
        Planet p = pg.generate(root, 42L, 1L, 5L, 1L);
        TerrainGenerator tg = new TerrainGenerator();
        Chunk c = tg.generate(p, new ChunkCoord(0, 0, 0));
        int solid = 0;
        for (int y = 0; y < Chunk.CHUNK_EDGE; y++) {
            for (int z = 0; z < Chunk.CHUNK_EDGE; z++) {
                for (int x = 0; x < Chunk.CHUNK_EDGE; x++) {
                    if (VoxelType.isSolid(c.voxel(x, y, z))) solid++;
                }
            }
        }
        assertTrue(solid > 0);
    }

    @Test
    void climateDependsOnLatitude() {
        PlanetGenerator pg = new PlanetGenerator();
        UniverseId root = new UniverseId(1, 0, 0, 0, 0, 0, 0);
        Planet p = pg.generate(root, 42L, 1L, 5L, 1L);
        ClimateGenerator cg = new ClimateGenerator();
        var eq = cg.sample(p, new ChunkCoord(0, 0, 0), 0, 0, 0);
        var pole = cg.sample(p, new ChunkCoord(0, 64, 0), 0, 0, 0);
        // Pole should be cooler (latitude near pi/2).
        assertTrue(pole.temperatureK < eq.temperatureK + 50);
    }
}
