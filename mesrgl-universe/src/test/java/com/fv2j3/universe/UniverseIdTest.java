package com.fv2j3.universe;

import com.fv2j3.universe.identifiers.ChunkCoord;
import com.fv2j3.universe.identifiers.UniverseId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UniverseIdTest {

    @Test
    void idEqualityAndHash() {
        UniverseId a = new UniverseId(1, 2, 3, 4, 5, 6, 7);
        UniverseId b = new UniverseId(1, 2, 3, 4, 5, 6, 7);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void encodeDecodeRoundTrip() {
        UniverseId a = new UniverseId(Long.MAX_VALUE, 1, 2, 3, 4, 5, 6);
        assertEquals(a, UniverseId.decode(a.encode()));
    }

    @Test
    void stableHashDeterministic() {
        UniverseId a = new UniverseId(1, 2, 3, 4, 5, 6, 7);
        long h1 = a.stableHash();
        long h2 = a.stableHash();
        assertEquals(h1, h2);
    }

    @Test
    void withHelpersPreserveId() {
        UniverseId root = new UniverseId(1, 0, 0, 0, 0, 0, 0);
        UniverseId galaxy = root.withGalaxy(5L);
        assertEquals(1L, galaxy.universe());
        assertEquals(5L, galaxy.galaxy());
        assertEquals(0L, galaxy.system());
    }

    @Test
    void unassignedRejected() {
        assertThrows(IllegalArgumentException.class, () -> new UniverseId(Long.MIN_VALUE, 0, 0, 0, 0, 0, 0));
    }

    @Test
    void chunkCoordCompareTo() {
        ChunkCoord a = new ChunkCoord(0, 0, 0);
        ChunkCoord b = new ChunkCoord(1, 0, 0);
        assertTrue(a.compareTo(b) < 0);
    }

    @Test
    void chunkCoordDistance() {
        ChunkCoord a = new ChunkCoord(0, 0, 0);
        ChunkCoord b = new ChunkCoord(3, 4, 0);
        assertEquals(7, a.manhattanDistance(b));
        assertEquals(4, a.chebyshevDistance(b));
    }
}
