package com.fv2j3.universe.math;

/**
 * 2D simplex noise (deterministic, seeded). Returns values in [-1, 1].
 * Implementation derived from Stefan Gustavson's reference algorithm and adapted
 * for a deterministic seeded permutation table.
 */
public final class SimplexNoise {
    private final int[] perm;
    private final int[] permMod12;

    public SimplexNoise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        XorShift64 rng = new XorShift64(seed);
        for (int i = 255; i > 0; i--) {
            int j = rng.nextInt(0, i + 1);
            int tmp = p[i]; p[i] = p[j]; p[j] = tmp;
        }
        this.perm = new int[512];
        this.permMod12 = new int[512];
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
            permMod12[i] = perm[i] % 12;
        }
    }

    private static final int[][] GRAD3 = {
        {1,1,0}, {-1,1,0}, {1,-1,0}, {-1,-1,0},
        {1,0,1}, {-1,0,1}, {1,0,-1}, {-1,0,-1},
        {0,1,1}, {0,-1,1}, {0,1,-1}, {0,-1,-1}
    };

    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;

    public double noise2D(double xin, double yin) {
        double s = (xin + yin) * F2;
        int i = fastFloor(xin + s);
        int j = fastFloor(yin + s);
        double t = (i + j) * G2;
        double X0 = i - t;
        double Y0 = j - t;
        double x0 = xin - X0;
        double y0 = yin - Y0;

        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; } else { i1 = 0; j1 = 1; }

        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double y2 = y0 - 1.0 + 2.0 * G2;

        int ii = i & 255;
        int jj = j & 255;
        int gi0 = permMod12[ii + perm[jj]];
        int gi1 = permMod12[ii + i1 + perm[jj + j1]];
        int gi2 = permMod12[ii + 1 + perm[jj + 1]];

        double n0 = 0.0, n1 = 0.0, n2 = 0.0;
        double t0 = 0.5 - x0 * x0 - y0 * y0;
        if (t0 >= 0) { t0 *= t0; n0 = t0 * t0 * dot2(GRAD3[gi0], x0, y0); }
        double t1 = 0.5 - x1 * x1 - y1 * y1;
        if (t1 >= 0) { t1 *= t1; n1 = t1 * t1 * dot2(GRAD3[gi1], x1, y1); }
        double t2 = 0.5 - x2 * x2 - y2 * y2;
        if (t2 >= 0) { t2 *= t2; n2 = t2 * t2 * dot2(GRAD3[gi2], x2, y2); }
        return 70.0 * (n0 + n1 + n2);
    }

    public double fractal2D(double x, double y, int octaves, double lacunarity, double gain) {
        double sum = 0.0;
        double amp = 1.0;
        double freq = 1.0;
        double max = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise2D(x * freq, y * freq);
            max += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / max;
    }

    private static double dot2(int[] g, double x, double y) {
        return g[0] * x + g[1] * y;
    }

    private static int fastFloor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}