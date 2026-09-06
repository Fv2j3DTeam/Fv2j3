package com.fv2j3.mesrgl.integration.minecraft;

import java.util.Locale;

/**
 * Camera validator (Phase 47.1 - PART 5).
 *
 * Compares the Minecraft camera (eye position, look direction, FOV) with
 * the MesrGL camera parameters at the time of the last frame. The
 * renderer overlay reports both side-by-side; if any pair disagrees by
 * more than the configured tolerance, the validator flags the issue and
 * the overlay shows a warning. This lets the operator distinguish a
 * rendering bug from a camera bug.
 */
public final class CameraValidator {

    private static final float EPSILON = 1e-3f;

    private float lastMcFovDegrees = 70.0f;
    private float lastMesrglFovDegrees = 70.0f;
    private double lastMcEyeX, lastMcEyeY, lastMcEyeZ;
    private double lastMcLookX, lastMcLookY, lastMcLookZ;
    private double lastMesrglEyeX, lastMesrglEyeY, lastMesrglEyeZ;
    private double lastMesrglLookX, lastMesrglLookY, lastMesrglLookZ;
    private String lastMode = "first-person";
    private int warnings;

    public void recordMcCamera(float fovDeg, double ex, double ey, double ez,
                               double lx, double ly, double lz, String mode) {
        lastMcFovDegrees = fovDeg;
        lastMcEyeX = ex; lastMcEyeY = ey; lastMcEyeZ = ez;
        lastMcLookX = lx; lastMcLookY = ly; lastMcLookZ = lz;
        lastMode = mode;
    }

    public void recordMesrglCamera(float fovDeg, double ex, double ey, double ez,
                                   double lx, double ly, double lz) {
        lastMesrglFovDegrees = fovDeg;
        lastMesrglEyeX = ex; lastMesrglEyeY = ey; lastMesrglEyeZ = ez;
        lastMesrglLookX = lx; lastMesrglLookY = ly; lastMesrglLookZ = lz;
        warnings = 0;
        if (Math.abs(fovDeg - lastMcFovDegrees) > 0.5f) warnings++;
        if (distSq(ex, ey, ez, lastMcEyeX, lastMcEyeY, lastMcEyeZ) > 0.01) warnings++;
        if (distSq(lx, ly, lz, lastMcLookX, lastMcLookY, lastMcLookZ) > 0.01) warnings++;
    }

    public int warnings() { return warnings; }
    public float fovDegrees() { return lastMesrglFovDegrees; }
    public String mode() { return lastMode; }
    public boolean hasWarning() { return warnings > 0; }

    public String overlayText() {
        return String.format(Locale.ROOT,
                "cam{%s fov=%.1f eye=(%.1f,%.1f,%.1f) look=(%.2f,%.2f,%.2f) ok=%s}",
                lastMode, lastMesrglFovDegrees,
                lastMesrglEyeX, lastMesrglEyeY, lastMesrglEyeZ,
                lastMesrglLookX, lastMesrglLookY, lastMesrglLookZ,
                warnings == 0 ? "yes" : "WARN(" + warnings + ")");
    }

    private static double distSq(double x1, double y1, double z1,
                                  double x2, double y2, double z2) {
        double dx = x1 - x2, dy = y1 - y2, dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }
}
