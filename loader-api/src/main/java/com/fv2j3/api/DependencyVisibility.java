package com.fv2j3.api;

public record DependencyVisibility(
        String dependencyId,
        boolean visible,
        boolean parentVisible,
        boolean sharedLayerVisible,
        String reason
) {
    public DependencyVisibility {
        dependencyId = dependencyId == null || dependencyId.isBlank() ? "unknown" : dependencyId;
        reason = reason == null ? "visibility determined by dependency graph" : reason;
    }

    public static DependencyVisibility resolved(String dependencyId, boolean parentVisible, boolean sharedLayerVisible) {
        return new DependencyVisibility(dependencyId, parentVisible || sharedLayerVisible, parentVisible, sharedLayerVisible,
                "dependency graph approved visibility");
    }
}
