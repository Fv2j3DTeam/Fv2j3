package io.github.fv2j3dteam.universe.smoke;

import io.github.fv2j3dteam.universe.terrain.Chunk;
import io.github.fv2j3dteam.universe.units.Units;

import java.util.Objects;

/**
 * Volumetric smoke grid (§30, §29). A coarse 3D grid of (density, temperature,
 * velocity) for a chunk. Uses semi-Lagrangian advection, explicit diffusion
 * and dissipation, and temperature-driven buoyancy.
 */
public final class SmokeGrid {

    public static final int GRID_EDGE = 8; // smaller than fluid for memory
    private final GasDefinition gas;
    private final double[] density; // per voxel, normalised 0..1
    private final double[] temperature; // per voxel
    private final double[] vx; // per voxel
    private final double[] vy;
    private final double[] vz;
    private final double[] densityTmp;
    private final double[] temperatureTmp;
    private final double[] solid; // 1 if cell is solid
    private final double ambientTemperatureK;

    public SmokeGrid(GasDefinition gas, double ambientTemperatureK) {
        this.gas = Objects.requireNonNull(gas, "gas");
        this.ambientTemperatureK = ambientTemperatureK;
        this.density = new double[GRID_EDGE * GRID_EDGE * GRID_EDGE];
        this.temperature = new double[GRID_EDGE * GRID_EDGE * GRID_EDGE];
        this.vx = new double[GRID_EDGE * GRID_EDGE * GRID_EDGE];
        this.vy = new double[GRID_EDGE * GRID_EDGE * GRID_EDGE];
        this.vz = new double[GRID_EDGE * GRID_EDGE * GRID_EDGE];
        this.densityTmp = new double[GRID_EDGE * GRID_EDGE * GRID_EDGE];
        this.temperatureTmp = new double[GRID_EDGE * GRID_EDGE * GRID_EDGE];
        this.solid = new double[GRID_EDGE * GRID_EDGE * GRID_EDGE];
    }

    public GasDefinition gas() { return gas; }
    public int index(int x, int y, int z) { return (y * GRID_EDGE + z) * GRID_EDGE + x; }
    public double density(int x, int y, int z) { return density[index(x, y, z)]; }
    public double temperature(int x, int y, int z) { return temperature[index(x, y, z)]; }
    public double vx(int x, int y, int z) { return vx[index(x, y, z)]; }
    public double vy(int x, int y, int z) { return vy[index(x, y, z)]; }
    public double vz(int x, int y, int z) { return vz[index(x, y, z)]; }

    public void addSmoke(int x, int y, int z, double densityDelta, double temperatureK, double initVx, double initVy, double initVz) {
        if (x < 0 || x >= GRID_EDGE || y < 0 || y >= GRID_EDGE || z < 0 || z >= GRID_EDGE) return;
        int ic = index(x, y, z);
        density[ic] = Units.clamp(density[ic] + densityDelta, 0.0, 1.0, "smoke");
        temperature[ic] = temperature[ic] + (temperatureK - temperature[ic]) * Math.min(1.0, densityDelta * 2.0);
        vx[ic] = vx[ic] * 0.5 + initVx * 0.5;
        vy[ic] = vy[ic] * 0.5 + initVy * 0.5;
        vz[ic] = vz[ic] * 0.5 + initVz * 0.5;
    }

    public void importSolids(Chunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        for (int y = 0; y < GRID_EDGE; y++) {
            for (int z = 0; z < GRID_EDGE; z++) {
                for (int x = 0; x < GRID_EDGE; x++) {
                    int cx = x * 2; int cy = y * 2; int cz = z * 2;
                    if (cx >= Chunk.CHUNK_EDGE) cx = Chunk.CHUNK_EDGE - 1;
                    if (cy >= Chunk.CHUNK_EDGE) cy = Chunk.CHUNK_EDGE - 1;
                    if (cz >= Chunk.CHUNK_EDGE) cz = Chunk.CHUNK_EDGE - 1;
                    int v = chunk.voxel(cx, cy, cz);
                    solid[index(x, y, z)] = io.github.fv2j3dteam.universe.terrain.VoxelType.isSolid(v) ? 1.0 : 0.0;
                }
            }
        }
    }

    public void step(double dt) {
        if (dt <= 0) return;
        // Advection
        System.arraycopy(density, 0, densityTmp, 0, density.length);
        System.arraycopy(temperature, 0, temperatureTmp, 0, temperature.length);
        for (int y = 0; y < GRID_EDGE; y++) {
            for (int z = 0; z < GRID_EDGE; z++) {
                for (int x = 0; x < GRID_EDGE; x++) {
                    int ic = index(x, y, z);
                    if (solid[ic] > 0.5) continue;
                    double sx = x - vx[ic] * dt;
                    double sy = y - vy[ic] * dt;
                    double sz = z - vz[ic] * dt;
                    int xi = clamp((int) Math.floor(sx));
                    int yi = clamp((int) Math.floor(sy));
                    int zi = clamp((int) Math.floor(sz));
                    int xi1 = clamp(xi + 1);
                    int yi1 = clamp(yi + 1);
                    int zi1 = clamp(zi + 1);
                    double fx = sx - xi;
                    double fy = sy - yi;
                    double fz = sz - zi;
                    double d = trilinear(densityTmp, xi, yi, zi, xi1, yi1, zi1, fx, fy, fz);
                    double t = trilinear(temperatureTmp, xi, yi, zi, xi1, yi1, zi1, fx, fy, fz);
                    density[ic] = Units.clamp(d, 0.0, 1.0, "smoke");
                    if (Double.isFinite(t)) temperature[ic] = t;
                }
            }
        }
        // Buoyancy
        for (int i = 0; i < density.length; i++) {
            double dtemp = temperature[i] - ambientTemperatureK;
            double buoy = gas.buoyancyPerTempDiff() * dtemp;
            vy[i] = vy[i] + buoy * dt;
            // Dissipation
            density[i] = Math.max(0, density[i] - 0.05 * dt);
            // Cooling toward ambient
            temperature[i] = temperature[i] + (ambientTemperatureK - temperature[i]) * Math.min(1.0, 0.5 * dt);
        }
    }

    public long residentBytes() {
        return (long)(density.length + temperature.length + vx.length + vy.length + vz.length
                + densityTmp.length + temperatureTmp.length + solid.length) * 8L + 64;
    }

    private double trilinear(double[] a, int x0, int y0, int z0, int x1, int y1, int z1, double fx, double fy, double fz) {
        double c000 = a[index(x0, y0, z0)];
        double c100 = a[index(x1, y0, z0)];
        double c010 = a[index(x0, y1, z0)];
        double c110 = a[index(x1, y1, z0)];
        double c001 = a[index(x0, y0, z1)];
        double c101 = a[index(x1, y0, z1)];
        double c011 = a[index(x0, y1, z1)];
        double c111 = a[index(x1, y1, z1)];
        double c00 = c000 * (1 - fx) + c100 * fx;
        double c01 = c001 * (1 - fx) + c101 * fx;
        double c10 = c010 * (1 - fx) + c110 * fx;
        double c11 = c011 * (1 - fx) + c111 * fx;
        double c0 = c00 * (1 - fy) + c10 * fy;
        double c1 = c01 * (1 - fy) + c11 * fy;
        return c0 * (1 - fz) + c1 * fz;
    }

    private int clamp(int i) { return Math.max(0, Math.min(GRID_EDGE - 1, i)); }
}
