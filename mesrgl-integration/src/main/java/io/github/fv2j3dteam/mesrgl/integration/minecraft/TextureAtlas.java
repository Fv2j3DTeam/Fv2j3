package io.github.fv2j3dteam.mesrgl.integration.minecraft;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/**
 * Synthetic Minecraft terrain.png atlas (PART 1 - texture atlas).
 *
 * The real terrain.png is shipped inside the 1.12.2 jar at
 * assets/minecraft/textures/blocks/terrain.png. We do not extract it
 * here (we only read the Block class, not asset bytes). Instead we
 * procedurally paint a 16x16 tile grid that matches the canonical 1.12
 * terrain.png layout: each tile is 16x16, and the tiles we use are
 * grass top, grass side, dirt, stone, sand, water still, glass, lava
 * still, log side, log top, leaves, snow, plus the metal set.
 *
 * The atlas is exposed as RGBA8 bytes (256x256). The UV coordinates
 * in {@link MinecraftMaterials} address tiles in the same grid.
 *
 * Why procedural instead of loading the real png: the renderer must
 * work in stress/replay mode without the full game jar, and the audit
 * shows the CPU reference ray tracer does not sample textures. UVs
 * flow into the mesh for forward compatibility; the visual color is
 * the average of each tile, applied as per-vertex albedo.
 */
public final class TextureAtlas {

    public static final int TILE_SIZE = 16;
    public static final int GRID_SIZE = 16;
    public static final int SIZE = TILE_SIZE * GRID_SIZE; // 256

    private final byte[] rgba = new byte[SIZE * SIZE * 4];

    public TextureAtlas() {
        // Fill with transparent first; cells are overwritten below.
        Arrays.fill(rgba, (byte) 0);

        // Tile 0: row 0 col 0..3: grass top + dirt (we use col 0 row 0 for grass-top).
        // The 1.12 atlas uses (col, row) = (0,0) grass-top, (0,3) grass-side,
        // (2,0) dirt, (1,0) stone, (2,1) sand, (13,12) water, (1,3) glass,
        // (13,1) lava, (4,1) log side, (5,1) log top, (4,8) leaves, (2,4) snow.
        paintGrassTop(0, 0);
        paintGrassSide(0, 3);
        paintDirt(2, 0);
        paintStone(1, 0);
        paintCobble(0, 1);
        paintSand(2, 1);
        paintWater(13, 12);
        paintGlass(1, 3);
        paintLava(13, 1);
        paintLogSide(4, 1);
        paintLogTop(5, 1);
        paintLeaves(4, 8);
        paintSnow(2, 4);
        paintOre(7, 1, 0xC8C8C8, 0x6F6F6F);
        paintOre(8, 1, 0xFCDB6A, 0x8B6F1F);
        paintOre(11, 2, 0x5DECF5, 0x2E8B8B);
        paintGold(8, 6, 0xFCDB6A);
        paintGold(10, 2, 0x5DECF5);
        paintGold(11, 6, 0x4DEC82);
        // torch is a small sprite; we use col 0 row 5
        paintTorch(0, 5);
        // glowstone: col 9 row 6
        paintGlowstone(9, 6);
    }

    public byte[] rgba() {
        return rgba;
    }

    public int width() {
        return SIZE;
    }

    public int height() {
        return SIZE;
    }

    /**
     * Returns the average color of a tile in the atlas, used as a per-vertex
     * albedo when the renderer cannot sample the texture directly.
     */
    public int averageColor(int col, int row) {
        int x0 = col * TILE_SIZE;
        int y0 = row * TILE_SIZE;
        long r = 0, g = 0, b = 0, a = 0;
        for (int dy = 0; dy < TILE_SIZE; dy++) {
            for (int dx = 0; dx < TILE_SIZE; dx++) {
                int i = ((y0 + dy) * SIZE + (x0 + dx)) * 4;
                r += rgba[i] & 0xFF;
                g += rgba[i + 1] & 0xFF;
                b += rgba[i + 2] & 0xFF;
                a += rgba[i + 3] & 0xFF;
            }
        }
        int n = TILE_SIZE * TILE_SIZE;
        int ar = (int) (r / n);
        int ag = (int) (g / n);
        int ab = (int) (b / n);
        return (ar << 16) | (ag << 8) | ab;
    }

