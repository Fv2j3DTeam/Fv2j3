package com.fv2j3.universe.identifiers;

import java.util.Arrays;
import java.util.Objects;

/**
 * Stable hierarchical identifier for a location in the universe hierarchy.
 * Composed of: universe, galaxy, system, body (planet/moon), region, chunk, salt.
 *
 * All parts are {@code long}; the record is fully serialisable and equality-based.
 * Stable across process restarts and save/load.
 */
public record UniverseId(
        long universe,
        long galaxy,
        long system,
        long body,
        long region,
        long chunk,
        long salt
) implements Comparable<UniverseId> {

    public static final long UNASSIGNED = Long.MIN_VALUE;

    /** Number of bytes used by binary serialisation (7 longs). */
    public static final int BYTES = 7 * Long.BYTES;

    public UniverseId {
        if (universe == UNASSIGNED) throw new IllegalArgumentException("universe must be assigned");
        if (galaxy == UNASSIGNED) throw new IllegalArgumentException("galaxy must be assigned");
        if (system == UNASSIGNED) throw new IllegalArgumentException("system must be assigned");
        if (body == UNASSIGNED) throw new IllegalArgumentException("body must be assigned");
    }

    /** Creates a top-level universe id (galaxy/system/body = 0, representing the universe itself). */
    public static UniverseId universeRoot(long universe) {
        return new UniverseId(universe, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    public UniverseId withGalaxy(long galaxy) {
        return new UniverseId(universe, galaxy, 0L, 0L, 0L, 0L, salt);
    }

    public UniverseId withSystem(long system) {
        return new UniverseId(universe, galaxy, system, 0L, 0L, 0L, salt);
    }

    public UniverseId withBody(long body) {
        return new UniverseId(universe, galaxy, system, body, 0L, 0L, salt);
    }

    public UniverseId withRegion(long region) {
        return new UniverseId(universe, galaxy, system, body, region, 0L, salt);
    }

    public UniverseId withChunk(long chunk) {
        return new UniverseId(universe, galaxy, system, body, region, chunk, salt);
    }

    public UniverseId withSalt(long s) {
        return new UniverseId(universe, galaxy, system, body, region, chunk, s);
    }

    public boolean isUniverseRoot() {
        return galaxy == 0L && system == 0L && body == 0L;
    }

    public boolean isGalaxyLevel() {
        return system == 0L && body == 0L && galaxy != 0L;
    }

    public boolean isSystemLevel() {
        return body == 0L && system != 0L;
    }

    public boolean isBodyLevel() {
        return body != 0L && region == 0L;
    }

    public boolean isRegionLevel() {
        return region != 0L && chunk == 0L;
    }

    public boolean isChunkLevel() {
        return chunk != 0L;
    }

    @Override
    public int compareTo(UniverseId o) {
        int c = Long.compare(universe, o.universe);
        if (c != 0) return c;
        c = Long.compare(galaxy, o.galaxy);
        if (c != 0) return c;
        c = Long.compare(system, o.system);
        if (c != 0) return c;
        c = Long.compare(body, o.body);
        if (c != 0) return c;
        c = Long.compare(region, o.region);
        if (c != 0) return c;
        c = Long.compare(chunk, o.chunk);
        if (c != 0) return c;
        return Long.compare(salt, o.salt);
    }

    @Override
    public String toString() {
        return "U[" + universe + "/" + galaxy + "/" + system + "/" + body + "/" + region + "/" + chunk + "]";
    }

    /** Round-trip serialisation: encodes to a string and parses back. */
    public String encode() {
        return universe + ":" + galaxy + ":" + system + ":" + body + ":" + region + ":" + chunk + ":" + salt;
    }

    public static UniverseId decode(String encoded) {
        Objects.requireNonNull(encoded, "encoded");
        String[] parts = encoded.split(":");
        if (parts.length != 7) throw new IllegalArgumentException("invalid id: " + encoded);
        long[] values = new long[7];
        for (int i = 0; i < 7; i++) {
            values[i] = Long.parseLong(parts[i]);
        }
        return new UniverseId(values[0], values[1], values[2], values[3], values[4], values[5], values[6]);
    }

    /** Hash combining function (64-bit FNV-1a) used by tests and caches. */
    public long stableHash() {
        long[] parts = {universe, galaxy, system, body, region, chunk, salt};
        long h = 0xcbf29ce484222325L;
        for (long p : parts) {
            for (int i = 0; i < 8; i++) {
                h ^= (p >>> (i * 8)) & 0xff;
                h *= 0x100000001b3L;
            }
        }
        return h;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UniverseId other)) return false;
        return Arrays.equals(new long[]{universe, galaxy, system, body, region, chunk, salt},
                new long[]{other.universe, other.galaxy, other.system, other.body, other.region, other.chunk, other.salt});
    }
}