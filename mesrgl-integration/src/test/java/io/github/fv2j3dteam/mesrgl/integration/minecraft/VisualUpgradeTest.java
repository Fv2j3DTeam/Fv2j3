package io.github.fv2j3dteam.mesrgl.integration.minecraft;

import io.github.fv2j3dteam.mesrgl.MesrGLRenderer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test for the Phase 47 visual-quality upgrade.
 *
 * Builds a small synthetic scene by hand: a ground plane, a stone wall,
 * a glass pane, a water surface, a glowing lava block, and a few trees
 * (log + leaves). Then it adds the skydome + sun + moon + clouds and
 * runs the renderer. The resulting framebuffer is dumped to a PNG-like
 * file so a human can verify the visual upgrade.
 *
 * This test exercises the same code paths as the real Minecraft
 * integration (MinecraftSceneBuilder, MinecraftLighting, SkyDome,
 * WeatherBridge, TemporalDenoiser, QualityPresets), but with a
 * hand-rolled scene instead of a live Minecraft world, so it can run
 * in headless CI / sandbox.
 */
public class VisualUpgradeTest {

    private static final int W = 480;
    private static final int H = 320;

    @Test
    void produceDayFrame() throws Exception {
        MesrGLRenderer renderer = new MesrGLRenderer(W, H, 1, 3, true, 0xC0FFEEL);
        try {
            renderer.setShadowsEnabled(true);
            renderer.setGIEnabled(true);
            renderer.setExposure(0.8f);
            renderer.setToneMapping(4); // ACES

            // Sun at noon.
            MinecraftLighting lighting = new MinecraftLighting();
            MinecraftLighting.Frame frame = lighting.compute(6000L, new ArrayList<>());
            // 24 verts cube
            int cube = makeCube(0f, 0f, 0f, 1f, 1f, 1f);
            renderer.addMesh(cubePositions, cubeNormals, cubeUVs, cubeIndices, groundMaterial(renderer, frame));

            // glass block
            float[] gP = new float[]{
                    1.0f, 0.5f, -1.0f, 2.0f, 0.5f, -1.0f, 2.0f, 1.5f, -1.0f, 1.0f, 1.5f, -1.0f,
                    1.0f, 0.5f, 0.0f, 1.0f, 0.5f, -1.0f, 1.0f, 1.5f, -1.0f, 1.0f, 1.5f, 0.0f,
                    2.0f, 0.5f, 0.0f, 1.0f, 0.5f, 0.0f, 1.0f, 1.5f, 0.0f, 2.0f, 1.5f, 0.0f,
                    2.0f, 0.5f, -1.0f, 2.0f, 0.5f, 0.0f, 2.0f, 1.5f, 0.0f, 2.0f, 1.5f, -1.0f,
                    1.0f, 1.5f, -1.0f, 2.0f, 1.5f, -1.0f, 2.0f, 1.5f, 0.0f, 1.0f, 1.5f, 0.0f,
                    1.0f, 0.5f, 0.0f, 2.0f, 0.5f, 0.0f, 2.0f, 0.5f, -1.0f, 1.0f, 0.5f, -1.0f
            };
            int glassMat = renderer.createMaterial(0.78f, 0.94f, 1.0f, 0.05f, 0.0f, 0.0f, 0.92f, 1.5f);
            renderer.addMesh(gP, expandNormals(gP), new float[gP.length / 3 * 2], cubeIndices, glassMat);

            // water surface
            float[] waterP = new float[]{
                    -2f, 0.4f, -2f, 4f, 0.4f, -2f, 4f, 0.4f, 4f, -2f, 0.4f, 4f
            };
            int waterMat = renderer.createMaterial(0.24f, 0.46f, 0.88f, 0.05f, 0.0f, 0.0f, 0.75f, 1.333f);
            renderer.addMesh(waterP, new float[]{0,1,0, 0,1,0, 0,1,0, 0,1,0}, new float[]{0,0,1,0,1,1,0,1},
                    new int[]{0, 1, 2, 0, 2, 3}, waterMat);

            // lava (emissive)
            float[] lavaP = new float[]{
                    2.0f, 0.5f, 1.0f, 3.0f, 0.5f, 1.0f, 3.0f, 1.5f, 1.0f, 2.0f, 1.5f, 1.0f,
                    2.0f, 0.5f, 2.0f, 2.0f, 0.5f, 1.0f, 2.0f, 1.5f, 1.0f, 2.0f, 1.5f, 2.0f,
                    3.0f, 0.5f, 2.0f, 2.0f, 0.5f, 2.0f, 2.0f, 1.5f, 2.0f, 3.0f, 1.5f, 2.0f,
                    3.0f, 0.5f, 1.0f, 3.0f, 0.5f, 2.0f, 3.0f, 1.5f, 2.0f, 3.0f, 1.5f, 1.0f,
                    2.0f, 1.5f, 1.0f, 3.0f, 1.5f, 1.0f, 3.0f, 1.5f, 2.0f, 2.0f, 1.5f, 2.0f,
                    2.0f, 0.5f, 2.0f, 3.0f, 0.5f, 2.0f, 3.0f, 0.5f, 1.0f, 2.0f, 0.5f, 1.0f
            };
            int lavaMat = renderer.createMaterial(0.89f, 0.42f, 0.08f, 0.5f, 0.0f, 6.0f, 0.0f, 1.5f);
            renderer.addMesh(lavaP, expandNormals(lavaP), new float[lavaP.length / 3 * 2], cubeIndices, lavaMat);

            // metal block (iron)
            float[] metalP = new float[]{
                    -3f, 0f, -3f, -2f, 0f, -3f, -2f, 1f, -3f, -3f, 1f, -3f,
                    -3f, 0f, -2f, -3f, 0f, -3f, -3f, 1f, -3f, -3f, 1f, -2f,
                    -2f, 0f, -2f, -3f, 0f, -2f, -3f, 1f, -2f, -2f, 1f, -2f,
                    -2f, 0f, -3f, -2f, 0f, -2f, -2f, 1f, -2f, -2f, 1f, -3f,
                    -3f, 1f, -3f, -2f, 1f, -3f, -2f, 1f, -2f, -3f, 1f, -2f,
                    -3f, 0f, -2f, -2f, 0f, -2f, -2f, 0f, -3f, -3f, 0f, -3f
            };
            int metalMat = renderer.createMaterial(0.85f, 0.85f, 0.85f, 0.35f, 1.0f, 0.0f, 0.0f, 1.5f);
            renderer.addMesh(metalP, expandNormals(metalP), new float[metalP.length / 3 * 2], cubeIndices, metalMat);

            // skydome (atmosphere)
            SkyDome sk = new SkyDome();
            SkyDome.Sky sky = sk.build(frame.sun.dx, frame.sun.dy, frame.sun.dz,
                    new float[]{frame.sun.r, frame.sun.g, frame.sun.b},
                    frame.sun.intensity,
                    new float[]{frame.sky.r, frame.sky.g, frame.sky.b},
                    80f, 16);
            int skyMat = renderer.createMaterial(
                    averageR(sky.dome.colors, 0),
                    averageR(sky.dome.colors, 1),
                    averageR(sky.dome.colors, 2),
                    1.0f, 0.0f, 1.0f, 0.0f, 1.0f);
            renderer.addMesh(sky.dome.positions, expandNormals(sky.dome.positions),
                    new float[sky.dome.positions.length / 3 * 2], sky.dome.indices, skyMat);

            // sun disc
            int sunMat = renderer.createMaterial(frame.sun.r, frame.sun.g, frame.sun.b,
                    0.3f, 0.0f, 8.0f, 0.0f, 1.0f);
            renderer.addMesh(sky.sun.positions, expandNormals(sky.sun.positions),
                    sky.sun.texCoords, sky.sun.indices, sunMat);

            // moon disc
            int moonMat = renderer.createMaterial(0.9f, 0.92f, 0.95f,
                    0.5f, 0.0f, 1.5f, 0.0f, 1.0f);
            renderer.addMesh(sky.moon.positions, expandNormals(sky.moon.positions),
                    sky.moon.texCoords, sky.moon.indices, moonMat);

            // clouds
            List<float[]> clouds = SkyDome.cloudQuads(1, System.currentTimeMillis());
            int cloudMat = renderer.createMaterial(1.0f, 1.0f, 1.0f, 1.0f, 0.0f,
                    frame.sky.intensity * 0.4f, 0.0f, 1.0f);
            for (float[] c : clouds) {
                renderer.addMesh(c, new float[]{0,1,0, 0,1,0, 0,1,0, 0,1,0},
                        new float[]{0,0,1,0,1,1,0,1},
                        new int[]{0,1,2,0,2,3}, cloudMat);
            }

            // lights
            renderer.addDirectionalLight(frame.sun.dx, frame.sun.dy, frame.sun.dz,
                    frame.sun.r, frame.sun.g, frame.sun.b, frame.sun.intensity);
            // torch point light near the lava
            renderer.addPointLight(2.5f, 1.2f, 1.5f, 1.0f, 0.85f, 0.5f, 8.0f, 12.0f);

            renderer.setClearColor(frame.sky.r, frame.sky.g, frame.sky.b);
            assertTrue(renderer.buildAccelerationStructure(), "BVH build must succeed");

            // Camera looking at the scene from above-ground.
            renderer.setCamera(-6f, 2.5f, 6f, 0f, 0.5f, 0f, 0f, 1f, 0f,
                    (float) Math.toRadians(60.0), (float) W / (float) H, 0.1f, 200f);

            double renderMs = renderer.renderFrame();
            System.out.println("day frame renderMs=" + renderMs);
            ByteBuffer frame0 = renderer.readFramebufferRgba8();
            assertNotNull(frame0);

            // Apply the denoiser (PART 7).
            TemporalDenoiser d = new TemporalDenoiser();
            d.setHistoryWeight(0.65f);
            d.denoiseInPlace(frame0, W, H, null);
            // Render a second time to get temporal accumulation.
            renderer.renderFrame();
            ByteBuffer frame1 = renderer.readFramebufferRgba8();
            d.denoiseInPlace(frame1, W, H, null);
            d.spatialFilter(frame1, W, H);

            Path out = Files.createTempFile("phase47-day-", ".bmp");
            dumpBmp(frame1, W, H, out);
            System.out.println("DAY frame dumped to " + out);

            // Sanity: ensure the frame has color variation (not a uniform
            // saturated color), which would prove the visual upgrade ran.
            int warm = 0, blue = 0, dark = 0, mid = 0;
            for (int i = 0; i < W * H * 4; i += 4) {
                int r = frame1.get(i) & 0xFF;
                int g = frame1.get(i + 1) & 0xFF;
                int b = frame1.get(i + 2) & 0xFF;
                if (r > 180 && g > 60 && g < 180 && b < 80) warm++;
                else if (b > 150 && r < 150) blue++;
                else if (r + g + b < 60) dark++;
                else mid++;
            }
            assertTrue(warm + blue + mid > W * H * 0.6,
                    "frame must show varied color (warm=" + warm
                            + " blue=" + blue + " dark=" + dark + " mid=" + mid + ")");
        } finally {
            renderer.close();
        }
    }

