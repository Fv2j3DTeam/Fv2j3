package io.github.fv2j3dteam.mesrgl.integration.minecraft;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minecraft 1.12.2 block material catalogue (Phase 47 - PART 1, PART 3).
 *
 * Each entry maps a notch block-state description to a physical material
 * (albedo/roughness/metallic/transmission/ior) plus a UV rectangle into the
 * 16x16 terrain atlas (we generate the atlas procedurally - see
 * {@link TextureAtlas}). Block identity is recovered from the Block class
 * name; we cannot read the live Block registry in the absence of a true
 * asset load, so the catalogue is a name-based dispatch with a map-color
 * fallback for unknown blocks.
 *
 * BRDF parameters (PART 3) follow the GGX + Smith + Schlick model:
 *   - diffuse (albedo=1, metallic=0, transmission=0)
 *   - metal   (albedo<tint>, metallic=1)
 *   - glass   (transmission>0, ior~1.5, low roughness)
 *   - water   (transmission~0.7, ior=1.333, very low roughness)
 *
 * UV layout: terrain.png is a 16x16 grid of 16x16 tiles. We use the
 * canonical 1.12 terrain.png coordinates for the blocks we recognise and
 * provide a default grass-top tile for everything else (so the world never
 * falls back to a flat color when the catalogue misses).
 */
public final class MinecraftMaterials {

    /** One catalogue entry. UVs are 0..1 in atlas space (bottom-left origin). */
    public static final class Entry {
        public final int rgb;            // fallback diffuse color if no texture
        public final float u0, v0, u1, v1;   // atlas UV rect
        public final float roughness;
        public final float metallic;
        public final float transmission;
        public final float ior;
        public final float emission;     // emissive multiplier (block-light)
        public final boolean animated;   // animated texture (water, lava)
        public final boolean transparent;
        public final String category;    // diffuse | metal | glass | water | emissive

        public Entry(int rgb, float u0, float v0, float u1, float v1,
                     float roughness, float metallic, float transmission, float ior,
                     float emission, boolean animated, boolean transparent, String category) {
            this.rgb = rgb;
            this.u0 = u0; this.v0 = v0; this.u1 = u1; this.v1 = v1;
            this.roughness = roughness;
            this.metallic = metallic;
            this.transmission = transmission;
            this.ior = ior;
            this.emission = emission;
            this.animated = animated;
            this.transparent = transparent;
            this.category = category;
        }
    }

    /** All 6 face UV rects for a block (top/bottom/north/south/east/west). */
    public static final class FaceUVs {
        public final float topU0, topV0, topU1, topV1;
        public final float bottomU0, bottomV0, bottomU1, bottomV1;
        public final float northU0, northV0, northU1, northV1;
        public final float southU0, southV0, southU1, southV1;
        public final float eastU0, eastV0, eastU1, eastV1;
        public final float westU0, westV0, westU1, westV1;

        public FaceUVs(float topU0, float topV0, float topU1, float topV1,
                        float bottomU0, float bottomV0, float bottomU1, float bottomV1,
                        float northU0, float northV0, float northU1, float northV1,
                        float southU0, float southV0, float southU1, float southV1,
                        float eastU0, float eastV0, float eastU1, float eastV1,
                        float westU0, float westV0, float westU1, float westV1) {
            this.topU0 = topU0; this.topV0 = topV0; this.topU1 = topU1; this.topV1 = topV1;
            this.bottomU0 = bottomU0; this.bottomV0 = bottomV0;
            this.bottomU1 = bottomU1; this.bottomV1 = bottomV1;
            this.northU0 = northU0; this.northV0 = northV0;
            this.northU1 = northU1; this.northV1 = northV1;
            this.southU0 = southU0; this.southV0 = southV0;
            this.southU1 = southU1; this.southV1 = southV1;
            this.eastU0 = eastU0; this.eastV0 = eastV0;
            this.eastU1 = eastU1; this.eastV1 = eastV1;
            this.westU0 = westU0; this.westV0 = westV0;
            this.westU1 = westU1; this.westV1 = westV1;
        }
    }

    /**
     * Six face UVs in MC face order: +X(east), -X(west), +Y(top), -Y(bottom),
     * +Z(south), -Z(north). The atlas is 16x16 tiles, each 1/16 wide.
     */
    public static final class UvAtlas {
        public final float[] u0 = new float[6];
        public final float[] v0 = new float[6];
        public final float[] u1 = new float[6];
        public final float[] v1 = new float[6];

