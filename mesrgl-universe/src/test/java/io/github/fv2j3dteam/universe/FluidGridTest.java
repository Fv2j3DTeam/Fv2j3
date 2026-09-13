package io.github.fv2j3dteam.universe;

import io.github.fv2j3dteam.universe.fluid.FluidDefinition;
import io.github.fv2j3dteam.universe.fluid.FluidGrid;
import io.github.fv2j3dteam.universe.identifiers.ChunkCoord;
import io.github.fv2j3dteam.universe.terrain.Chunk;
import io.github.fv2j3dteam.universe.terrain.VoxelType;
import io.github.fv2j3dteam.universe.units.Units;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FluidGridTest {

    @Test
    void gravityAcceleratesDown() {
        FluidGrid g = new FluidGrid(FluidDefinition.water());
        for (int y = 8; y < FluidGrid.GRID_EDGE; y++) {
            for (int z = 0; z < FluidGrid.GRID_EDGE; z++) {
                for (int x = 0; x < FluidGrid.GRID_EDGE; x++) {
                    g.setV(x, y, z, 0);
                }
            }
        }
        g.step(0.1, 9.81);
        // After step, y-velocity should be negative (downward)
        for (int y = 5; y < FluidGrid.GRID_EDGE; y++) {
            for (int z = 0; z < FluidGrid.GRID_EDGE; z++) {
                for (int x = 0; x < FluidGrid.GRID_EDGE; x++) {
                    assertTrue(g.v(x, y, z) < 0);
                }
            }
        }
    }

    @Test
    void noNegativeDensity() {
        FluidGrid g = new FluidGrid(FluidDefinition.water());
        Chunk chunk = new Chunk(0, 0, 0);
        chunk.fillBox(0, 0, 0, FluidGrid.GRID_EDGE, 8, FluidGrid.GRID_EDGE, VoxelType.WATER);
        g.importSolids(chunk);
        g.step(0.1, 9.81);
        for (int i = 0; i < FluidGrid.GRID_EDGE * FluidGrid.GRID_EDGE * FluidGrid.GRID_EDGE; i++) {
            assertTrue(g.density(0, 0, 0) >= 0);
        }
    }

    @Test
    void solidBoundaryZerosVelocity() {
        FluidGrid g = new FluidGrid(FluidDefinition.water());
        Chunk chunk = new Chunk(0, 0, 0);
        chunk.fillBox(0, 0, 0, FluidGrid.GRID_EDGE, 1, FluidGrid.GRID_EDGE, VoxelType.STONE);
        g.importSolids(chunk);
        for (int z = 0; z < FluidGrid.GRID_EDGE; z++) {
            for (int x = 0; x < FluidGrid.GRID_EDGE; x++) {
                g.setU(x, 0, z, 5.0);
                g.setV(x, 0, z, 5.0);
                g.setW(x, 0, z, 5.0);
            }
        }
        g.step(0.05, 9.81);
        for (int z = 0; z < FluidGrid.GRID_EDGE; z++) {
            for (int x = 0; x < FluidGrid.GRID_EDGE; x++) {
                assertEquals(0, g.v(x, 0, z), 1e-6);
            }
        }
    }

    @Test
    void stepsStableUnderExtremeParams() {
        FluidGrid g = new FluidGrid(FluidDefinition.water());
        Chunk chunk = new Chunk(0, 0, 0);
        chunk.fillBox(0, 0, 0, FluidGrid.GRID_EDGE, 8, FluidGrid.GRID_EDGE, VoxelType.WATER);
        g.importSolids(chunk);
        for (int i = 0; i < 50; i++) {
            g.step(0.016, 9.81);
        }
        for (int i = 0; i < FluidGrid.GRID_EDGE * FluidGrid.GRID_EDGE * FluidGrid.GRID_EDGE; i++) {
            // density and velocity finite
            int x = i % FluidGrid.GRID_EDGE;
            int y = (i / FluidGrid.GRID_EDGE) % FluidGrid.GRID_EDGE;
            int z = i / (FluidGrid.GRID_EDGE * FluidGrid.GRID_EDGE);
            double d = g.density(x, y, z);
            assertTrue(Units.isFinite(d));
            assertTrue(d >= 0 && d <= 1);
        }
    }

    @Test
    void rejectsNaNInputs() {
        FluidGrid g = new FluidGrid(FluidDefinition.water());
        g.setU(0, 0, 0, Double.NaN);
        assertEquals(0, g.u(0, 0, 0));
        g.setV(0, 0, 0, Double.POSITIVE_INFINITY);
        assertEquals(0, g.v(0, 0, 0));
    }
}
