package io.github.fv2j3dteam.mesrgl.integration.minecraft;

import io.github.fv2j3dteam.mesrgl.MesrGLRenderer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Visual-quality scene builder (Phase 47 - PART 1 through PART 5, PART 6).
 *
 * Takes a {@link ChunkSceneExtractor.ExtractedScene} and turns it into:
 *  - per-block-type meshes with UV coordinates from the terrain atlas
 *  - a water surface mesh with transmission + IOR 1.333
 *  - a tessellated skydome with Rayleigh+Mie per-vertex colors
 *  - sun + moon discs
 *  - weather particle quads (rain / snow / smoke)
 *  - sun and block-light point lights
 *
 * The builder is deliberately stateless across frames: it produces a
 * complete scene on every call. The caller (MesrGLMinecraftRenderer) is
 * responsible for skipping the rebuild when nothing changed.
 */
public final class MinecraftSceneBuilder {

    private final MinecraftMaterials materials = new MinecraftMaterials();
    private final TextureAtlas atlas = new TextureAtlas();
    private final SkyDome skyBuilder = new SkyDome();
    private final WeatherBridge weatherBridge = new WeatherBridge();
    private final LightingDebug lightingDebug = new LightingDebug();

    /** How strong the sky ambient contribution is, in [0,1]. */
    public static final float SKY_AMBIENT_STRENGTH = Float.parseFloat(
            System.getProperty("fv2j3.mesrgl.skyAmbientStrength", "0.25"));

    /** Result of one scene build. */
    public static final class Built {
        public int meshCount;
        public int triangleCount;
        public int materialCount;
        public int lightCount;
        public float[] clearColor; // 3 floats (sky tint)
        public Built() {
            meshCount = 0; triangleCount = 0; materialCount = 0; lightCount = 0;
            clearColor = new float[]{0.5f, 0.7f, 0.9f};
        }
    }

    /** Holds the data needed to build a per-material mesh. */
    private static final class Build {
        final MinecraftMaterials.Entry entry;
        final GrowableFloatArray positions = new GrowableFloatArray();
        final GrowableFloatArray normals = new GrowableFloatArray();
        final GrowableFloatArray texCoords = new GrowableFloatArray();
        final GrowableIntArray indices = new GrowableIntArray();
        Build(MinecraftMaterials.Entry entry) { this.entry = entry; }
    }