    // -- paint helpers ----------------------------------------------------

    private void fillTile(int col, int row, int color, int alpha) {
        int x0 = col * TILE_SIZE;
        int y0 = row * TILE_SIZE;
        for (int dy = 0; dy < TILE_SIZE; dy++) {
            for (int dx = 0; dx < TILE_SIZE; dx++) {
                int i = ((y0 + dy) * SIZE + (x0 + dx)) * 4;
                rgba[i]     = (byte) ((color >> 16) & 0xFF);
                rgba[i + 1] = (byte) ((color >> 8) & 0xFF);
                rgba[i + 2] = (byte) (color & 0xFF);
                rgba[i + 3] = (byte) alpha;
            }
        }
    }

    private void noiseTile(int col, int row, int base, int variance) {
        // Deterministic noise based on a per-tile seed.
        long seed = (long) col * 1009L + (long) row * 9176L + 7L;
        Random rng = new Random(seed);
        int x0 = col * TILE_SIZE;
        int y0 = row * TILE_SIZE;
        for (int dy = 0; dy < TILE_SIZE; dy++) {
            for (int dx = 0; dx < TILE_SIZE; dx++) {
                int j = rng.nextInt(variance * 2 + 1) - variance;
                int r = clamp(((base >> 16) & 0xFF) + j);
                int g = clamp(((base >> 8) & 0xFF) + j);
                int b = clamp((base & 0xFF) + j);
                int i = ((y0 + dy) * SIZE + (x0 + dx)) * 4;
                rgba[i]     = (byte) r;
                rgba[i + 1] = (byte) g;
                rgba[i + 2] = (byte) b;
                rgba[i + 3] = (byte) 255;
            }
        }
    }

    private void paintGrassTop(int col, int row) {
        noiseTile(col, row, 0x7FB238, 14);
    }

    private void paintGrassSide(int col, int row) {
        // Top 4 rows: grass, bottom 12: dirt. We approximate by a banded
        // noise + a single "dirt" strip below row 4 of the tile.
        int x0 = col * TILE_SIZE;
        int y0 = row * TILE_SIZE;
        long seed = (long) col * 1009L + (long) row * 9176L + 13L;
        Random rng = new Random(seed);
        for (int dy = 0; dy < TILE_SIZE; dy++) {
            for (int dx = 0; dx < TILE_SIZE; dx++) {
                int j = rng.nextInt(12) - 6;
                int r, g, b;
                if (dy >= 4) {
                    r = clamp(0x8B + j);
                    g = clamp(0x62 + j);
                    b = clamp(0x43 + j);
                } else {
                    r = clamp(0x7F + j);
                    g = clamp(0xB2 + j);
                    b = clamp(0x38 + j);
                }
                int i = ((y0 + dy) * SIZE + (x0 + dx)) * 4;
                rgba[i] = (byte) r; rgba[i + 1] = (byte) g; rgba[i + 2] = (byte) b;
                rgba[i + 3] = (byte) 255;
            }
        }
    }

    private void paintDirt(int col, int row) {
        noiseTile(col, row, 0x8B6243, 12);
    }

    private void paintStone(int col, int row) {
        noiseTile(col, row, 0x808080, 12);
    }

