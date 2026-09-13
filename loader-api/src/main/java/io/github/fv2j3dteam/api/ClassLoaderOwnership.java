package io.github.fv2j3dteam.api;

public record ClassLoaderOwnership(
        String owner,
        String containerId,
        String runtimeId,
        boolean closeOnStopped,
        boolean closeOnFailed,
        boolean idempotentClose
) {
    public ClassLoaderOwnership {
        owner = owner == null ? "Fv2j3Loader" : owner;
        containerId = containerId == null ? "unknown" : containerId;
        runtimeId = runtimeId == null ? "unknown" : runtimeId;
    }

    public static ClassLoaderOwnership forContainer(ModContainer container) {
        if (container == null) {
            return new ClassLoaderOwnership("Fv2j3Loader", "unknown", "unknown", true, true, true);
        }
        String runtimeId = container.runtime() == null ? "unbound" : container.runtime().getClass().getSimpleName();
        return new ClassLoaderOwnership("Fv2j3Loader", container.id(), runtimeId, true, true, true);
    }
}
