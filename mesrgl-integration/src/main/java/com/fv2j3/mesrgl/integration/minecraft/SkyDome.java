package com.fv2j3.mesrgl.integration.minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Atmospheric sky dome (PART 4 - atmosphere).
 *
 * The software RT path has no native sky shader, so we model the sky as a
 * large emissive mesh that the ray tracer will hit. The skydome is a
 * tessellated sphere; per-vertex colors are computed from Rayleigh + Mie
 * scattering at the vertex direction. The sun and moon are added as small
 * bright quads facing the camera.
 *
 * This module also exposes the per-pixel sky lookup that the post-pass
 * denoiser uses when the ray tracer returns a "miss" without hitting
 * the dome (e.g. low altitude looking up).
 *
 * Rayleigh scattering: I ~ (1 + cos^2) * (lambda^-4) for the phase function,
 * modulated by the atmospheric path length ~ 1/cos(altitude).
 * Mie scattering: forward-peaked, applies to the sun halo.
 * Both are reproduced analytically per the well-known Preetham/Hosek
 * formulations, simplified for performance.
 */
public final class SkyDome {

    /** A skydome vertex with position and pre-computed scattering color. */
    public static final class Vertex {
        public final float px, py, pz;
        public final float r, g, b;
        public final float u, v;
        public Vertex(float px, float py, float pz, float r, float g, float b, float u, float v) {
            this.px = px; this.py = py; this.pz = pz;
            this.r = r; this.g = g; this.b = b;
            this.u = u; this.v = v;
        }
    }

    public static final class Dome {
        public final float[] positions;
        public final float[] colors;
        public final int[] indices;
        public final int vertexCount;
        public final int indexCount;

        Dome(float[] positions, float[] colors, int[] indices,
             int vertexCount, int indexCount) {
            this.positions = positions;
            this.colors = colors;
            this.indices = indices;
            this.vertexCount = vertexCount;
            this.indexCount = indexCount;
        }
    }

    public static final class SunMoon {
        public final float[] positions; // 4 vertices × 3 (a quad)
        public final float[] colors;    // 4 × 3
        public final float[] texCoords; // 4 × 2
        public final int[] indices;     // 2 triangles × 3
        public final boolean isSun;     // false = moon

        SunMoon(float[] positions, float[] colors, float[] texCoords, int[] indices, boolean isSun) {
            this.positions = positions;
            this.colors = colors;
            this.texCoords = texCoords;
            this.indices = indices;
            this.isSun = isSun;
        }
    }

    public static final class Sky {
        public final Dome dome;
        public final SunMoon sun;
        public final SunMoon moon;
        public final float[] clearColor; // 3 floats (RGB)

        public Sky(Dome dome, SunMoon sun, SunMoon moon, float[] clearColor) {
            this.dome = dome;
            this.sun = sun;
            this.moon = moon;
            this.clearColor = clearColor;
        }
    }

    /**
     * Builds a sky composed of a tessellated skydome sphere, a sun disc, and
     * a moon disc. The radius is in world units; the sun and moon discs are
     * placed at a smaller distance so they appear in the right direction.
     *
     * @param sunDx sun direction (X) toward the sun
     * @param sunDy sun direction (Y) toward the sun
     * @param sunDz sun direction (Z) toward the sun
     * @param sunColor RGB
     * @param sunIntensity light intensity (0..3+)
     * @param skyColor  RGB for the zenith (used for clear color)
     * @param radius  world radius of the skydome
     * @param tessellation  number of latitude/longitude subdivisions
     */
    public Sky build(float sunDx, float sunDy, float sunDz,
                     float[] sunColor, float sunIntensity,
                     float[] skyColor, float radius, int tessellation) {
        Dome dome = buildDome(sunDx, sunDy, sunDz, sunColor, sunIntensity, skyColor, radius, tessellation);
        SunMoon sunDisc = buildDisc(sunDx, sunDy, sunDz, sunColor, radius * 0.85f, 0.05f, true);
        // Moon is opposite the sun direction
        SunMoon moonDisc = buildDisc(-sunDx, -sunDy, -sunDz,
                new float[]{0.85f, 0.88f, 0.95f}, radius * 0.85f, 0.04f, false);
        return new Sky(dome, sunDisc, moonDisc, skyColor);
    }