    private void paintCobble(int col, int row) {
        // Alternating dark/light cells to simulate cobble pattern.
        int x0 = col * TILE_SIZE;
        int y0 = row * TILE_SIZE;
        long seed = (long) col * 1009L + (long) row * 9176L + 17L;
        Random rng = new Random(seed);
        for (int dy = 0; dy < TILE_SIZE; dy++) {
            for (int dx = 0; dx < TILE_SIZE; dx++) {
                boolean dark = ((dx / 4 + dy / 4) & 1) == 0;
                int j = rng.nextInt(10) - 5;
                int base = dark ? 0x6F6F6F : 0x909090;
                int r = clamp(((base >> 16) & 0xFF) + j);
                int g = clamp(((base >> 8) & 0xFF) + j);
                int b = clamp((base & 0xFF) + j);
                int i = ((y0 + dy) * SIZE + (x0 + dx)) * 4;
                rgba[i] = (byte) r; rgba[i + 1] = (byte) g; rgba[i + 2] = (byte) b;
                rgba[i + 3] = (byte) 255;
            }
        }
    }

    private void paintSand(int col, int row) {
        noiseTile(col, row, 0xF7E9A3, 8);
    }

    private void paintWater(int col, int row) {
        // Translucent blue: alpha 180.
        int x0 = col * TILE_SIZE;
        int y0 = row * TILE_SIZE;
        long seed = (long) col * 1009L + (long) row * 9176L + 23L;
        Random rng = new Random(seed);
        for (int dy = 0; dy < TILE_SIZE; dy++) {
            for (int dx = 0; dx < TILE_SIZE; dx++) {
                int j = rng.nextInt(10) - 5;
                int r = clamp(0x3D + j / 2);
                int g = clamp(0x75 + j);
                int b = clamp(0xE0 + j);
                int i = ((y0 + dy) * SIZE + (x0 + dx)) * 4;
                rgba[i] = (byte) r; rgba[i + 1] = (byte) g; rgba[i + 2] = (byte) b;
                rgba[i + 3] = (byte) 200;
            }
        }
    }

    private void paintGlass(int col, int row) {
        int x0 = col * TILE_SIZE;
        int y0 = row * TILE_SIZE;
        for (int dy = 0; dy < TILE_SIZE; dy++) {
            for (int dx = 0; dx < TILE_SIZE; dx++) {
                int i = ((y0 + dy) * SIZE + (x0 + dx)) * 4;
                boolean frame = dx == 0 || dx == TILE_SIZE - 1 || dy == 0 || dy == TILE_SIZE - 1;
                if (frame) {
                    rgba[i] = (byte) 0xC8; rgba[i + 1] = (byte) 0xEF; rgba[i + 2] = (byte) 0xFF;
                    rgba[i + 3] = (byte) 220;
                } else {
                    rgba[i] = (byte) 0xC8; rgba[i + 1] = (byte) 0xEF; rgba[i + 2] = (byte) 0xFF;
                    rgba[i + 3] = (byte) 40;
                }
            }
        }
    }

    private void paintLava(int col, int row) {
        int x0 = col * TILE_SIZE;
        int y0 = row * TILE_SIZE;
        long seed = (long) col * 1009L + (long) row * 9176L + 31L;
        Random rng = new Random(seed);
        for (int dy = 0; dy < TILE_SIZE; dy++) {
            for (int dx = 0; dx < TILE_SIZE; dx++) {
                int j = rng.nextInt(28) - 14;
                int r = clamp(0xE3 + j);
                int g = clamp(0x6B + j / 2);
                int b = clamp(0x14);
                int i = ((y0 + dy) * SIZE + (x0 + dx)) * 4;
                rgba[i] = (byte) r; rgba[i + 1] = (byte) g; rgba[i + 2] = (byte) b;
                rgba[i + 3] = (byte) 255;
            }
        }
    }

    private void paintLogSide(int col, int row) {
        int x0 = col * TILE_SIZE;
        int y0 = row * TILE_SIZE;
        for (int dy = 0; dy < TILE_SIZE; dy++) {
            for (int dx = 0; dx < TILE_SIZE; dx++) {
                int j = ((dx * 5) ^ (dy * 3)) & 7;
                int r = clamp(0x6E + j - 3);
                int g = clamp(0x54 + j - 3);
                int b = clamp(0x36 + j / 2);
                int i = ((y0 + dy) * SIZE + (x0 + dx)) * 4;
                rgba[i] = (byte) r; rgba[i + 1] = (byte) g; rgba[i + 2] = (byte) b;
                rgba[i + 3] = (byte) 255;
            }
        }
    }

