package io.github.fv2j3dteam.mesrgl.integration.minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts renderable geometry from loaded Minecraft chunks into
 * per-color-group triangle batches for the MesrGL scene.
 *
 * Extraction model (spec 18: world data and render data are decoupled):
 *  - the Minecraft world is only read, through reflection, into
 *    renderer-local triangle lists; the extractor owns no MC state
 *  - chunks are cached by (x,z) WITH their extracted geometry; a chunk is
 *    scanned exactly once when it first arrives, never again — the render
 *    thread cannot afford reflective rescan of unchanged chunks per frame
 *  - a face is emitted where the neighbor block's quantized map color
 *    differs from the block's (air is calibrated from the sky at runtime);
 *    unloaded neighbors are treated as hidden so a partially streamed
 *    world never explodes the mesh; when a chunk arrives it invalidates
 *    its neighbors so their shared boundary is re-extracted once
 *  - triangles are batched one mesh per quantized map color, matching the
 *    bridge's one-material-per-mesh contract
 */
final class ChunkSceneExtractor {

    /** One drawable color group: vertex arrays + matching material color. */
    static final class ColorGroup {
        final int rgb;
        final GrowableFloatArray positions = new GrowableFloatArray();
        final GrowableFloatArray normals = new GrowableFloatArray();
        final GrowableFloatArray texCoords = new GrowableFloatArray();
        final GrowableIntArray indices = new GrowableIntArray();
        /** Block identity (registry name) of the first block in this group. */
        String identity = "";

        ColorGroup(int rgb) {
            this.rgb = rgb;
        }

        int triangleCount() {
            return indices.size() / 3;
        }

        float[] positionsArray() {
            return positions.toArray();
        }

        float[] normalsArray() {
            return normals.toArray();
        }

        float[] texCoordsArray() {
            return texCoords.toArray();
        }

        int[] indicesArray() {
            return indices.toArray();
        }
    }

    /** Result of a scene build pass. */
    static final class ExtractedScene {
        final List<ColorGroup> groups = new ArrayList<>();
        final List<CachedChunk> cachedChunks = new ArrayList<>();
        int chunkCount;
        int blockCount;
        int radiusChunks;
        int maxHeight;
    }

    private static final int CHUNK_SIZE = 16;

    private final MinecraftReflection mc;
    private final int radiusChunks;
    private final int maxHeight;

    private final Map<Long, CachedChunk> cache = new HashMap<>();
    private long lastNullSweepMs;

    ChunkSceneExtractor(MinecraftReflection mc, int radiusChunks, int maxHeight) {
        this.mc = mc;
        this.radiusChunks = Math.max(1, radiusChunks);
        this.maxHeight = Math.max(16, maxHeight);
    }

    static final class CachedChunk {
        final int x;
        final int z;
        final Object chunk;
        /** Geometry extracted once when the chunk was cached. */
        final Map<Integer, ColorGroup> groups = new HashMap<>();
        long blockCount;

        CachedChunk(int x, int z, Object chunk) {
            this.x = x;
            this.z = z;
            this.chunk = chunk;
        }
    }

    /** Exposes a CachedChunk to other classes in the package. */
    static CachedChunk exposedChunk(CachedChunk c) { return c; }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    private CachedChunk chunkAt(int cx, int cz) {
        return cache.get(key(cx, cz));
    }