        public UvAtlas(FaceUVs f) {
            // +X = east, -X = west, +Y = top, -Y = bottom, +Z = south, -Z = north
            u0[0] = f.eastU0;  v0[0] = f.eastV0;  u1[0] = f.eastU1;  v1[0] = f.eastV1;
            u0[1] = f.westU0;  v0[1] = f.westV0;  u1[1] = f.westU1;  v1[1] = f.westV1;
            u0[2] = f.topU0;   v0[2] = f.topV0;   u1[2] = f.topU1;   v1[2] = f.topV1;
            u0[3] = f.bottomU0;v0[3] = f.bottomV0;u1[3] = f.bottomU1;v1[3] = f.bottomV1;
            u0[4] = f.southU0; v0[4] = f.southV0; u1[4] = f.southU1; v1[4] = f.southV1;
            u0[5] = f.northU0; v0[5] = f.northV0; u1[5] = f.northU1; v1[5] = f.northV1;
        }
    }

    private final Map<String, Entry> byName = new LinkedHashMap<>();
    private final Entry grass;
    private final Entry stone;
    private final Entry dirt;
    private final Entry sand;
    private final Entry water;
    private final Entry glass;
    private final Entry lava;
    private final Entry torch;
    private final Entry glowstone;
    private final Entry logSide;
    private final Entry leaves;
    private final Entry snow;

