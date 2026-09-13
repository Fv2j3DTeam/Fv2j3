package io.github.fv2j3dteam.universe.generation;

import io.github.fv2j3dteam.universe.identifiers.UniverseId;
import io.github.fv2j3dteam.universe.math.XorShift64;
import io.github.fv2j3dteam.universe.seeds.SeedDerivation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Multi-stage generation pipeline (§6). Stages are independent, deterministic,
 * testable, and may be replaced. The pipeline accepts a list of stages and runs
 * them in order, threading the output of one stage as the input of the next.
 *
 * Stages are pure functions of their inputs; they must not mutate world state
 * or hold references to non-deterministic globals.
 */
public final class GenerationPipeline {

    public interface Stage<I, O> {
        String name();
        O run(I input, GenerationContext context);
    }

    public static final class Builder {
        private final Map<String, Stage<?, ?>> stages = new LinkedHashMap<>();

        public <I, O> Builder add(Stage<I, O> stage) {
            Objects.requireNonNull(stage, "stage");
            if (stages.containsKey(stage.name())) {
                throw new IllegalArgumentException("duplicate stage name: " + stage.name());
            }
            stages.put(stage.name(), stage);
            return this;
        }

        public <I, O> Builder add(String name, Stage<I, O> stage) {
            stages.put(name, new NamedStage<>(name, stage));
            return this;
        }

        public List<String> stageNames() { return List.copyOf(stages.keySet()); }
        public Map<String, Stage<?, ?>> stages() { return stages; }

    public GenerationPipeline build() {
        return new GenerationPipeline(stages);
    }
    }

    public static Builder builder() { return new Builder(); }

    private final List<Stage<?, ?>> ordered;
    private final List<String> orderedNames;

    GenerationPipeline(Map<String, Stage<?, ?>> stages) {
        this.ordered = List.copyOf(stages.values());
        this.orderedNames = List.copyOf(stages.keySet());
    }

    public List<String> stageNames() { return orderedNames; }

    public Object run(Object input, UniverseId id, long universeRootSeed) {
        Object current = input;
        long stageSeed = SeedDerivation.deriveSeed(universeRootSeed, id);
        for (int i = 0; i < ordered.size(); i++) {
            Stage<Object, Object> stage = (Stage<Object, Object>) ordered.get(i);
            // Per-stage seed derived from the universe seed and stage index for determinism.
            long perStageSeed = stageSeed ^ ((long) i * 0x9E3779B97F4A7C15L);
            GenerationContext ctx = new GenerationContext(
                    universeRootSeed, id, stage.name(), new XorShift64(perStageSeed), Map.of());
            current = stage.run(current, ctx);
        }
        return current;
    }

    private static final class NamedStage<I, O> implements Stage<I, O> {
        private final String name;
        private final Stage<I, O> inner;
        NamedStage(String name, Stage<I, O> inner) { this.name = name; this.inner = inner; }
        @Override public String name() { return name; }
        @Override public O run(I input, GenerationContext context) { return inner.run(input, context); }
    }
}
