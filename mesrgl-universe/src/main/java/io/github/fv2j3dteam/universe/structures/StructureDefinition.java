package io.github.fv2j3dteam.universe.structures;

import java.util.Objects;

/** Structure kind definition (§16). */
public final class StructureDefinition {
    public enum Kind { STATION, RUIN, ANOMALY, BEACON, MONOLITH, OUTPOST, LAB, SHRINE }
    private final String id;
    private final Kind kind;
    private final double rarity; // 0..1 (lower = rarer)
    private final String preferredBiome;
    private final double minDistance;

    public StructureDefinition(String id, Kind kind, double rarity, String preferredBiome, double minDistance) {
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        if (rarity < 0 || rarity > 1) throw new IllegalArgumentException("rarity");
        this.rarity = rarity;
        this.preferredBiome = preferredBiome == null ? "" : preferredBiome;
        this.minDistance = minDistance;
    }

    public String id() { return id; }
    public Kind kind() { return kind; }
    public double rarity() { return rarity; }
    public String preferredBiome() { return preferredBiome; }
    public double minDistance() { return minDistance; }

    public boolean matchesBiome(String biomeId) {
        return preferredBiome.isEmpty() || preferredBiome.equals(biomeId);
    }
}