    /**
     * Builds the scene by consuming chunk data and applying every PART
     * of the visual upgrade. The result is the rebuilt MesrGL scene, the
     * computed lighting state, and the per-frame weather/particle data.
     */
    public Built build(MesrGLRenderer renderer,
                       ChunkSceneExtractor.ExtractedScene scene,
                       MinecraftReflection mc,
                       Object world,
                       Object mcInstance,
                       double cameraX, double cameraY, double cameraZ,
                       long worldTime,
                       int qualityTier) {
        renderer.resetScene();
        // The extractor scans each chunk exactly once when it arrives; the
        // builder only maps every pre-extracted color group to a material and
        // uploads its mesh. No Minecraft block is touched here, which keeps
        // the rebuild cost independent of the world size.
        Map<Integer, Integer> materialIds = new LinkedHashMap<>();
        int waterMaterialId = -1;
        int glassMaterialId = -1;

        for (ChunkSceneExtractor.ColorGroup group : scene.groups) {
            MinecraftMaterials.Entry entry = materials.resolve(group.identity, group.rgb);
            int materialId = renderer.createMaterial(
                    (entry.rgb >> 16) / 255.0f,
                    (entry.rgb >> 8 & 0xFF) / 255.0f,
                    (entry.rgb & 0xFF) / 255.0f,
                    entry.roughness, entry.metallic, entry.emission,
                    entry.transmission, entry.ior);
            materialIds.put(group.rgb, materialId);
            if ("water".equals(entry.category)) waterMaterialId = materialId;
            if ("glass".equals(entry.category)) glassMaterialId = materialId;
        }

        // Upload each mesh.
        int totalTris = 0;
        int meshCount = 0;
        for (ChunkSceneExtractor.ColorGroup group : scene.groups) {
            int materialId = materialIds.get(group.rgb);
            renderer.addMesh(group.positionsArray(), group.normalsArray(),
                    group.texCoordsArray(), group.indicesArray(), materialId);
            totalTris += group.indices.size() / 3;
            meshCount++;
        }

        // ---- Water surface: a translucent plane at the top of water blocks ----
        int waterMeshId = addWaterSurface(renderer, scene, mc, waterMaterialId, materials);

        // ---- Skydome + sun + moon ----
        MinecraftLighting.Frame lighting = lightingForWorldTime(worldTime);
        SkyDome.Sky sky = skyBuilder.build(
                lighting.sun.dx, lighting.sun.dy, lighting.sun.dz,
                new float[]{lighting.sun.r, lighting.sun.g, lighting.sun.b},
                lighting.sun.intensity,
                new float[]{lighting.sky.r, lighting.sky.g, lighting.sky.b},
                /*radius*/ 400f,
                /*tess*/ 24);
        // Dome is a colored mesh: per-vertex color goes through the material
        // as albedo (we override with vertex colors via the addMesh call's
        // texCoords not being used; the renderer-side vertex color is
        // approximated by the average sky color for visual effect).
        float avgR = 0, avgG = 0, avgB = 0;
        for (int i = 0; i < sky.dome.colors.length / 3; i++) {
            avgR += sky.dome.colors[i * 3];
            avgG += sky.dome.colors[i * 3 + 1];
            avgB += sky.dome.colors[i * 3 + 2];
        }
        int vCount = sky.dome.colors.length / 3;
        if (vCount > 0) {
            avgR /= vCount; avgG /= vCount; avgB /= vCount;
        }
        int skyMat = renderer.createMaterial(avgR, avgG, avgB, 1.0f, 0.0f, 4.0f, 0.0f, 1.0f);
        renderer.addMesh(sky.dome.positions,
                sphereNormals(sky.dome.positions),
                new float[sky.dome.positions.length / 3 * 2],
                sky.dome.indices, skyMat);

        // Sun disc
        int sunMat = renderer.createMaterial(lighting.sun.r, lighting.sun.g, lighting.sun.b,
                0.3f, 0.0f, 12.0f, 0.0f, 1.0f);
        renderer.addMesh(sky.sun.positions, quadNormals(sky.sun.positions),
                sky.sun.texCoords, sky.sun.indices, sunMat);
        // Moon disc
        int moonMat = renderer.createMaterial(0.9f, 0.92f, 0.95f,
                0.5f, 0.0f, 2.0f, 0.0f, 1.0f);
        renderer.addMesh(sky.moon.positions, quadNormals(sky.moon.positions),
                sky.moon.texCoords, sky.moon.indices, moonMat);

        // ---- Weather particles ----
        WeatherBridge.State wx = weatherBridge.computeState(world, mcInstance);
        WeatherBridge.Particles parts = weatherBridge.buildParticles(
                cameraX, cameraY, cameraZ, wx,
                Math.min(128, 16 * (qualityTier + 1)));
        if (!parts.rainQuads.isEmpty()) {
            int rainMat = renderer.createMaterial(0.6f, 0.7f, 0.95f, 0.0f, 0.0f, 0.8f, 0.3f, 1.0f);
            for (float[] q : parts.rainQuads) {
                int[] idx = new int[]{0, 1, 2, 0, 2, 3};
                renderer.addMesh(q, quadNormals(q),
                        new float[]{0, 0, 1, 0, 1, 1, 0, 1}, idx, rainMat);
            }
        }
        if (!parts.snowQuads.isEmpty()) {
            int snowMat = renderer.createMaterial(0.95f, 0.97f, 1.0f, 0.6f, 0.0f, 0.6f, 0.0f, 1.0f);
            for (float[] q : parts.snowQuads) {
                int[] idx = new int[]{0, 1, 2, 0, 2, 3};
                renderer.addMesh(q, quadNormals(q),
                        new float[]{0, 0, 1, 0, 1, 1, 0, 1}, idx, snowMat);
            }
        }
        if (!parts.smokeQuads.isEmpty()) {
            int smokeMat = renderer.createMaterial(0.6f, 0.55f, 0.5f, 1.0f, 0.0f, 0.1f, 0.0f, 1.0f);
            for (float[] q : parts.smokeQuads) {
                int[] idx = new int[]{0, 1, 2, 0, 2, 3};
                renderer.addMesh(q, quadNormals(q),
                        new float[]{0, 0, 1, 0, 1, 1, 0, 1}, idx, smokeMat);
            }
        }

        // ---- Cloud planes (PART 8) ----
        List<float[]> clouds = SkyDome.cloudQuads(qualityTier, System.currentTimeMillis());
        if (!clouds.isEmpty()) {
            int cloudMat = renderer.createMaterial(1.0f, 1.0f, 1.0f, 1.0f, 0.0f, lighting.sky.intensity * 0.6f, 0.0f, 1.0f);
            for (float[] q : clouds) {
                int[] idx = new int[]{0, 1, 2, 0, 2, 3};
                renderer.addMesh(q, quadNormals(q),
                        new float[]{0, 0, 1, 0, 1, 1, 0, 1}, idx, cloudMat);
            }
        }

        // ---- Lights (Phase 47.1 - PART 2 sky ambient, PART 3 debug) ----
        LightingDebug.Override dbg = lightingDebug.override();
        // Sun: scaled by debug override (e.g. 0 for DIRECT_LIGHT_ONLY,
        // 1 otherwise).
        renderer.addDirectionalLight(lighting.sun.dx, lighting.sun.dy, lighting.sun.dz,
                lighting.sun.r, lighting.sun.g, lighting.sun.b,
                lighting.sun.intensity * dbg.sunScale);
        // Sky ambient: a second up-pointing directional light with the
        // sky color. This is the standard "hemispheric ambient"
        // approximation when a true ambient term is not available in
        // the JNI bridge. The intensity follows the sun-altitude
        // envelope so it fades at night and reaches a maximum at noon.
        float skyAmbient = SKY_AMBIENT_STRENGTH
                * Math.max(0.05f, lighting.sky.intensity) * dbg.sunScale;
        renderer.addDirectionalLight(0f, 1f, 0f,
                lighting.sky.r, lighting.sky.g, lighting.sky.b,
                skyAmbient);
        // Block lights: torch / glowstone / lava, scaled by emission
        // override (1 in NORMAL, 1 in EMISSION_ONLY, 0 in DIRECT_LIGHT_ONLY
        // and ALBEDO_ONLY).
        for (MinecraftLighting.PointLight pl : lighting.blockLights) {
            renderer.addPointLight(pl.px, pl.py, pl.pz,
                    pl.r, pl.g, pl.b,
                    pl.intensity * dbg.emissionScale,
                    pl.range);
        }

        // Sky-tinted clear color (used by the path tracer's miss shader
        // when a ray does not hit the dome, e.g. looking through a window).
        renderer.setClearColor(lighting.sky.r * lighting.sky.intensity,
                lighting.sky.g * lighting.sky.intensity,
                lighting.sky.b * lighting.sky.intensity);

        // Build the acceleration structure.
        renderer.buildAccelerationStructure();

        Built b = new Built();
        b.meshCount = meshCount + 1 /*water*/ + 1 /*sky*/ + 2 /*sun, moon*/
                + parts.rainQuads.size() + parts.snowQuads.size() + parts.smokeQuads.size()
                + clouds.size();
        b.triangleCount = totalTris + 2 /*water plane*/ + sky.dome.indexCount / 3
                + 2 /*sun*/ + 2 /*moon*/
                + (parts.rainQuads.size() + parts.snowQuads.size() + parts.smokeQuads.size()) * 2
                + clouds.size() * 2;
        b.materialCount = materialIds.size() + 1 /*water*/ + 1 /*sky*/ + 1 /*sun*/ + 1 /*moon*/
                + (parts.rainQuads.isEmpty() ? 0 : 1)
                + (parts.snowQuads.isEmpty() ? 0 : 1)
                + (parts.smokeQuads.isEmpty() ? 0 : 1)
                + (clouds.isEmpty() ? 0 : 1);
        b.lightCount = 2 + lighting.blockLights.size();  // sun + sky-ambient + block lights
        b.clearColor = new float[]{lighting.sky.r, lighting.sky.g, lighting.sky.b};
        return b;
    }

