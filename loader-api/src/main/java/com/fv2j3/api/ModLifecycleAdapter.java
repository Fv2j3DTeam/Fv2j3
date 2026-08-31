package com.fv2j3.api;

public interface ModLifecycleAdapter {
    ModContainer container();

    ModRuntime runtime();

    default void bind(ModContainer container, ModRuntime runtime) {
        if (container == null) {
            throw new IllegalArgumentException("container must not be null");
        }
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
    }

    default void beforeLoad() {
    }

    default void onLoad() {
    }

    default void beforeInitialize() {
    }

    default void onInitialize() {
    }

    default void beforeStart() {
    }

    default void onStart() {
    }

    default void beforeStop() {
    }

    default void onStop() {
    }

    default void close() {
    }
}