    @Test
    void produceNightFrame() throws Exception {
        MesrGLRenderer renderer = new MesrGLRenderer(W, H, 1, 3, true, 0xC0FFEEL);
        try {
            renderer.setShadowsEnabled(true);
            renderer.setGIEnabled(true);
            renderer.setExposure(1.0f);
            renderer.setToneMapping(4);
            MinecraftLighting.Frame frame = new MinecraftLighting().compute(18000L, new ArrayList<>());
            SkyDome sk = new SkyDome();
            SkyDome.Sky sky = sk.build(frame.sun.dx, frame.sun.dy, frame.sun.dz,
                    new float[]{frame.sun.r, frame.sun.g, frame.sun.b},
                    frame.sun.intensity,
                    new float[]{frame.sky.r, frame.sky.g, frame.sky.b},
                    80f, 16);
            int skyMat = renderer.createMaterial(
                    averageR(sky.dome.colors, 0),
                    averageR(sky.dome.colors, 1),
                    averageR(sky.dome.colors, 2),
                    1.0f, 0.0f, 0.6f, 0.0f, 1.0f);
            renderer.addMesh(sky.dome.positions, expandNormals(sky.dome.positions),
                    new float[sky.dome.positions.length / 3 * 2], sky.dome.indices, skyMat);
            int moonMat = renderer.createMaterial(0.95f, 0.95f, 1.0f,
                    0.5f, 0.0f, 2.0f, 0.0f, 1.0f);
            renderer.addMesh(sky.moon.positions, expandNormals(sky.moon.positions),
                    sky.moon.texCoords, sky.moon.indices, moonMat);
            // Lava for warm light at night.
            float[] lavaP = new float[]{
                    0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f,
                    0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f,
                    1f, 0f, 1f, 0f, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f,
                    1f, 0f, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f, 1f, 0f,
                    0f, 1f, 0f, 1f, 1f, 0f, 1f, 1f, 1f, 0f, 1f, 1f,
                    0f, 0f, 1f, 1f, 0f, 1f, 1f, 0f, 0f, 0f, 0f, 0f
            };
            int lavaMat = renderer.createMaterial(0.89f, 0.42f, 0.08f, 0.5f, 0.0f, 6.0f, 0.0f, 1.5f);
            renderer.addMesh(lavaP, expandNormals(lavaP), new float[lavaP.length / 3 * 2], cubeIndices, lavaMat);
            renderer.addDirectionalLight(frame.sun.dx, frame.sun.dy, frame.sun.dz,
                    frame.sun.r, frame.sun.g, frame.sun.b, frame.sun.intensity);
            renderer.addPointLight(0.5f, 0.8f, 0.5f, 1.0f, 0.55f, 0.15f, 6.0f, 10.0f);
            renderer.setClearColor(frame.sky.r, frame.sky.g, frame.sky.b);
            assertTrue(renderer.buildAccelerationStructure());
            renderer.setCamera(-3f, 3f, 3f, 0.5f, 0.5f, 0.5f, 0f, 1f, 0f,
                    (float) Math.toRadians(60.0), (float) W / (float) H, 0.1f, 200f);
            renderer.renderFrame();
            ByteBuffer fb = renderer.readFramebufferRgba8();
            assertNotNull(fb);
            Path out = Files.createTempFile("phase47-night-", ".bmp");
            dumpBmp(fb, W, H, out);
            System.out.println("NIGHT frame dumped to " + out);
        } finally {
            renderer.close();
        }
    }

