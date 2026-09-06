package com.fv2j3.universe.generation;

import com.fv2j3.universe.identifiers.UniverseId;
import com.fv2j3.universe.math.XorShift64;

import java.util.Map;
import java.util.Objects;

/**
 * Generation context. Carries the deterministic inputs for a generation stage
 * (parent seed, identifier, stage name, parameters). It is immutable and
 * fully serialisable.
 */
public record GenerationContext(
        long universeRootSeed,
        UniverseId id,
        String stage,
        XorShift64 rng,
        Map<String, Object> parameters
) {
    public GenerationContext {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(rng, "rng");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    public GenerationContext child(String childStage) {
        long childSeed = com.fv2j3.universe.seeds.SeedDerivation.deriveSubSeed(rng.state(), childStage.hashCode());
        return new GenerationContext(universeRootSeed, id, childStage, new XorShift64(childSeed), parameters);
    }

    public Object param(String key) {
        return parameters.get(key);
    }
}