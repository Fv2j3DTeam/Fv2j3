package com.fv2j3.universe.generation;

/**
 * A generation stage: deterministic, ordered, testable.
 */
public interface GenerationStage<I, O> {
    String name();
    O generate(I input, GenerationContext context) throws GenerationException;

    default GenerationStage<I, O> withName(String n) {
        GenerationStage<I, O> self = this;
        return new GenerationStage<>() {
            @Override public String name() { return n; }
            @Override public O generate(I input, GenerationContext context) throws GenerationException {
                return self.generate(input, context);
            }
        };
    }
}