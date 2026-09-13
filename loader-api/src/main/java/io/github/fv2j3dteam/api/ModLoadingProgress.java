package io.github.fv2j3dteam.api;

public record ModLoadingProgress(String phase, String modId, int completed, int total) {
    public ModLoadingProgress {
        if (phase == null || phase.isBlank()) {
            throw new IllegalArgumentException("Loading phase must not be blank.");
        }
        if (completed < 0 || total < 0 || completed > total) {
            throw new IllegalArgumentException("Invalid loading progress: " + completed + "/" + total);
        }
    }

    public double fraction() {
        return total == 0 ? 1.0 : (double) completed / total;
    }
}
