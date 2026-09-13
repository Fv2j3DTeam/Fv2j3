package io.github.fv2j3dteam.api;

public interface ModLifecycle {
    default void onLoad(ModContext context) {
    }

    default void onInitialize(ModContext context) {
    }

    default void onStart(ModContext context) {
    }

    default void onStop(ModContext context) {
    }
}
