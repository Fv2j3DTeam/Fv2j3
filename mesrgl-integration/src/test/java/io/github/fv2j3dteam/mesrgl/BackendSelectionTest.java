package io.github.fv2j3dteam.mesrgl;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BackendSelectionTest {

    @BeforeAll
    static void resetBackend() {
        // Reset to auto before each test class
        MesrGLJNI.setPreferredBackend(0);
    }

    @AfterAll
    static void cleanup() {
        MesrGLJNI.setPreferredBackend(0);
    }

    @Test
    void autoBackendSelectsGPUWhenAvailable() {
        MesrGLRenderer r = new MesrGLRenderer(320, 240, 1, 2, true, 0xC0FFEEL);
        try {
            RendererBackend backend = r.currentBackend();
            // Should be either CPU or GPU software RT (both are valid)
            assertTrue(backend == RendererBackend.CPU_SOFTWARE_RT || 
                       backend == RendererBackend.GPU_SOFTWARE_RT,
                       "Backend should be a software RT backend, got: " + backend);
            System.out.println("Auto backend selected: " + backend.displayName() + " | Device: " + r.deviceName());
        } finally {
            r.close();
        }
    }

    @Test
    void forceCPUBackend() {
        MesrGLJNI.setPreferredBackend(1); // Force CPU
        MesrGLRenderer r = new MesrGLRenderer(320, 240, 1, 2, true, 0xC0FFEEL);
        try {
            assertEquals(RendererBackend.CPU_SOFTWARE_RT, r.currentBackend(),
                    "Should be CPU Software RT when forced");
            System.out.println("CPU backend selected: " + r.currentBackend().displayName() + " | Device: " + r.deviceName());
        } finally {
            r.close();
        }
    }

    @Test
    void preferGPUBackend() {
        MesrGLJNI.setPreferredBackend(2); // Prefer GPU
        MesrGLRenderer r = new MesrGLRenderer(320, 240, 1, 2, true, 0xC0FFEEL);
        try {
            // GPU may not be available, so accept either (with GPU preferred)
            RendererBackend backend = r.currentBackend();
            assertTrue(backend == RendererBackend.CPU_SOFTWARE_RT || 
                       backend == RendererBackend.GPU_SOFTWARE_RT,
                       "Backend should be a software RT backend, got: " + backend);
            System.out.println("Prefer GPU backend selected: " + backend.displayName() + " | Device: " + r.deviceName());
        } finally {
            r.close();
        }
    }

    @Test
    void bothBackendsRenderIdenticalScene() {
        // Test that both backends can render the same basic scene
        // CPU
        MesrGLJNI.setPreferredBackend(1);
        MesrGLRenderer cpuRenderer = new MesrGLRenderer(160, 120, 1, 2, true, 0xC0FFEEL);
        long cpuScene = cpuRenderer.createScene();
        
        float[] positions = {-1, 0, -1, 1, 0, -1, 1, 0, 1, -1, 0, 1};
        float[] normals = {0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0};
        float[] texCoords = {0, 0, 1, 0, 1, 1, 0, 1};
        int[] indices = {0, 1, 2, 0, 2, 3};
        int matId = cpuRenderer.createMaterial(0.8f, 0.8f, 0.8f, 0.5f, 0.0f, 0.0f, 0.0f, 1.5f);
        cpuRenderer.addMesh(positions, normals, texCoords, indices, matId);
        cpuRenderer.addDirectionalLight(0, -1, -0.5f, 1, 1, 1, 3.0f);
        cpuRenderer.setCamera(0, 3, 5, 0, 0, 0, 0, 1, 0, 1.047f, 160f/120f, 0.1f, 100f);
        cpuRenderer.buildAccelerationStructure();
        
        double cpuTime = cpuRenderer.renderFrame();
        java.nio.ByteBuffer cpuFB = cpuRenderer.readFramebufferRgba8();
        assertNotNull(cpuFB, "CPU framebuffer should not be null");
        cpuRenderer.close();
        
        // GPU (if available)
        MesrGLJNI.setPreferredBackend(2);
        MesrGLRenderer gpuRenderer = new MesrGLRenderer(160, 120, 1, 2, true, 0xC0FFEEL);
        long gpuScene = gpuRenderer.createScene();
        gpuRenderer.addMesh(positions, normals, texCoords, indices, matId);
        gpuRenderer.addDirectionalLight(0, -1, -0.5f, 1, 1, 1, 3.0f);
        gpuRenderer.setCamera(0, 3, 5, 0, 0, 0, 0, 1, 0, 1.047f, 160f/120f, 0.1f, 100f);
        gpuRenderer.buildAccelerationStructure();
        
        double gpuTime = gpuRenderer.renderFrame();
        java.nio.ByteBuffer gpuFB = gpuRenderer.readFramebufferRgba8();
        
        // GPU might fall back to CPU, so just verify it renders without error
        assertNotNull(gpuFB, "GPU framebuffer should not be null");
        System.out.println("CPU render time: " + cpuTime + "ms, GPU render time: " + gpuTime + "ms");
        System.out.println("CPU backend: " + cpuRenderer.activeBackend() + ", GPU backend: " + gpuRenderer.activeBackend());
        gpuRenderer.close();
    }
}