    /**
     * Extracts not-yet-cached chunks around the camera, nearest ring first,
     * bounded by {@code budget}. Returns how many real chunks were extracted;
     * a nonzero result means the scene must be rebuilt.
     */
    int update(Object world, double camX, double camZ, int budget, long nowMs) {
        // Absent chunks are cached as null so they do not retry every frame,
        // but the world keeps streaming chunks in after the first frame: sweep
        // the null markers periodically so late chunks still get extracted.
        if (nowMs - lastNullSweepMs > 2000) {
            cache.values().removeIf(java.util.Objects::isNull);
            lastNullSweepMs = nowMs;
        }
        List<Object> chunks = mc.loadedChunks(world);
        int centerCx = Math.floorDiv((int) Math.floor(camX), CHUNK_SIZE);
        int centerCz = Math.floorDiv((int) Math.floor(camZ), CHUNK_SIZE);
        int extracted = 0;

        for (int r = 0; r <= radiusChunks && extracted < budget; r++) {
            for (int dx = -r; dx <= r && extracted < budget; dx++) {
                for (int dz = -r; dz <= r && extracted < budget; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue; // ring perimeter only
                    }
                    int cx = centerCx + dx;
                    int cz = centerCz + dz;
                    long key = key(cx, cz);
                    if (cache.containsKey(key)) {
                        continue;
                    }
                    Object chunk = findChunk(chunks, cx, cz);
                    // Absent chunks are cached as null so they do not retry
                    // every frame; a world change clears the cache entirely.
                    cache.put(key, chunk == null ? null : cacheChunk(chunk, cx, cz));
                    if (chunk != null) {
                        extracted++;
                        // The new chunk changes face visibility across its
                        // borders: re-extract the loaded neighbors once so
                        // their shared boundary faces appear.
                        invalidateNeighbors(cx, cz);
                    }
                }
            }
        }
        return extracted;
    }

    /** Build a CachedChunk and run the one-shot geometry extraction. */
    private CachedChunk cacheChunk(Object chunk, int cx, int cz) {
        CachedChunk cached = new CachedChunk(cx, cz, chunk);
        extractChunk(cached);
        return cached;
    }

    private void invalidateNeighbors(int cx, int cz) {
        for (int d = 0; d < DIR_X.length; d++) {
            if (DIR_Y[d] != 0 || (DIR_X[d] == 0 && DIR_Z[d] == 0)) {
                continue;
            }
            // Only null markers (chunk was absent when first probed) need a
            // re-probe; re-extracting a real neighbor would ping-pong the
            // two chunks into an endless invalidation loop.
            Long neighborKey = key(cx + DIR_X[d], cz + DIR_Z[d]);
            if (cache.containsKey(neighborKey) && cache.get(neighborKey) == null) {
                cache.remove(neighborKey);
            }
        }
    }

    /**
     * Scans one chunk's blocks and fills its color groups. Runs exactly once
     * per chunk (when it first arrives), never per frame.
     */
    private void extractChunk(CachedChunk c) {
        int baseX = c.x * CHUNK_SIZE;
        int baseZ = c.z * CHUNK_SIZE;
        for (int lx = 0; lx < CHUNK_SIZE; lx++) {
            for (int lz = 0; lz < CHUNK_SIZE; lz++) {
                for (int y = 0; y < maxHeight; y++) {
                    Object state = mc.blockState(c.chunk, lx, y, lz);
                    if (state == null) {
                        continue;
                    }
                    int color = mc.mapColor(state);
                    if (color < 0) {
                        continue; // air
                    }
                    c.blockCount++;
                    emitVisibleFaces(c.groups, c, state, color, baseX + lx, y, baseZ + lz, lx, y, lz);
                }
            }
        }
    }

    private Object findChunk(List<Object> chunks, int cx, int cz) {
        for (Object chunk : chunks) {
            if (mc.chunkX(chunk) == cx && mc.chunkZ(chunk) == cz) {
                return chunk;
            }
        }
        return null;
    }

    /** Drops all cached chunks (world switch, dimension change). */
    void reset() {
        cache.clear();
    }

    int cachedChunkCount() {
        int n = 0;
        for (CachedChunk c : cache.values()) {
            if (c != null) n++;
        }
        return n;
    }

    /**
     * Merges the per-chunk geometry into scene groups. Cost is linear in the
     * already-extracted data; never re-scans Minecraft blocks.
     */
    ExtractedScene buildScene() {
        ExtractedScene scene = new ExtractedScene();
        scene.radiusChunks = radiusChunks;
        scene.maxHeight = maxHeight;
        scene.cachedChunks.addAll(cache.values());
        Map<Integer, ColorGroup> merged = new HashMap<>();

        for (CachedChunk c : cache.values()) {
            if (c == null) continue;
            scene.chunkCount++;
            scene.blockCount += c.blockCount;
            for (Map.Entry<Integer, ColorGroup> e : c.groups.entrySet()) {
                ColorGroup target = merged.computeIfAbsent(e.getKey(), k -> new ColorGroup(k));
                ColorGroup src = e.getValue();
                append(target, src);
                if (target.identity.isEmpty()) {
                    target.identity = src.identity;
                }
            }
        }
        scene.groups.addAll(merged.values());
        return scene;
    }

    private static void append(ColorGroup target, ColorGroup src) {
        int vertexBase = target.positions.size() / 3;
        for (int i = 0; i < src.indices.size(); i++) {
            target.indices.add(src.indices.get(i) + vertexBase);
        }
        target.positions.append(src.positions);
        target.normals.append(src.normals);
        target.texCoords.append(src.texCoords);
    }

    private void emitVisibleFaces(Map<Integer, ColorGroup> groups, CachedChunk own, Object state,
                                  int color, int wx, int wy, int wz, int lx, int y, int lz) {
        int q = quantize(color);
        ColorGroup group = groups.computeIfAbsent(q, k -> {
            ColorGroup g = new ColorGroup(k);
            String id = mc.blockIdentity(state);
            g.identity = id == null ? "" : id;
            return g;
        });
        for (int dir = 0; dir < 6; dir++) {
            int nx = lx + DIR_X[dir];
            int ny = y + DIR_Y[dir];
            int nz = lz + DIR_Z[dir];
            boolean visible;
            if (ny < 0) {
                visible = false; // below the world
            } else if (ny >= maxHeight) {
                visible = true;  // open sky above the scan window
            } else if (nx >= 0 && nx < CHUNK_SIZE && nz >= 0 && nz < CHUNK_SIZE) {
                visible = neighborVisible(own, nx, ny, nz, color);
            } else {
                CachedChunk neighbor = chunkAt(own.x + DIR_X[dir], own.z + DIR_Z[dir]);
                if (neighbor == null) {
                    // Unloaded neighbor chunk: treat as hidden. Emitting these
                    // faces explodes the mesh (every edge chunk × every block);
                    // when the neighbor arrives, this chunk is re-extracted.
                    visible = false;
                } else {
                    visible = neighborVisible(neighbor,
                            Math.floorMod(nx, CHUNK_SIZE), ny,
                            Math.floorMod(nz, CHUNK_SIZE), color);
                }
            }
            if (visible) {
                addFace(group, wx, wy, wz, dir);
            }
        }
    }

    /** A face is visible when the neighbor is air or a different color family. */
    private boolean neighborVisible(CachedChunk owner, int lx, int y, int lz, int ownColor) {
        Object neighbor = mc.blockState(owner.chunk, lx, y, lz);
        if (neighbor == null) {
            return true;
        }
        int neighborColor = mc.mapColor(neighbor);
        return neighborColor < 0 || quantize(neighborColor) != quantize(ownColor);
    }

    /** 5 bits per channel: bounded mesh count, visible color separation. */
    private static int quantize(int rgb) {
        int r = (rgb >> 16) & 0xF8;
        int g = (rgb >> 8) & 0xF8;
        int b = rgb & 0xF8;
        return (r << 16) | (g << 8) | b;
    }

    private static final int[] DIR_X = {1, -1, 0, 0, 0, 0};
    private static final int[] DIR_Y = {0, 0, 1, -1, 0, 0};
    private static final int[] DIR_Z = {0, 0, 0, 0, 1, -1};

    // Winding is counter-clockwise seen from outside the block.
    private static final float[][] FACE_VERTS = {
            {1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1}, // +X
            {0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0}, // -X
            {0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0}, // +Y
            {0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1}, // -Y
            {0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1}, // +Z
            {1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0}, // -Z
    };
    private static final float[][] FACE_NORMALS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1},
    };

    private static void addFace(ColorGroup group, int wx, int wy, int wz, int dir) {
        float[] v = FACE_VERTS[dir];
        float[] n = FACE_NORMALS[dir];
        int vertexBase = group.positions.size() / 3;
        for (int i = 0; i < 4; i++) {
            group.positions.add(wx + v[i * 3]);
            group.positions.add(wy + v[i * 3 + 1]);
            group.positions.add(wz + v[i * 3 + 2]);
            group.normals.add(n[0]);
            group.normals.add(n[1]);
            group.normals.add(n[2]);
            group.texCoords.add(0.0f);
            group.texCoords.add(0.0f);
        }
        group.indices.add(vertexBase);
        group.indices.add(vertexBase + 1);
        group.indices.add(vertexBase + 2);
        group.indices.add(vertexBase);
        group.indices.add(vertexBase + 2);
        group.indices.add(vertexBase + 3);
    }
}