    private void paintLogTop(int col, int row) {
        int x0 = col * TILE_SIZE;
        int y0 = row * TILE_SIZE;
        for (int dy = 0; dy < TILE_SIZE; dy++) {
            for (int dx = 0; dx < TILE_SIZE; dx++) {
                double cx = dx - 7.5, cy = dy - 7.5;
                double r = Math.sqrt(cx * cx + cy * cy);
                int base = (int) (0x9A - Math.min(r * 8, 0x20));
                int g = clamp(0x82 - (int) (r * 4));
                int b = clamp(0x4A - (int) (r * 4));
                int i = ((y0 + dy) * SIZE + (x0 + dx)) * 4;
                rgba[i] = (byte) clamp(base); rgba[i + 1] = (byte) g; rgba[i + 2] = (byte) b;
                rgba[i + 3] = (byte) 255;
            }
        }
    }

    private void paintLeaves(int col, int row) {
        int x0 = col * TILE_SIZE;
        int y0 = row * TILE_SIZE;
        long seed = (long) col * 1009L + (long) row * 9176L + 41L;
        Random rng = new Random(seed);
        for (int dy = 0; dy < TILE_SIZE; dy++) {
            for (int dx = 0; dx < TILE_SIZE; dx++) {
                int j = rng.nextInt(30) - 15;
                int r = clamp(0x4B + j);
                int g = clamp(0x7A + j / 2);
                int b = clamp(0x2A + j / 2);
                int i = ((y0 + dy) * SIZE + (x0 + dx)) * 4;
                rgba[i] = (byte) r; rgba[i + 1] = (byte) g; rgba[i + 2] = (byte) b;
                rgba[i + 3] = (byte) 230;
            }
        }
    }

    private void paintSnow(int col, int row) {
        noiseTile(col, row, 0xFAFFFA, 6);
    }

    private void paintOre(int col, int row, int base, int dark) {
        int x0 = col * TILE_SIZE;
        int y0 = row * TILE_SIZE;
        long seed = (long) col * 1009L + (long) row * 9176L + 47L;
        Random rng = new Random(seed);
        for (int dy = 0; dy < TILE_SIZE; dy++) {
            for (int dx = 0; dx < TILE_SIZE; dx++) {
                boolean speck = rng.nextInt(8) == 0;
                int color = speck ? base : dark;
                int i = ((y0 + dy) * SIZE + (x0 + dx)) * 4;
                rgba[i] = (byte) ((color >> 16) & 0xFF);
                rgba[i + 1] = (byte) ((color >> 8) & 0xFF);
                rgba[i + 2] = (byte) (color & 0xFF);
                rgba[i + 3] = (byte) 255;
            }
        }
    }

    private void paintGold(int col, int row, int color) {
        fillTile(col, row, color, 255);
        // a single pixel of darker shade in each corner for visual interest
        int x0 = col * TILE_SIZE;
        int y0 = row * TILE_SIZE;
        int[] corners = {0, 0, TILE_SIZE - 1, 0, 0, TILE_SIZE - 1, TILE_SIZE - 1, TILE_SIZE - 1};
        for (int k = 0; k < corners.length; k += 2) {
            int i = ((y0 + corners[k + 1]) * SIZE + (x0 + corners[k])) * 4;
            rgba[i] = (byte) 0x80;
            rgba[i + 1] = (byte) 0x6F;
            rgba[i + 2] = (byte) 0x10;
        }
    }

