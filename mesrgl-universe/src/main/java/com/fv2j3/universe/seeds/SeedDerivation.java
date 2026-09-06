package com.fv2j3.universe.seeds;

import com.fv2j3.universe.math.XorShift64;
import com.fv2j3.universe.identifiers.UniverseId;

/**
 * Hierarchical seed derivation (deterministic; §5). Uses xxHash-style
 * mixing over the identifier fields and the current generator version.
 *
 * The same {@code (universeSeed, id, generatorVersion)} always produces the
 * same derived seed, independent of generation order, render order, or cache state.
 */
public final class SeedDerivation {

    /** Current generator version; bumping changes all derived seeds (intentional). */
    public static final int GENERATOR_VERSION = 1;

    /** Mix one long value into an accumulator (splitmix64-style mixing). */
    private static long mix(long h, long v) {
        h ^= v;
        h *= 0x9E3779B97F4A7C15L;
        h ^= h >>> 32;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 32;
        return h;
    }

    /** Derive a 64-bit seed for a universe id at the universe root level. */
    public static long deriveUniverseSeed(long universeRootSeed) {
        long h = 0x9E3779B97F4A7C15L;
        h = mix(h, universeRootSeed);
        h = mix(h, GENERATOR_VERSION);
        return h;
    }

    /** Derive the seed for any hierarchical {@link UniverseId}. */
    public static long deriveSeed(long universeRootSeed, UniverseId id) {
        long h = deriveUniverseSeed(universeRootSeed);
        h = mix(h, id.universe());
        h = mix(h, id.galaxy());
        h = mix(h, id.system());
        h = mix(h, id.body());
        h = mix(h, id.region());
        h = mix(h, id.chunk());
        h = mix(h, id.salt());
        return h;
    }

    /** Derive a child seed by mixing an additional discriminator (e.g. a sub-stage name hash). */
    public static long deriveSubSeed(long parentSeed, long discriminator) {
        long h = mix(parentSeed, discriminator);
        return h;
    }

    /** Convenience: convert a seed into a ready-to-use PRNG. */
    public static XorShift64 prng(long universeRootSeed, UniverseId id) {
        return new XorShift64(deriveSeed(universeRootSeed, id));
    }

    private SeedDerivation() {}
}