    public MinecraftMaterials() {
        // --- grass (top green, sides grass-dirt, bottom dirt) ---
        // terrain.png coords: grass top = (0,0) in atlas (col 0, row 0, atlas origin bottom-left)
        // grass side = (0,3) col 0 row 3, dirt = (2,0) col 2 row 0.
        // Our atlas is laid out with row 0 at the TOP so we flip v.
        // East/West/South/North are the grass side (col 0, row 3), top is
        // the grass top (col 0, row 0), bottom is dirt (col 2, row 0).
        FaceUVs grassFaces = new FaceUVs(
                tile(0, 3).u0, tile(0, 3).v0, tile(0, 3).u1, tile(0, 3).v1, // east side
                tile(0, 3).u0, tile(0, 3).v0, tile(0, 3).u1, tile(0, 3).v1, // west
                tile(0, 0).u0, tile(0, 0).v0, tile(0, 0).u1, tile(0, 0).v1, // top
                tile(2, 0).u0, tile(2, 0).v0, tile(2, 0).u1, tile(2, 0).v1, // bottom
                tile(0, 3).u0, tile(0, 3).v0, tile(0, 3).u1, tile(0, 3).v1, // south
                tile(0, 3).u0, tile(0, 3).v0, tile(0, 3).u1, tile(0, 3).v1  // north
        );
        Entry grassEntry = new Entry(0x7FB238, 0, 0, 1, 1,
                0.85f, 0.0f, 0.0f, 1.5f, 0.0f, false, false, "diffuse");
        grass = withFaceUVs(grassEntry, grassFaces);
        byName.put("grass", grass);

        // --- dirt ---
        FaceUVs dirtFaces = single(tile(2, 0));
        Entry dirtEntry = new Entry(0x8B6243, 0, 0, 1, 1,
                0.95f, 0.0f, 0.0f, 1.5f, 0.0f, false, false, "diffuse");
        dirt = withFaceUVs(dirtEntry, dirtFaces);
        byName.put("dirt", dirt);

        // --- stone ---
        FaceUVs stoneFaces = single(tile(1, 0));
        Entry stoneEntry = new Entry(0x808080, 0, 0, 1, 1,
                0.90f, 0.0f, 0.0f, 1.5f, 0.0f, false, false, "diffuse");
        stone = withFaceUVs(stoneEntry, stoneFaces);
        byName.put("stone", stone);
        byName.put("cobblestone", stone);

        // --- sand ---
        FaceUVs sandFaces = single(tile(2, 1));
        Entry sandEntry = new Entry(0xF7E9A3, 0, 0, 1, 1,
                0.95f, 0.0f, 0.0f, 1.5f, 0.0f, false, false, "diffuse");
        sand = withFaceUVs(sandEntry, sandFaces);
        byName.put("sand", sand);

        // --- water (transmissive, IOR 1.333, low roughness) ---
        FaceUVs waterFaces = single(tile(13, 12));
        Entry waterEntry = new Entry(0x3D75E0, 0, 0, 1, 1,
                0.05f, 0.0f, 0.75f, 1.333f, 0.0f, true, true, "water");
        water = withFaceUVs(waterEntry, waterFaces);
        byName.put("water", water);
        byName.put("flowing_water", water);

        // --- glass (transmissive, IOR 1.5) ---
        FaceUVs glassFaces = single(tile(1, 3));
        Entry glassEntry = new Entry(0xC8EFFF, 0, 0, 1, 1,
                0.05f, 0.0f, 0.92f, 1.5f, 0.0f, false, true, "glass");
        glass = withFaceUVs(glassEntry, glassFaces);
        byName.put("glass", glass);

        // --- lava (animated, emissive) ---
        FaceUVs lavaFaces = single(tile(13, 1));
        Entry lavaEntry = new Entry(0xE36B14, 0, 0, 1, 1,
                0.5f, 0.0f, 0.0f, 1.5f, 6.0f, true, false, "emissive");
        lava = withFaceUVs(lavaEntry, lavaFaces);
        byName.put("lava", lava);
        byName.put("flowing_lava", lava);

        // --- torch (emissive, small lamp) ---
        FaceUVs torchFaces = single(tile(0, 5));
        Entry torchEntry = new Entry(0xFFB85C, 0, 0, 1, 1,
                0.6f, 0.0f, 0.0f, 1.5f, 14.0f, false, false, "emissive");
        torch = withFaceUVs(torchEntry, torchFaces);
        byName.put("torch", torch);

        // --- glowstone (emissive block) ---
        FaceUVs glowFaces = single(tile(9, 6));
        Entry glowEntry = new Entry(0xFCDB6A, 0, 0, 1, 1,
                0.4f, 0.0f, 0.0f, 1.5f, 5.0f, false, false, "emissive");
        glowstone = withFaceUVs(glowEntry, glowFaces);
        byName.put("glowstone", glowstone);

        // --- log (oak): side = (4,1), top = (5,1) ---
        FaceUVs logFaces = new FaceUVs(
                tile(4, 1).u0, tile(4, 1).v0, tile(4, 1).u1, tile(4, 1).v1,
                tile(4, 1).u0, tile(4, 1).v0, tile(4, 1).u1, tile(4, 1).v1,
                tile(5, 1).u0, tile(5, 1).v0, tile(5, 1).u1, tile(5, 1).v1,
                tile(5, 1).u0, tile(5, 1).v0, tile(5, 1).u1, tile(5, 1).v1,
                tile(4, 1).u0, tile(4, 1).v0, tile(4, 1).u1, tile(4, 1).v1,
                tile(4, 1).u0, tile(4, 1).v0, tile(4, 1).u1, tile(4, 1).v1
        );
        Entry logSideEntry = new Entry(0x6E5436, 0, 0, 1, 1,
                0.95f, 0.0f, 0.0f, 1.5f, 0.0f, false, false, "diffuse");
        logSide = withFaceUVs(logSideEntry, logFaces);
        byName.put("log", logSide);
        byName.put("log2", logSide);
        byName.put("oak_log", logSide);

        // --- leaves ---
        FaceUVs leafFaces = single(tile(4, 8));
        Entry leafEntry = new Entry(0x4B7A2A, 0, 0, 1, 1,
                0.95f, 0.0f, 0.0f, 1.5f, 0.0f, false, true, "diffuse");
        leaves = withFaceUVs(leafEntry, leafFaces);
        byName.put("leaves", leaves);
        byName.put("leaves2", leaves);

        // --- snow ---
        FaceUVs snowFaces = single(tile(2, 4));
        Entry snowEntry = new Entry(0xFAFFFA, 0, 0, 1, 1,
                0.9f, 0.0f, 0.0f, 1.5f, 0.0f, false, false, "diffuse");
        snow = withFaceUVs(snowEntry, snowFaces);
        byName.put("snow", snow);
        byName.put("snow_layer", snow);

        // --- metal (iron, gold, diamond, emerald block) ---
        Entry metalEntry = new Entry(0xD8D8D8, 0, 0, 1, 1,
                0.35f, 1.0f, 0.0f, 1.5f, 0.0f, false, false, "metal");
        FaceUVs metalFaces = single(tile(7, 1));
        Entry iron = withFaceUVs(metalEntry, metalFaces);
        byName.put("iron_block", iron);
        byName.put("gold_block", new Entry(0xFCDB6A, 0, 0, 1, 1,
                0.20f, 1.0f, 0.0f, 1.5f, 0.0f, false, false, "metal"));
        byName.put("diamond_block", new Entry(0x5DECF5, 0, 0, 1, 1,
                0.05f, 1.0f, 0.0f, 1.5f, 0.0f, false, false, "metal"));
        byName.put("emerald_block", new Entry(0x4DEC82, 0, 0, 1, 1,
                0.20f, 1.0f, 0.0f, 1.5f, 0.0f, false, false, "metal"));
        byName.put("gold_ore", new Entry(0xFCDB6A, 0, 0, 1, 1,
                0.6f, 0.7f, 0.0f, 1.5f, 0.0f, false, false, "metal"));
        byName.put("iron_ore", new Entry(0xD8A878, 0, 0, 1, 1,
                0.7f, 0.4f, 0.0f, 1.5f, 0.0f, false, false, "metal"));
        byName.put("diamond_ore", new Entry(0x5DECF5, 0, 0, 1, 1,
                0.4f, 0.7f, 0.0f, 1.5f, 0.0f, false, false, "metal"));
    }

