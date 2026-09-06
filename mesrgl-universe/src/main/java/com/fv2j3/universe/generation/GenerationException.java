package com.fv2j3.universe.generation;

/**
 * Thrown when generation fails for a deterministic, recorded reason.
 * Carries the stage name and underlying cause.
 */
public class GenerationException extends RuntimeException {
    private final String stage;
    private final String id;

    public GenerationException(String stage, String id, String message, Throwable cause) {
        super("Generation failed in stage '" + stage + "' for " + id + ": " + message, cause);
        this.stage = stage;
        this.id = id;
    }

    public String stage() { return stage; }
    public String id() { return id; }
}