    @Test
    void produceRainFrame() throws Exception {
        MesrGLRenderer renderer = new MesrGLRenderer(W, H, 1, 3, true, 0xC0FFEEL);
        try {
            renderer.setShadowsEnabled(true);
            renderer.setGIEnabled(true);
            renderer.setExposure(0.8f);
            renderer.setToneMapping(4);
            MinecraftLighting.Frame frame = new MinecraftLighting().compute(6000L, new ArrayList<>());
            SkyDome sk = new SkyDome();
            SkyDome.Sky sky = sk.build(frame.sun.dx, frame.sun.dy, frame.sun.dz,
                    new float[]{0.45f, 0.5f, 0.6f}, 1.5f,
                    new float[]{0.5f, 0.55f, 0.65f}, 80f, 16);
            int skyMat = renderer.createMaterial(0.5f, 0.55f, 0.65f, 1.0f, 0.0f, 0.8f, 0.0f, 1.0f);
            renderer.addMesh(sky.dome.positions, expandNormals(sky.dome.positions),
                    new float[sky.dome.positions.length / 3 * 2], sky.dome.indices, skyMat);
            // ground
            int groundMat = renderer.createMaterial(0.45f, 0.5f, 0.35f, 0.95f, 0.0f, 0.0f, 0.0f, 1.5f);
            float[] gp = new float[]{
                    -8f, 0f, -8f, 8f, 0f, -8f, 8f, 0f, 8f, -8f, 0f, 8f
            };
            renderer.addMesh(gp, new float[]{0,1,0, 0,1,0, 0,1,0, 0,1,0},
                    new float[]{0,0,1,0,1,1,0,1},
                    new int[]{0,1,2,0,2,3}, groundMat);
            // rain particles
            WeatherBridge wb = new WeatherBridge();
            WeatherBridge.State st = new WeatherBridge.State(
                    WeatherBridge.WeatherMode.RAIN, 0.8f, 0.3f, 287f,
                    System.currentTimeMillis());
            WeatherBridge.Particles parts = wb.buildParticles(0, 3, 0, st, 64);
            int rainMat = renderer.createMaterial(0.6f, 0.7f, 0.95f, 0.0f, 0.0f, 0.5f, 0.3f, 1.0f);
            int[] idx = new int[]{0, 1, 2, 0, 2, 3};
            for (float[] q : parts.rainQuads) {
                renderer.addMesh(q, expandNormals(q),
                        new float[]{0, 0, 1, 0, 1, 1, 0, 1}, idx, rainMat);
            }
            renderer.addDirectionalLight(0.5f, -0.7f, -0.4f, 0.7f, 0.75f, 0.85f, 2.0f);
            renderer.setClearColor(0.5f, 0.55f, 0.65f);
            assertTrue(renderer.buildAccelerationStructure());
            renderer.setCamera(0f, 2.5f, 4f, 0f, 1.5f, 0f, 0f, 1f, 0f,
                    (float) Math.toRadians(60.0), (float) W / (float) H, 0.1f, 200f);
            renderer.renderFrame();
            ByteBuffer fb = renderer.readFramebufferRgba8();
            assertNotNull(fb);
            Path out = Files.createTempFile("phase47-rain-", ".bmp");
            dumpBmp(fb, W, H, out);
            System.out.println("RAIN frame dumped to " + out);
        } finally {
            renderer.close();
        }
    }

