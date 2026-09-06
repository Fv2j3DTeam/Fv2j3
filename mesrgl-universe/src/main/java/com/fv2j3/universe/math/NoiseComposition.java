package com.fv2j3.universe.math;

/**
 * Noise composition utilities. Domain warping and fBm stacks composed
 * over a deterministic {@link SimplexNoise}.
 */
public final class NoiseComposition {
    private NoiseComposition() {}

    /** Domain warp: f(g(p)) composed with the underlying noise function. */
    public static double warped2D(SimplexNoise noise, double x, double y, double warpStrength) {
        double qx = noise.fractal2D(x + 1.7, y + 9.2, 2, 2.0, 0.5);
        double qy = noise.fractal2D(x + 8.3, y + 2.8, 2, 2.0, 0.5);
        return noise.fractal2D(x + warpStrength * qx, y + warpStrength * qy, 4, 2.0, 0.5);
    }

    /** Ridged multifractal — sharp mountain-like features. */
    public static double ridged2D(SimplexNoise noise, double x, double y, int octaves, double lacunarity, double gain) {
        double sum = 0.0;
        double amp = 0.5;
        double freq = 1.0;
        for (int i = 0; i < octaves; i++) {
            double n = 1.0 - Math.abs(noise.noise2D(x * freq, y * freq));
            sum += n * n * amp;
            freq *= lacunarity;
            amp *= gain;
        }
        return sum;
    }

    public static double billow2D(SimplexNoise noise, double x, double y, int octaves, double lacunarity, double gain) {
        double sum = 0.0;
        double amp = 0.5;
        double freq = 1.0;
        for (int i = 0; i < octaves; i++) {
            sum += Math.abs(noise.noise2D(x * freq, y * freq)) * amp;
            freq *= lacunarity;
            amp *= gain;
        }
        return sum;
    }
}