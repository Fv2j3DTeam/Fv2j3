package com.fv2j3.api;

public record RuntimeTransition(
        ModRuntimeState fromState,
        ModRuntimeState toState,
        boolean allowed,
        String reason
) {
    public RuntimeTransition {
        fromState = fromState == null ? ModRuntimeState.DISCOVERED : fromState;
        toState = toState == null ? ModRuntimeState.DISCOVERED : toState;
        reason = reason == null ? "transition not allowed" : reason;
    }

    public static RuntimeTransition evaluate(ModRuntimeState fromState, ModRuntimeState toState) {
        boolean allowed = fromState != null && fromState.canTransitionTo(toState);
        String reason = allowed ? "allowed" : "illegal transition from " + fromState + " to " + toState;
        return new RuntimeTransition(fromState, toState, allowed, reason);
    }
}
