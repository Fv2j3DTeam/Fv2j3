package io.github.fv2j3dteam.api;

public enum RuntimeClosePolicy {
    NEVER,
    ON_STOPPED,
    ON_FAILED,
    BOTH;

    public boolean shouldCloseOn(ModRuntimeState state) {
        if (state == null) {
            return false;
        }
        return switch (this) {
            case NEVER -> false;
            case ON_STOPPED -> state == ModRuntimeState.STOPPED;
            case ON_FAILED -> state == ModRuntimeState.FAILED;
            case BOTH -> state == ModRuntimeState.STOPPED || state == ModRuntimeState.FAILED;
        };
    }
}