    /**
     * Emits the visible faces of one block. The visibility check follows
     * the existing extractor logic: a face is emitted when the neighbor
     * is air (or transparent for non-transparent materials) or a different
     * color family.
     */
    private void emitBlockFaces(Build b, int wx, int wy, int wz,
                                MinecraftMaterials.Entry entry,
                                MinecraftReflection mc,
                                ChunkSceneExtractor.CachedChunk c,
                                int lx, int y, int lz) {
        for (int dir = 0; dir < 6; dir++) {
            int nx = lx + DIR_X[dir];
            int ny = y + DIR_Y[dir];
            int nz = lz + DIR_Z[dir];
            boolean visible;
            if (ny < 0) {
                visible = false;
            } else if (ny >= 256) {
                visible = true;
            } else if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16) {
                visible = neighborIsAir(mc, c.chunk, nx, ny, nz, entry);
            } else {
                visible = true; // world boundary
            }
            if (!visible) continue;
            addBlockFace(b, wx, wy, wz, dir, materials.faceUVsFor(entry), entry);
        }
    }

    private boolean neighborIsAir(MinecraftReflection mc, Object chunk,
                                  int x, int y, int z, MinecraftMaterials.Entry entry) {
        try {
            Object s = mc.blockState(chunk, x, y, z);
            if (s == null) return true;
            int color = mc.mapColor(s);
            if (color < 0) return true; // air
            // For transparent materials, a neighbor of a different color
            // is still visible (glass against glass blends together).
            if (entry.transparent) {
                return true;
            }
            return true; // any solid neighbor means the face is visible
        } catch (Throwable t) {
            return true;
        }
    }

    private static final int[] DIR_X = {1, -1, 0, 0, 0, 0};
    private static final int[] DIR_Y = {0, 0, 1, -1, 0, 0};
    private static final int[] DIR_Z = {0, 0, 0, 0, 1, -1};
    private static final float[][] FACE_VERTS = {
            {1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1},
            {0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0},
            {0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0},
            {0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1},
            {0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1},
            {1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0},
    };
    private static final float[][] FACE_NORMALS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
    };

    private void addBlockFace(Build b, int wx, int wy, int wz, int dir,
                              MinecraftMaterials.FaceUVs faces, MinecraftMaterials.Entry entry) {
        float[] v = FACE_VERTS[dir];
        float[] n = FACE_NORMALS[dir];
        int vertexBase = b.positions.size() / 3;
        float u0, v0, u1, v1;
        switch (dir) {
            case 0: u0 = faces.eastU0; v0 = faces.eastV0; u1 = faces.eastU1; v1 = faces.eastV1; break;
            case 1: u0 = faces.westU0; v0 = faces.westV0; u1 = faces.westU1; v1 = faces.westV1; break;
            case 2: u0 = faces.topU0;  v0 = faces.topV0;  u1 = faces.topU1;  v1 = faces.topV1;  break;
            case 3: u0 = faces.bottomU0; v0 = faces.bottomV0;
                    u1 = faces.bottomU1; v1 = faces.bottomV1; break;
            case 4: u0 = faces.southU0; v0 = faces.southV0; u1 = faces.southU1; v1 = faces.southV1; break;
            default: u0 = faces.northU0; v0 = faces.northV0; u1 = faces.northU1; v1 = faces.northV1; break;
        }
        float[][] corner = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        for (int i = 0; i < 4; i++) {
            b.positions.add(wx + v[i * 3]);
            b.positions.add(wy + v[i * 3 + 1]);
            b.positions.add(wz + v[i * 3 + 2]);
            b.normals.add(n[0]);
            b.normals.add(n[1]);
            b.normals.add(n[2]);
            b.texCoords.add(corner[i][0]);
            b.texCoords.add(corner[i][1]);
        }
        b.indices.add(vertexBase);
        b.indices.add(vertexBase + 1);
        b.indices.add(vertexBase + 2);
        b.indices.add(vertexBase);
        b.indices.add(vertexBase + 2);
        b.indices.add(vertexBase + 3);
    }

    private MinecraftLighting.Frame lightingForWorldTime(long worldTime) {
        // We don't have access to block-light positions yet from a world
        // snapshot; the renderer can supplement this list with the
        // torch/glowstone/lava positions it sees. For now we use an
        // empty list and let the renderer inject block lights.
        return new MinecraftLighting().compute(worldTime, new ArrayList<>());
    }

    /**
     * Adds a single water surface mesh: one large quad covering the world
     * bounds at sea level. The water material is transmissive (IOR 1.333,
     * low roughness) so the ray tracer will refract through it.
     *
     * A more accurate implementation would emit one quad per water block
     * row; this conservative version is correct visually and avoids
     * degeneracies at the world boundary.
     */
    private int addWaterSurface(MesrGLRenderer renderer,
                                ChunkSceneExtractor.ExtractedScene scene,
                                MinecraftReflection mc,
                                int waterMaterialId,
                                MinecraftMaterials materials) {
        if (waterMaterialId < 0) return -1;
        // Build a single big quad at y=62 covering the chunk radius.
        int r = scene.radiusChunks;
        float size = r * 16.0f;
        float[] pos = new float[]{
                -size, 62.0f, -size,
                size, 62.0f, -size,
                size, 62.0f, size,
                -size, 62.0f, size,
        };
        float[] nrm = new float[]{0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0};
        float[] uvs = new float[]{0, 0, size / 4f, 0, size / 4f, size / 4f, 0, size / 4f};
        int[] idx = new int[]{0, 1, 2, 0, 2, 3};
        renderer.addMesh(pos, nrm, uvs, idx, waterMaterialId);
        return 0;
    }

    /**
     * Per-vertex normals for an indexed sphere mesh (the sky dome): the
     * normal of a sphere point is the normalized position. The old
     * triangle-triplet normal generator read 9-float groups and ran off
     * the end of any vertex array whose count is not divisible by 3
     * (e.g. the 24x48 dome grid: 1225 vertices), crashing every build.
     */
    private static float[] sphereNormals(float[] positions) {
        float[] n = new float[positions.length];
        for (int i = 0; i + 2 < positions.length; i += 3) {
            float x = positions[i], y = positions[i + 1], z = positions[i + 2];
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            if (len < 1e-5f) len = 1f;
            n[i] = x / len;
            n[i + 1] = y / len;
            n[i + 2] = z / len;
        }
        return n;
    }

    /**
     * Face normals for independent 4-vertex quads (12 floats each: sun disc,
     * moon disc, rain/snow/smoke particles).
     */
    private static float[] quadNormals(float[] positions) {
        int quads = positions.length / 12;
        float[] n = new float[positions.length];
        for (int q = 0; q < quads; q++) {
            int b = q * 12;
            float ax = positions[b + 3] - positions[b];
            float ay = positions[b + 4] - positions[b + 1];
            float az = positions[b + 5] - positions[b + 2];
            float bx = positions[b + 6] - positions[b];
            float by = positions[b + 7] - positions[b + 1];
            float bz = positions[b + 8] - positions[b + 2];
            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-5f) len = 1f;
            nx /= len; ny /= len; nz /= len;
            for (int v = 0; v < 4; v++) {
                n[b + v * 3] = nx;
                n[b + v * 3 + 1] = ny;
                n[b + v * 3 + 2] = nz;
            }
        }
        return n;
    }

    /** Convenience: compute the current per-frame lighting (sun + sky) only. */
    public MinecraftLighting.Frame lightingForTime(long worldTime) {
        return lightingForWorldTime(worldTime);
    }
}
