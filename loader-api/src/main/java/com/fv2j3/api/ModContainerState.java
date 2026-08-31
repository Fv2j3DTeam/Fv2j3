package com.fv2j3.api;

public enum ModContainerState {
    DISCOVERED,
    VALIDATED,
    READY,
    LOADING,
    LOADED,
    INITIALIZING,
    INITIALIZED,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED;

    public boolean canTransitionTo(ModContainerState next) {
        if (this == FAILED || next == null) {
            return false;
        }

        if (next == FAILED) {
            return this != FAILED && this != STOPPED;
        }

        return switch (this) {
            case DISCOVERED -> next == VALIDATED;
            case VALIDATED -> next == READY;
            case READY -> next == LOADING;
            case LOADING -> next == LOADED;
            case LOADED -> next == INITIALIZING;
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
