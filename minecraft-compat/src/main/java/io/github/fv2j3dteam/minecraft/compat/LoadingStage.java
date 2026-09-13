package io.github.fv2j3dteam.minecraft.compat;

/**
 * Real Fv2j3 loader lifecycle stages. Each stage maps to the phases the loader
 * actually reports through {@code ModLoadingProgress} and carries a fixed
 * [start, end] slice of the overall 0..1 progress range so the bar advances
 * only when the loader does real work.
 */
public enum LoadingStage {
    BOOTSTRAP("Starting...", 0.0, 0.0),
    DISCOVERING_MODS("Discovering mods...", 0.0, 0.15),
    VALIDATING_MODS("Validating mods...", 0.15, 0.22),
    RESOLVING_DEPENDENCIES("Resolving dependencies...", 0.22, 0.28),
    LOADING_MODS("Loading mods...", 0.28, 0.72),
    INITIALIZING_MODS("Initializing mods...", 0.72, 0.86),
    STARTING_MODS("Starting mods...", 0.86, 0.93),
    FINALIZING("Finalizing...", 0.93, 0.99),
    COMPLETE("Loading complete", 1.0, 1.0);

    private final String display;
    private final double start;
    private final double end;

    LoadingStage(String display, double start, double end) {
        this.display = display;
        this.start = start;
        this.end = end;
    }

    public String display() {
        return display;
    }

    /**
     * Overall fraction for the given per-mod counts. {@code completed}/{@code total}
     * come from real loader events; the result is always clamped to [0, 1] and can
     * only reach exactly 1.0 for {@link #COMPLETE}.
     */
    public double fraction(int completed, int total) {
        if (this == COMPLETE) {
            return 1.0;
        }
        if (total <= 0) {
            return start;
        }
        double base = (completed / (double) total) * (end - start);
        double value = Math.min(end, Math.max(start, start + base));
        return Math.min(value, 0.99);
    }

    public static LoadingStage fromPhase(String phase) {
        return switch (phase) {
            case "discovering" -> DISCOVERING_MODS;
            case "validating" -> VALIDATING_MODS;
            case "resolving dependencies" -> RESOLVING_DEPENDENCIES;
            case "loading" -> LOADING_MODS;
            case "initializing" -> INITIALIZING_MODS;
            case "starting" -> STARTING_MODS;
            case "running" -> FINALIZING;
            case "complete" -> COMPLETE;
            default -> BOOTSTRAP;
        };
    }
}
