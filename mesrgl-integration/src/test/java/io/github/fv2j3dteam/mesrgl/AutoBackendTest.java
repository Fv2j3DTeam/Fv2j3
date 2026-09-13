package io.github.fv2j3dteam.mesrgl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AutoBackendTest {

    @Test
    void autoBackendUsesGPUWhenAvailable() {
        // Don't override - let the system property take effect
        MesrGLRenderer r = new MesrGLRenderer(320, 240, 1, 2, true, 0xC0FFEEL);
        try {
            RendererBackend backend = r.currentBackend();
            System.out.println("Auto backend (with -Dfv2j3.mesrgl.backend=gpu): " + backend.displayName() + " | Device: " + r.deviceName());
            
            // With -Dfv2j3.mesrgl.backend=gpu, it should prefer GPU
            assertEquals(RendererBackend.GPU_SOFTWARE_RT, backend,
                    "Should use GPU Software RT when prefer GPU is set");
        } finally {
            r.close();
        }
    }
}