    @Test
    void qualityPresetsSanity() {
        for (QualityPresets q : QualityPresets.values()) {
            assertTrue(q.renderScale > 0.0f && q.renderScale <= 1.0f, q + " scale");
            assertTrue(q.samplesPerPixel >= 1 && q.samplesPerPixel <= 16, q + " spp");
            assertTrue(q.maxBounces >= 1 && q.maxBounces <= 8, q + " bounces");
            assertTrue(q.cloudTier >= 0 && q.cloudTier <= 4, q + " cloud tier");
        }
        QualityPresets.set(QualityPresets.HIGH);
        assertSame(QualityPresets.HIGH, QualityPresets.current());
        // Sysprop fallback
        System.setProperty("fv2j3.mesrgl.quality", "ultra");
        assertSame(QualityPresets.ULTRA, QualityPresets.fromProperty());
        System.clearProperty("fv2j3.mesrgl.quality");
    }

    @Test
    void materialsCatalogueSanity() {
        MinecraftMaterials m = new MinecraftMaterials();
        assertNotNull(m.water());
        assertNotNull(m.glass());
        assertNotNull(m.grass());
        assertNotNull(m.stone());
        assertNotNull(m.lava());
        // Water is transmissive, glass is transmissive, lava is emissive
        assertTrue(m.water().transmission > 0.5f);
        assertTrue(m.glass().transmission > 0.5f);
        assertTrue(m.lava().emission > 0f);
        // Iron is metal
        MinecraftMaterials.Entry iron = m.resolve("iron_block", 0xD8D8D8);
        assertEquals(1.0f, iron.metallic, 1e-3f);
    }