    private void paintTorch(int col, int row) {
        // Mostly transparent, a small yellow/orange rod.
        int x0 = col * TILE_SIZE;
        int y0 = row * TILE_SIZE;
        for (int dy = 0; dy < TILE_SIZE; dy++) {
            for (int dx = 0; dx < TILE_SIZE; dx++) {
                int i = ((y0 + dy) * SIZE + (x0 + dx)) * 4;
                boolean rod = (dx >= 7 && dx <= 8) && (dy >= 6 && dy <= 14);
                boolean flame = (dx >= 6 && dx <= 9) && (dy >= 2 && dy <= 7);
                if (rod) {
                    rgba[i] = (byte) 0x99; rgba[i + 1] = (byte) 0x6F; rgba[i + 2] = (byte) 0x33;
                    rgba[i + 3] = (byte) 255;
                } else if (flame) {
                    rgba[i] = (byte) 0xFF; rgba[i + 1] = (byte) 0xB8; rgba[i + 2] = (byte) 0x5C;
                    rgba[i + 3] = (byte) 255;
                } else {
                    rgba[i + 3] = (byte) 0;
                }
            }
        }
    }

    private void paintGlowstone(int col, int row) {
        int x0 = col * TILE_SIZE;
        int y0 = row * TILE_SIZE;
        long seed = (long) col * 1009L + (long) row * 9176L + 53L;
        Random rng = new Random(seed);
        for (int dy = 0; dy < TILE_SIZE; dy++) {
            for (int dx = 0; dx < TILE_SIZE; dx++) {
                int j = rng.nextInt(30);
                int r = clamp(0xFC + j / 4);
                int g = clamp(0xDB + j / 4);
                int b = clamp(0x6A + j / 2);
                int i = ((y0 + dy) * SIZE + (x0 + dx)) * 4;
                rgba[i] = (byte) r; rgba[i + 1] = (byte) g; rgba[i + 2] = (byte) b;
                rgba[i + 3] = (byte) 255;
            }
        }
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    /** PNG encode (just for diagnostic dumps). */
    public byte[] encodePngHeader() {
        // We don't ship a PNG encoder; the atlas is consumed in-memory.
        return rgba;
    }

    @Override
    public String toString() {
        return "TextureAtlas " + SIZE + "x" + SIZE + " (procedural terrain.png)";
    }

    /** Block identity → (col, row) hint for the UV resolver. */
    public static int[] tileFor(String blockIdentity) {
        String low = blockIdentity == null ? "" : blockIdentity.toLowerCase(Locale.ROOT);
        if (low.contains("grass")) return new int[]{0, 3};
        if (low.contains("dirt")) return new int[]{2, 0};
        if (low.contains("cobble")) return new int[]{0, 1};
        if (low.contains("stone")) return new int[]{1, 0};
        if (low.contains("sand")) return new int[]{2, 1};
        if (low.contains("water")) return new int[]{13, 12};
        if (low.contains("glass")) return new int[]{1, 3};
        if (low.contains("lava")) return new int[]{13, 1};
        if (low.contains("torch")) return new int[]{0, 5};
        if (low.contains("glowstone")) return new int[]{9, 6};
        if (low.contains("log")) return new int[]{4, 1};
        if (low.contains("leaves")) return new int[]{4, 8};
        if (low.contains("snow")) return new int[]{2, 4};
        if (low.contains("iron_block") || low.contains("ironblock")) return new int[]{7, 1};
        if (low.contains("gold_block") || low.contains("goldblock")) return new int[]{8, 6};
        if (low.contains("diamond_block") || low.contains("diamondblock")) return new int[]{10, 2};
        if (low.contains("emerald_block") || low.contains("emeraldblock")) return new int[]{11, 6};
        if (low.contains("iron_ore") || low.contains("ironore")) return new int[]{7, 1};
        if (low.contains("gold_ore") || low.contains("goldore")) return new int[]{8, 1};
        if (low.contains("diamond_ore") || low.contains("diamondore")) return new int[]{11, 2};
        return new int[]{0, 0};
    }
}