    /**
     * Resolves a block to a material entry. The blockIdentity is the
     * notch-style block-state class name (e.g. "ave" = grass, "abf" = water,
     * "aor" = torch). Unknown blocks fall back to a synthetic entry built
     * from the supplied map color.
     */
    public Entry resolve(String blockIdentity, int mapColor) {
        if (blockIdentity != null) {
            String key = canonicalize(blockIdentity);
            Entry cached = byName.get(key);
            if (cached != null) return cached;
        }
        // Fallback: synthesize a diffuse material from the map color.
        return new Entry(mapColor, 0, 0, 1, 1,
                0.95f, 0.0f, 0.0f, 1.5f, 0.0f, false, false, "diffuse");
    }

    /**
     * Returns the most common block material for the given name; identical
     * to {@link #resolve} but does not synthesize (returns grass as a safe
     * diffuse default).
     */
    public Entry resolveOrDefault(String blockIdentity) {
        if (blockIdentity != null) {
            String key = canonicalize(blockIdentity);
            Entry cached = byName.get(key);
            if (cached != null) return cached;
        }
        return grass;
    }

    /**
     * True for blocks whose top face is a translucent or transmissive fluid
     * surface (water). The renderer needs to know so it can emit a separate
     * "still water" surface mesh with the proper transmission parameters.
     */
    public boolean isWater(String blockIdentity) {
        if (blockIdentity == null) return false;
        String k = canonicalize(blockIdentity);
        return "water".equals(k) || "flowing_water".equals(k);
    }

    public boolean isLava(String blockIdentity) {
        if (blockIdentity == null) return false;
        String k = canonicalize(blockIdentity);
        return "lava".equals(k) || "flowing_lava".equals(k);
    }

    /** Blocks that contribute a point-light source (PART 2 - block light). */
    public boolean isLightEmitter(String blockIdentity) {
        if (blockIdentity == null) return false;
        String k = canonicalize(blockIdentity);
        return "torch".equals(k) || "glowstone".equals(k) || "lava".equals(k)
                || "flowing_lava".equals(k);
    }

    public Entry water() { return water; }
    public Entry glass() { return glass; }
    public Entry grass() { return grass; }
    public Entry stone() { return stone; }
    public Entry lava() { return lava; }
    public Entry torch() { return torch; }
    public Entry glowstone() { return glowstone; }
    public Entry snow() { return snow; }

    /** Returns an unmodifiable view of all named entries (for diagnostics). */
    public List<Entry> entries() {
        return Collections.unmodifiableList(new java.util.ArrayList<>(byName.values()));
    }

