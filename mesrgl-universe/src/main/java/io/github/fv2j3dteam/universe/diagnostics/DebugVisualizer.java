package io.github.fv2j3dteam.universe.diagnostics;

/**
 * Debug visualisation sink (§66). Receives field samples per cell and may
 * emit them to a renderer, file, or test. Does not depend on any rendering
 * library. Implementations must not become runtime dependencies.
 */
public interface DebugVisualizer {

    /** Wind sample at (x, y, z). */
    void windSample(double x, double y, double z, double vx, double vy, double vz, double magnitude);

    /** Temperature sample. */
    void temperatureSample(double x, double y, double z, double temperatureK);

    /** Pressure sample. */
    void pressureSample(double x, double y, double z, double pressurePa);

    /** Humidity sample. */
    void humiditySample(double x, double y, double z, double humidity);

    /** Fluid density / velocity. */
    void fluidSample(double x, double y, double z, double density, double vx, double vy, double vz);

    /** Smoke density / velocity. */
    void smokeSample(double x, double y, double z, double density, double vx, double vy, double vz);

    /** Precipitation cell. */
    void precipitationSample(double x, double y, double z, double rate);

    /** Snow coverage. */
    void snowSample(double x, double y, double z, double depth);

    /** LOD tier per chunk. */
    void lodSample(long chunkKey, int tier);

    /** Streaming state. */
    void streamingSample(String label, String state);

    /** Active/sleeping cells. */
    void activitySample(String label, boolean active);

    /** Generation job. */
    void generationSample(String label, String state, long elapsedMs);

    /** Generic marker; used for tests. */
    void marker(String label, String text);
}
