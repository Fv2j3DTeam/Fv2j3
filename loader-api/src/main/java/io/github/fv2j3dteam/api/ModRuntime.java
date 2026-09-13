package io.github.fv2j3dteam.api;

public interface ModRuntime {
    ModContainer container();

    ModInstance instance();

    ModRuntimeState state();

    ClassLoader classLoader();

    ModLifecycleAdapter lifecycleAdapter();

    default RuntimeClosePolicy closePolicy() {
        return RuntimeClosePolicy.BOTH;
    }

    default RuntimeTransition transition(ModRuntimeState next) {
        if (next == null) {
            throw new IllegalArgumentException("next state must not be null");
        }
        return RuntimeTransition.evaluate(state(), next);
    }

    default void transitionTo(ModRuntimeState next) {
        RuntimeTransition transition = transition(next);
        if (!transition.allowed()) {
            throw new IllegalStateException(transition.reason());
        }
    }

    default boolean isActive() {
        return state() == ModRuntimeState.RUNNING || state() == ModRuntimeState.INITIALIZED || state() == ModRuntimeState.LOADED;
    }

    default boolean isTerminal() {
        return state() == ModRuntimeState.FAILED || state() == ModRuntimeState.STOPPED;
    }
}
