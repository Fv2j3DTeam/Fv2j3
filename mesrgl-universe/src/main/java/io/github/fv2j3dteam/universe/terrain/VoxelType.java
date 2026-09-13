package io.github.fv2j3dteam.universe.terrain;

/**
 * Voxel/material id type. Values are non-negative. 0 is reserved for "air".
 * 1..1024 are reserved for built-in materials. ≥1025 are user-defined.
 */
public final class VoxelType {
    public static final int AIR = 0;
    public static final int STONE = 1;
    public static final int DIRT = 2;
    public static final int GRASS = 3;
    public static final int SAND = 4;
    public static final int SNOW = 5;
    public static final int ICE = 6;
    public static final int WATER = 7;
    public static final int LAVA = 8;
    public static final int SANDSTONE = 9;
    public static final int GRAVEL = 10;
    public static final int BEDROCK = 11;
    public static final int WOOD = 12;
    public static final int LEAVES = 13;
    public static final int CLAY = 14;
    public static final int OBSIDIAN = 15;
    public static final int ASH = 16;
    public static final int COAL_ORE = 17;
    public static final int IRON_ORE = 18;
    public static final int COPPER_ORE = 19;
    public static final int USER_DEFINED_BASE = 1024;

    private VoxelType() {}

    public static boolean isFluid(int id) { return id == WATER || id == LAVA; }
    public static boolean isSolid(int id) { return id != AIR && !isFluid(id); }
    public static boolean isFlammable(int id) { return id == WOOD || id == LEAVES || id == GRASS; }
    public static boolean isPermeable(int id) { return id == AIR || id == SAND || id == GRAVEL || id == SNOW; }
}
