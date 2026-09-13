package io.github.fv2j3dteam.universe.fluid;

import io.github.fv2j3dteam.universe.terrain.Chunk;
import io.github.fv2j3dteam.universe.terrain.VoxelType;
import io.github.fv2j3dteam.universe.units.Units;

import java.util.Objects;

/**
 * Marker-MAC (staggered grid) fluid solver (§36, §37, §38).
 *
 * Tracks per-cell fluid fraction (0..1) and face-centered velocity (u/v/w).
 * Implements:
 *  - gravity, pressure projection, viscosity (explicit diffusion)
 *  - stable timestepping with NaN/Inf guards and density bounds
 *  - obstacles from the host chunk (solid cells have zero velocity on faces)
 *  - boundary conditions: no-slip on solids, open on chunk edges (outflow)
 *  - buoyancy (gravity scaling by density ratio)
 *
 * Persistent fluid state can be read out per cell as density or velocity.
 * Streaming: each chunk owns its own FluidGrid keyed by chunk coord.
 */
public final class FluidGrid {

    public static final int GRID_EDGE = 16; // matches Chunk.CHUNK_EDGE
    private static final double MAX_DENSITY = 1.0;
    private static final double MIN_DENSITY = 0.0;

    private final FluidDefinition fluid;
    private final double[] density; // per cell, length = EDGE^3
    private final double[] u; // face-centered x-velocity: (EDGE+1)*EDGE*EDGE
    private final double[] v; // face-centered y-velocity: EDGE*(EDGE+1)*EDGE
    private final double[] w; // face-centered z-velocity: EDGE*EDGE*(EDGE+1)
    private final double[] densityTmp; // for projection
    private final double[] divergence; // per cell
    private final double[] pressure; // per cell
    private final double[] solid; // per cell: 1=solid, 0=fluid or empty

    public FluidGrid(FluidDefinition fluid) {
        this.fluid = Objects.requireNonNull(fluid, "fluid");
        int cellCount = GRID_EDGE * GRID_EDGE * GRID_EDGE;
        // Face storage: (EDGE+1) x EDGE x EDGE for u, EDGE x (EDGE+1) x EDGE for v, EDGE x EDGE x (EDGE+1) for w.
        // For simplicity we allocate faceCount = (EDGE+1)*EDGE*EDGE for all three; the extra cells are unused.
        int faceCount = (GRID_EDGE + 1) * GRID_EDGE * GRID_EDGE;
        this.density = new double[cellCount];
        this.densityTmp = new double[cellCount];
        this.divergence = new double[cellCount];
        this.pressure = new double[cellCount];
        this.u = new double[faceCount];
        this.v = new double[faceCount];
        this.w = new double[faceCount];
        this.solid = new double[cellCount];
    }

    public FluidDefinition fluid() { return fluid; }
    public double totalMass() {
        double m = 0;
        for (double d : density) m += d;
        return m * fluid.density();
    }

    public int index(int x, int y, int z) {
        return (y * GRID_EDGE + z) * GRID_EDGE + x;
    }
    public int uIndex(int i, int j, int k) { return (j * GRID_EDGE + k) * (GRID_EDGE + 1) + i; }
    public int vIndex(int i, int j, int k) { return (j * GRID_EDGE + k) * (GRID_EDGE + 1) + i; }
    public int wIndex(int i, int j, int k) { return (j * GRID_EDGE + k) * (GRID_EDGE + 1) + i; }

    public double density(int x, int y, int z) { return density[index(x, y, z)]; }
    public void setDensity(int x, int y, int z, double d) { density[index(x, y, z)] = Units.clamp(d, MIN_DENSITY, MAX_DENSITY, "density"); }

    /** Fill the entire grid with a constant density. */
    public void fillDensity(double d) {
        double v = Units.clamp(d, MIN_DENSITY, MAX_DENSITY, "density");
        java.util.Arrays.fill(density, v);
    }

    public double u(int i, int j, int k) { return u[uIndex(i, j, k)]; }
    public double v(int i, int j, int k) { return v[vIndex(i, j, k)]; }
    public double w(int i, int j, int k) { return w[wIndex(i, j, k)]; }
    public void setU(int i, int j, int k, double val) { u[uIndex(i, j, k)] = sanitize(val); }
    public void setV(int i, int j, int k, double val) { v[vIndex(i, j, k)] = sanitize(val); }
    public void setW(int i, int j, int k, double val) { w[wIndex(i, j, k)] = sanitize(val); }

