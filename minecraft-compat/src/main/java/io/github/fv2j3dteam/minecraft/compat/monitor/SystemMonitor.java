package io.github.fv2j3dteam.minecraft.compat.monitor;

/**
 * Platform system monitor. Implementations must be cheap enough to be called
 * from a background sampler thread and must never throw: unavailable metrics
 * are reported as empty Optionals inside {@link SystemStats}.
 */
public interface SystemMonitor {
    SystemStats snapshot();
}
