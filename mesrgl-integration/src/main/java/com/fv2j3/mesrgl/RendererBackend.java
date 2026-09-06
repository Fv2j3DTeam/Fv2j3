package com.fv2j3.mesrgl;

/**
 * The backend that ACTUALLY rendered the last frame, as reported by the
 * native bridge. The CPU Software RT (MesrGL reference renderer) is always
 * the fallback; GPU Software RT is only reported when the GPU really
 * executed the software ray-tracing kernels.
 */
public enum RendererBackend {
    NONE(0, "None"),
    CPU_SOFTWARE_RT(1, "CPU Software RT"),
    GPU_SOFTWARE_RT(2, "GPU Software RT");

    private final int nativeKind;
    private final String displayName;

    RendererBackend(int nativeKind, String displayName) {
        this.nativeKind = nativeKind;
        this.displayName = displayName;
    }

    public static RendererBackend fromNativeKind(int kind) {
        for (RendererBackend backend : values()) {
            if (backend.nativeKind == kind) {
                return backend;
            }
        }
        return NONE;
    }

    public String displayName() {
        return displayName;
    }
}