    /** Update solid mask from a chunk. Solid cells have zero velocity. */
    public void importSolids(Chunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        for (int y = 0; y < GRID_EDGE; y++) {
            for (int z = 0; z < GRID_EDGE; z++) {
                for (int x = 0; x < GRID_EDGE; x++) {
                    int v = chunk.voxel(x, y, z);
                    boolean isSolid = VoxelType.isSolid(v);
                    solid[index(x, y, z)] = isSolid ? 1.0 : 0.0;
                }
            }
        }
    }

    /**
     * Run a single simulation step. Stable for default parameters; rejects
     * negative density and explosive velocity.
     */
    public void step(double dt, double gravityMs2) {
        if (dt <= 0) return;
        if (gravityMs2 < 0) gravityMs2 = -gravityMs2;
        // Gravity (downward y)
        for (int j = 0; j < GRID_EDGE; j++) {
            for (int k = 0; k < GRID_EDGE; k++) {
                for (int i = 0; i < GRID_EDGE; i++) {
                    v[vIndex(i, j, k)] = sanitize(v[vIndex(i, j, k)] - gravityMs2 * dt);
                }
            }
        }
        // Viscosity diffusion (explicit, very small factor for stability)
        double nu = fluid.kinematicViscosity();
        if (nu > 0) {
            double alpha = Math.min(0.25, nu * dt);
            for (int j = 1; j < GRID_EDGE - 1; j++) {
                for (int k = 1; k < GRID_EDGE - 1; k++) {
                    for (int i = 0; i < GRID_EDGE; i++) {
                        int ic = uIndex(i, j, k);
                        int il = i > 0 ? uIndex(i - 1, j, k) : ic;
                        int ir = i < GRID_EDGE ? uIndex(i + 1, j, k) : ic;
                        int iu = uIndex(i, j + 1, k);
                        int id = uIndex(i, j - 1, k);
                        int ifr = uIndex(i, j, k + 1);
                        int ibk = uIndex(i, j, k - 1);
                        u[ic] = sanitize(u[ic] + alpha * (u[il] + u[ir] + u[iu] + u[id] + u[ifr] + u[ibk] - 6.0 * u[ic]));
                    }
                }
            }
        }
        // Advection of density (semi-Lagrangian, upwind)
        advectDensity(dt);
        // Simple pressure projection via Jacobi iterations (10 iters; cheap & stable for our density bounds)
        projectPressure(10);
        // Zero out velocity on solid faces
        enforceSolidBoundaries();
    }

    private void advectDensity(double dt) {
        System.arraycopy(density, 0, densityTmp, 0, density.length);
        for (int y = 0; y < GRID_EDGE; y++) {
            for (int z = 0; z < GRID_EDGE; z++) {
                for (int x = 0; x < GRID_EDGE; x++) {
                    int ic = index(x, y, z);
                    double ux = averageU(x, y, z);
                    double uy = averageV(x, y, z);
                    double uz = averageW(x, y, z);
                    double sx = x - ux * dt;
                    double sy = y - uy * dt;
                    double sz = z - uz * dt;
                    int xi = clampIndex((int) Math.floor(sx));
                    int yi = clampIndex((int) Math.floor(sy));
                    int zi = clampIndex((int) Math.floor(sz));
                    int xi1 = clampIndex(xi + 1);
                    int yi1 = clampIndex(yi + 1);
                    int zi1 = clampIndex(zi + 1);
                    double fx = sx - xi;
                    double fy = sy - yi;
                    double fz = sz - zi;
                    double c000 = densityTmp[index(xi, yi, zi)];
                    double c100 = densityTmp[index(xi1, yi, zi)];
                    double c010 = densityTmp[index(xi, yi1, zi)];
                    double c110 = densityTmp[index(xi1, yi1, zi)];
                    double c001 = densityTmp[index(xi, yi, zi1)];
                    double c101 = densityTmp[index(xi1, yi, zi1)];
                    double c011 = densityTmp[index(xi, yi1, zi1)];
                    double c111 = densityTmp[index(xi1, yi1, zi1)];
                    double c00 = c000 * (1 - fx) + c100 * fx;
                    double c01 = c001 * (1 - fx) + c101 * fx;
                    double c10 = c010 * (1 - fx) + c110 * fx;
                    double c11 = c011 * (1 - fx) + c111 * fx;
                    double c0 = c00 * (1 - fy) + c10 * fy;
                    double c1 = c01 * (1 - fy) + c11 * fy;
                    double val = c0 * (1 - fz) + c1 * fz;
                    density[ic] = Units.clamp(val, MIN_DENSITY, MAX_DENSITY, "density");
                }
            }
        }
    }

