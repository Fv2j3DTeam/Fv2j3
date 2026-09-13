package io.github.fv2j3dteam.universe.streaming;

/**
 * Explicit lifecycle for a streaming-managed region.
 * Invalid transitions are rejected in {@link StreamingManager}.
 */
public enum RegionState {
    UNLOADED,
    QUEUED,
    GENERATING,
    LOADING,
    LOADED,
    ACTIVE,
    INACTIVE,
    SAVING,
    UNLOADING,
    FAILED;

    public boolean canTransitionTo(RegionState target) {
        return switch (this) {
            case UNLOADED -> target == QUEUED || target == GENERATING || target == FAILED;
            case QUEUED -> target == GENERATING || target == UNLOADING || target == FAILED;
            case GENERATING -> target == LOADING || target == UNLOADING || target == FAILED;
            case LOADING -> target == LOADED || target == FAILED || target == UNLOADING;
            case LOADED -> target == ACTIVE || target == INACTIVE || target == SAVING || target == UNLOADING || target == FAILED;
            case ACTIVE -> target == INACTIVE || target == SAVING || target == UNLOADING;
            case INACTIVE -> target == ACTIVE || target == UNLOADING || target == GENERATING;
            case SAVING -> target == LOADED || target == ACTIVE || target == UNLOADING;
            case UNLOADING -> target == UNLOADED || target == FAILED;
            case FAILED -> target == UNLOADED || target == GENERATING;
        };
    }
}