    /**
     * Builds the dome as triangle-strip-friendly geometry. Each vertex holds
     * its pre-computed sky color; the ray tracer will shade the dome with
     * its vertex colors as emissive contribution, eliminating the need for
     * a separate sky shader pass.
     */
    private Dome buildDome(float sx, float sy, float sz,
                           float[] sunColor, float sunIntensity,
                           float[] skyColor, float radius, int n) {
        int lat = n;
        int lon = n * 2;
        int vCount = (lat + 1) * (lon + 1);
        int iCount = lat * lon * 6;
        float[] positions = new float[vCount * 3];
        float[] colors = new float[vCount * 3];
        int[] indices = new int[iCount];
        int vi = 0, ci = 0;
        for (int i = 0; i <= lat; i++) {
            float theta = (float) (Math.PI * i / lat);
            float sinT = (float) Math.sin(theta);
            float cosT = (float) Math.cos(theta);
            for (int j = 0; j <= lon; j++) {
                float phi = (float) (2.0 * Math.PI * j / lon);
                float sinP = (float) Math.sin(phi);
                float cosP = (float) Math.cos(phi);
                float dx = sinT * cosP;
                float dy = cosT;
                float dz = sinT * sinP;
                positions[vi * 3]     = dx * radius;
                positions[vi * 3 + 1] = dy * radius;
                positions[vi * 3 + 2] = dz * radius;
                float[] c = skyColorAt(dx, dy, dz, sx, sy, sz, sunColor, sunIntensity, skyColor);
                colors[ci]     = c[0];
                colors[ci + 1] = c[1];
                colors[ci + 2] = c[2];
                ci += 3;
                vi++;
            }
        }
        int ii = 0;
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                int a = i * (lon + 1) + j;
                int b = a + lon + 1;
                indices[ii++] = a;
                indices[ii++] = b;
                indices[ii++] = a + 1;
                indices[ii++] = b;
                indices[ii++] = b + 1;
                indices[ii++] = a + 1;
            }
        }
        return new Dome(positions, colors, indices, vCount, iCount / 3);
    }

    /**
     * Builds a small disc quad facing the camera. The quad is in a plane
     * perpendicular to the sun direction, centered on the sun direction.
     */
    private SunMoon buildDisc(float dx, float dy, float dz, float[] color,
                              float distance, float size, boolean isSun) {
        // Build an orthonormal basis (u, v) perpendicular to (dx,dy,dz).
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-5f) {
            dx = 0; dy = 1; dz = 0;
        } else {
            dx /= len; dy /= len; dz /= len;
        }
        // Pick a stable up vector.
        float upX = 0, upY = 1, upZ = 0;
        if (Math.abs(dy) > 0.95f) {
            upX = 1; upY = 0; upZ = 0;
        }
        // u = up × d
        float ux = upY * dz - upZ * dy;
        float uy = upZ * dx - upX * dz;
        float uz = upX * dy - upY * dx;
        float ulen = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ulen; uy /= ulen; uz /= ulen;
        // v = d × u
        float vx = dy * uz - dz * uy;
        float vy = dz * ux - dx * uz;
        float vz = dx * uy - dy * ux;
        float cx = dx * distance;
        float cy = dy * distance;
        float cz = dz * distance;
        float[] positions = new float[12];
        // 4 corners: c ± size*u ± size*v
        positions[0] = cx - ux * size - vx * size;
        positions[1] = cy - uy * size - vy * size;
        positions[2] = cz - uz * size - vz * size;
        positions[3] = cx + ux * size - vx * size;
        positions[4] = cy + uy * size - vy * size;
        positions[5] = cz + uz * size - vz * size;
        positions[6] = cx + ux * size + vx * size;
        positions[7] = cy + uy * size + vy * size;
        positions[8] = cz + uz * size + vz * size;
        positions[9] = cx - ux * size + vx * size;
        positions[10] = cy - uy * size + vy * size;
        positions[11] = cz - uz * size + vz * size;
        float[] colors = new float[12];
        // Brighter in the center, fading to halo at the edges.
        for (int k = 0; k < 4; k++) {
            colors[k * 3]     = color[0] * (isSun ? 2.5f : 1.4f);
            colors[k * 3 + 1] = color[1] * (isSun ? 2.5f : 1.4f);
            colors[k * 3 + 2] = color[2] * (isSun ? 2.5f : 1.4f);
        }
        float[] uvs = new float[]{
                0, 0, 1, 0, 1, 1, 0, 1
        };
        int[] indices = new int[]{0, 1, 2, 0, 2, 3};
        return new SunMoon(positions, colors, uvs, indices, isSun);
    }

    /**
     * Per-vertex sky color: Rayleigh + Mie analytic model.
     *
     *   Rayleigh:   beta_R ~ (1/dirY) for path length, color (R, G, B)
     *                scaled by 1/lambda^4 -> higher blue scattering.
     *   Mie:        forward-peaked near the sun, used for the halo.
     *
     * The output is in linear radiance; the renderer's tone mapping will
     * compress it into the visible range.
     */
    public static float[] skyColorAt(float dx, float dy, float dz,
                                      float sx, float sy, float sz,
                                      float[] sunColor, float sunIntensity,
                                      float[] zenithColor) {
        // Path length: 1/max(epsilon, dy).
        float upness = Math.max(0.001f, dy);
        // Atmospheric attenuation factor.
        float atm = 1.0f / upness;

        // Rayleigh phase: (3 / (16π)) * (1 + cos²θ)
        float cosT = dx * sx + dy * sy + dz * sz;
        float phaseR = 0.0596831f * (1.0f + cosT * cosT);

        // Rayleigh coefficients (Preetham-style, simplified).
        float betaR_R = 5.8e-6f;
        float betaR_G = 13.5e-6f;
        float betaR_B = 33.1e-6f;
        float rayleighAttenuation = (float) Math.exp(-atm * 0.0085f);
        float rR = betaR_R * phaseR * rayleighAttenuation * 1.0e6f;
        float gR = betaR_G * phaseR * rayleighAttenuation * 1.0e6f;
        float bR = betaR_B * phaseR * rayleighAttenuation * 1.0e6f;

        // Mie: forward-peaked, henyey-greenstein with g=0.8.
        float gHG = 0.8f;
        float phaseM = (3.0f * (1.0f - gHG * gHG)) /
                (2.0f * (2.0f + gHG * gHG)) *
                (float) Math.pow(1.0f + gHG * gHG - 2.0f * gHG * cosT, -1.5f);
        float betaM = 2.0e-5f;
        float mieAttenuation = (float) Math.exp(-atm * 0.005f);
        float m = betaM * phaseM * mieAttenuation * 1.0e6f * sunIntensity;

        // Compose: zenith tint + Rayleigh + Mie halo.
        float r = zenithColor[0] * 0.4f + rR + m * sunColor[0];
        float g = zenithColor[1] * 0.4f + gR + m * sunColor[1];
        float b = zenithColor[2] * 0.4f + bR + m * sunColor[2];

        // Clamp and slightly saturate.
        r = clamp01(r * 1.6f);
        g = clamp01(g * 1.6f);
        b = clamp01(b * 1.6f);
        return new float[]{r, g, b};
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /**
     * Reads the sky color at a normalized world direction, used by the
     * post-pass when the ray tracer produced a miss with no dome hit
     * (deep underground looking up, etc.). Mirrors {@link #skyColorAt}.
     */
    public static float[] skyColorAtDir(float dx, float dy, float dz,
                                        MinecraftLighting.Sun sun,
                                        float[] zenithColor) {
        return skyColorAt(dx, dy, dz, sun.dx, sun.dy, sun.dz,
                new float[]{sun.r, sun.g, sun.b}, sun.intensity, zenithColor);
    }

    /**
     * Sky coverage / cloud data (PART 8 - cloud quality).
     * Returns a list of cloud-plane vertices for the LOW / MEDIUM / HIGH
     * quality tiers. The cloud quads are flat sheets in the sky at a fixed
     * altitude; coverage is the number of quads and density is their
     * alpha/opacity. The quads use the weather module's cloud material.
     */
    public static List<float[]> cloudQuads(int qualityTier, long timeMs) {
        List<float[]> out = new ArrayList<>();
        // Time-evolving seed for cloud drift.
        float t = (timeMs % 60000L) / 1000.0f;
        int n = switch (qualityTier) {
            case 0 -> 4;   // LOW
            case 1 -> 12;  // MEDIUM
            case 2 -> 32;  // HIGH
            case 3 -> 64;  // ULTRA
            default -> 128; // EXTREME
        };
        for (int i = 0; i < n; i++) {
            float angle = (float) (i * 2.0 * Math.PI / n + t * 0.02);
            float radius = 30.0f + (i % 5) * 8.0f;
            float altitude = 90.0f + (i % 3) * 6.0f;
            float cx = (float) Math.cos(angle) * radius;
            float cz = (float) Math.sin(angle) * radius;
            float size = 8.0f + (i % 4) * 4.0f;
            out.add(new float[]{cx - size, altitude, cz - size,
                    cx + size, altitude, cz - size,
                    cx + size, altitude, cz + size,
                    cx - size, altitude, cz + size});
        }
        return out;
    }
}