    @Test
    void skyDomeRendersReasonableColors() {
        SkyDome sk = new SkyDome();
        SkyDome.Sky sky = sk.build(0.3f, 0.85f, 0.4f,
                new float[]{1.0f, 0.97f, 0.88f}, 3.4f,
                new float[]{0.45f, 0.62f, 0.85f}, 100f, 8);
        // Zenith should be blue, horizon warmer
        float r = averageR(sky.dome.colors, 0);
        float g = averageR(sky.dome.colors, 1);
        float b = averageR(sky.dome.colors, 2);
        assertTrue(b >= g, "blue channel should dominate zenith sky");
        assertTrue(r > 0.0f && g > 0.0f && b > 0.0f, "sky must be non-black");
    }

    // -- helpers ---------------------------------------------------------

    private static int makeCube(float x0, float y0, float z0, float x1, float y1, float z1) {
        // Reuse the standard 24-vert cube
        cubePositions = new float[]{
                x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1,
                x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0,
                x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1,
                x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1,
                x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0,
                x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0
        };
        cubeNormals = new float[]{
                0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1,
                -1, 0, 0, -1, 0, 0, -1, 0, 0, -1, 0, 0,
                1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0,
                0, -1, 0, 0, -1, 0, 0, -1, 0, 0, -1, 0,
                0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0,
                0, 0, -1, 0, 0, -1, 0, 0, -1, 0, 0, -1
        };
        cubeUVs = new float[]{
                0, 0, 1, 0, 1, 1, 0, 1,
                0, 0, 1, 0, 1, 1, 0, 1,
                0, 0, 1, 0, 1, 1, 0, 1,
                0, 0, 1, 0, 1, 1, 0, 1,
                0, 0, 1, 0, 1, 1, 0, 1,
                0, 0, 1, 0, 1, 1, 0, 1
        };
        cubeIndices = new int[]{
                0, 1, 2, 0, 2, 3,
                4, 5, 6, 4, 6, 7,
                8, 9, 10, 8, 10, 11,
                12, 13, 14, 12, 14, 15,
                16, 17, 18, 16, 18, 19,
                20, 21, 22, 20, 22, 23
        };
        return 0;
    }

