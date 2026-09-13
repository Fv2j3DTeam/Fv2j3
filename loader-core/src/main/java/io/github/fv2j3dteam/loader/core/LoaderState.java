package io.github.fv2j3dteam.loader.core;

public enum LoaderState {
    CREATED,
    INITIALIZING,
    INITIALIZED,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED;

    public boolean canTransitionTo(LoaderState next) {
        if (this == FAILED) {
            return false;
        }
        if (next == FAILED) {
            return this != CREATED && this != STOPPED;
        }
        return switch (this) {
            case CREATED -> next == INITIALIZING;
            case INITIALIZING -> next == INITIALIZED;
            case INITIALIZED -> next == STARTING;
            case STARTING -> next == RUNNING;
            case RUNNING -> next == STOPPING;
            case STOPPING -> next == STOPPED;
            case STOPPED -> false;
            case FAILED -> false;
        };
    }
}