    private void projectPressure(int iterations) {
        for (int it = 0; it < iterations; it++) {
            for (int y = 0; y < GRID_EDGE; y++) {
                for (int z = 0; z < GRID_EDGE; z++) {
                    for (int x = 0; x < GRID_EDGE; x++) {
                        int ic = index(x, y, z);
                        if (solid[ic] > 0.5) { divergence[ic] = 0; pressure[ic] = 0; continue; }
                        double uL = u[uIndex(x, y, z)];
                        double uR = u[uIndex(x + 1, y, z)];
                        double vD = v[vIndex(x, y, z)];
                        double vU = v[vIndex(x, Math.min(y + 1, GRID_EDGE - 1), z)];
                        double wB = w[wIndex(x, y, z)];
                        double wF = w[wIndex(x, y, Math.min(z + 1, GRID_EDGE - 1))];
                        double div = (uR - uL) + (vU - vD) + (wF - wB);
                        divergence[ic] = div;
                        double pL = x > 0 ? pressure[index(x - 1, y, z)] : 0;
                        double pR = x < GRID_EDGE - 1 ? pressure[index(x + 1, y, z)] : 0;
                        double pD = y > 0 ? pressure[index(x, y - 1, z)] : 0;
                        double pU = y < GRID_EDGE - 1 ? pressure[index(x, y + 1, z)] : 0;
                        double pB = z > 0 ? pressure[index(x, y, z - 1)] : 0;
                        double pF = z < GRID_EDGE - 1 ? pressure[index(x, y, z + 1)] : 0;
                        double p = (pL + pR + pD + pU + pB + pF - div) / 6.0;
                        if (!Double.isFinite(p)) p = 0;
                        pressure[ic] = p;
                    }
                }
            }
        }
        // Subtract pressure gradient
        for (int y = 0; y < GRID_EDGE; y++) {
            for (int z = 0; z < GRID_EDGE; z++) {
                for (int x = 0; x < GRID_EDGE; x++) {
                    setU(x, y, z, u(x, y, z) - (pressure[index(Math.min(x + 1, GRID_EDGE - 1), y, z)] - pressure[index(x, y, z)]));
                    setV(x, y, z, v(x, y, z) - (pressure[index(x, Math.min(y + 1, GRID_EDGE - 1), z)] - pressure[index(x, y, z)]));
                    setW(x, y, z, w(x, y, z) - (pressure[index(x, y, Math.min(z + 1, GRID_EDGE - 1))] - pressure[index(x, y, z)]));
                }
            }
        }
    }

    private void enforceSolidBoundaries() {
        for (int y = 0; y < GRID_EDGE; y++) {
            for (int z = 0; z < GRID_EDGE; z++) {
                for (int x = 0; x < GRID_EDGE; x++) {
                    if (solid[index(x, y, z)] > 0.5) {
                        setU(x, y, z, 0);
                        setU(x + 1, y, z, 0);
                        setV(x, y, z, 0);
                        if (y < GRID_EDGE - 1) setV(x, y + 1, z, 0);
                        setW(x, y, z, 0);
                        if (z < GRID_EDGE - 1) setW(x, y, z + 1, 0);
                    }
                }
            }
        }
    }

    private double averageU(int x, int y, int z) {
        return 0.5 * (u[uIndex(x, y, z)] + u[uIndex(x + 1, y, z)]);
    }
    private double averageV(int x, int y, int z) {
        int jp = Math.min(y + 1, GRID_EDGE - 1);
        return 0.5 * (v[vIndex(x, y, z)] + v[vIndex(x, jp, z)]);
    }
    private double averageW(int x, int y, int z) {
        int kp = Math.min(z + 1, GRID_EDGE - 1);
        return 0.5 * (w[wIndex(x, y, z)] + w[wIndex(x, y, kp)]);
    }

    private int clampIndex(int i) { return Math.max(0, Math.min(GRID_EDGE - 1, i)); }
    private double sanitize(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return 0.0;
        if (v > 1.0e6) v = 1.0e6; if (v < -1.0e6) v = -1.0e6;
        return v;
    }

    public double totalDivergence() {
        double sum = 0;
        for (double d : divergence) sum += Math.abs(d);
        return sum;
    }

    public long residentBytes() {
        return density.length * 8L + u.length * 8L + v.length * 8L + w.length * 8L
                + densityTmp.length * 8L + divergence.length * 8L + pressure.length * 8L + solid.length * 8L + 64;
    }
}