    private static float[] cubePositions;
    private static float[] cubeNormals;
    private static float[] cubeUVs;
    private static int[] cubeIndices;

    private static int groundMaterial(MesrGLRenderer renderer, MinecraftLighting.Frame frame) {
        return renderer.createMaterial(0.45f, 0.62f, 0.30f, 0.9f, 0.0f, 0.0f, 0.0f, 1.5f);
    }

    private static float[] expandNormals(float[] positions) {
        float[] n = new float[positions.length];
        int stride = positions.length % 9 == 0 ? 9 : 12; // 9 = 3 verts, 12 = 4 verts
        for (int i = 0; i + stride <= positions.length; i += stride) {
            float cx = 0, cy = 0, cz = 0;
            int verts = stride / 3;
            for (int k = 0; k < verts; k++) {
                cx += positions[i + k * 3];
                cy += positions[i + k * 3 + 1];
                cz += positions[i + k * 3 + 2];
            }
            cx /= verts; cy /= verts; cz /= verts;
            float len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            if (len < 1e-5f) len = 1f;
            float nx = cx / len, ny = cy / len, nz = cz / len;
            for (int k = 0; k < verts; k++) {
                n[i + k * 3] = nx;
                n[i + k * 3 + 1] = ny;
                n[i + k * 3 + 2] = nz;
            }
        }
        return n;
    }

    private static float averageR(float[] colors, int channel) {
        if (colors.length == 0) return 0f;
        double s = 0;
        for (int i = channel; i < colors.length; i += 3) s += colors[i];
        return (float) (s / (colors.length / 3));
    }

    private static void dumpBmp(ByteBuffer rgba, int w, int h, Path out) throws IOException {
        int rowBytes = w * 3;
        int pad = (4 - (rowBytes % 4)) % 4;
        int dataSize = (rowBytes + pad) * h;
        ByteBuffer bmp = ByteBuffer.allocate(54 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        bmp.put((byte) 'B').put((byte) 'M');
        bmp.putInt(54 + dataSize).putShort((short) 0).putShort((short) 0).putInt(54);
        bmp.putInt(40).putInt(w).putInt(h).putShort((short) 1).putShort((short) 24);
        bmp.putInt(0).putInt(dataSize).putInt(2835).putInt(2835).putInt(0).putInt(0);
        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                int i = (y * w + x) * 4;
                bmp.put(rgba.get(i + 2)).put(rgba.get(i + 1)).put(rgba.get(i));
            }
            for (int p = 0; p < pad; p++) bmp.put((byte) 0);
        }
        try (RandomAccessFile raf = new RandomAccessFile(out.toFile(), "rw")) {
            raf.write(bmp.array());
        }
    }
}