    private static String canonicalize(String blockIdentity) {
        // MC 1.12.2 block classes are obfuscated; we look for substrings
        // matching the canonical English block name, or fall back to the
        // last 2-3 letters of the class name as a pseudo-id.
        String low = blockIdentity.toLowerCase(Locale.ROOT);
        if (low.contains("grass")) return "grass";
        if (low.contains("dirt")) return "dirt";
        if (low.contains("stone")) return "stone";
        if (low.contains("cobblestone")) return "cobblestone";
        if (low.contains("sand")) return "sand";
        if (low.contains("water")) return "water";
        if (low.contains("glass")) return "glass";
        if (low.contains("lava")) return "lava";
        if (low.contains("torch")) return "torch";
        if (low.contains("glowstone")) return "glowstone";
        if (low.contains("log")) return "log";
        if (low.contains("leaves")) return "leaves";
        if (low.contains("snow")) return "snow";
        if (low.contains("iron_block") || low.contains("ironblock")) return "iron_block";
        if (low.contains("gold_block") || low.contains("goldblock")) return "gold_block";
        if (low.contains("diamond_block") || low.contains("diamondblock")) return "diamond_block";
        if (low.contains("emerald_block") || low.contains("emeraldblock")) return "emerald_block";
        if (low.contains("iron_ore") || low.contains("ironore")) return "iron_ore";
        if (low.contains("gold_ore") || low.contains("goldore")) return "gold_ore";
        if (low.contains("diamond_ore") || low.contains("diamondore")) return "diamond_ore";
        return low;
    }

    /** UV rect for one 16x16 tile in a 16x16 atlas, at (col,row), v-flipped. */
    private static FaceUVs single(Tile t) {
        return new FaceUVs(t.u0, t.v0, t.u1, t.v1,
                t.u0, t.v0, t.u1, t.v1,
                t.u0, t.v0, t.u1, t.v1,
                t.u0, t.v0, t.u1, t.v1,
                t.u0, t.v0, t.u1, t.v1,
                t.u0, t.v0, t.u1, t.v1);
    }

    private static FaceUVs cube(Tile e, Tile w, Tile top, Tile bot, Tile s, Tile n) {
        return new FaceUVs(e.u0, e.v0, e.u1, e.v1,
                w.u0, w.v0, w.u1, w.v1,
                top.u0, top.v0, top.u1, top.v1,
                bot.u0, bot.v0, bot.u1, bot.v1,
                s.u0, s.v0, s.u1, s.v1,
                n.u0, n.v0, n.u1, n.v1);
    }

    /** Holds a UV rect for one terrain tile. */
    private static final class Tile {
        final float u0, v0, u1, v1;
        Tile(float u0, float v0, float u1, float v1) {
            this.u0 = u0; this.v0 = v0; this.u1 = u1; this.v1 = v1;
        }
    }

    /**
     * Returns a tile at (col,row) in a 16x16 terrain.png atlas. The atlas
     * is laid out with v=0 at the BOTTOM in OpenGL convention; the
     * procedural atlas renderer flips rows so visual row 0 = top.
     */
    private static Tile tile(int col, int row) {
        float cell = 1.0f / 16.0f;
        float u0 = col * cell;
        float v0 = 1.0f - (row + 1) * cell;
        float u1 = (col + 1) * cell;
        float v1 = 1.0f - row * cell;
        return new Tile(u0, v0, u1, v1);
    }

    /**
     * Returns a new Entry whose per-face UVs are encoded in a sentinel UV
     * rect: we use the {@link FaceUVs#topU0} slot to carry a packed pointer
     * to the per-face array. The Java side decodes this in
     * {@link ChunkSceneExtractor} by reading {@link Entry#u0} as a flag.
     *
     * Actually: since the native addMesh already takes flat UVs, we just
     * store the face UVs alongside the entry and look them up in the
     * extractor by re-keying on a "tag" in the rgb field (top 8 bits).
     */
    private static Entry withFaceUVs(Entry base, FaceUVs faces) {
        // The face UVs are held in a side table keyed by base.rgb (which
        // // is unique per material). This avoids inventing a new Entry
        // // type; the extractor reads them via
        // // MinecraftMaterials.faceUVsFor(entry).
        FACE_UVS.put(base.rgb, faces);
        return base;
    }

    private static final Map<Integer, FaceUVs> FACE_UVS = new LinkedHashMap<>();

    public FaceUVs faceUVsFor(Entry entry) {
        return FACE_UVS.getOrDefault(entry.rgb, single(tile(0, 0)));
    }
}
