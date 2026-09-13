package io.github.fv2j3dteam.universe.diagnostics;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory debug visualizer used for tests. Captures all events.
 */
public final class CapturingDebugVisualizer implements DebugVisualizer {
    public static final class Entry {
        public final String kind;
        public final String text;
        public Entry(String k, String t) { this.kind = k; this.text = t; }
    }
    private final List<Entry> entries = new ArrayList<>();

    @Override public void windSample(double x, double y, double z, double vx, double vy, double vz, double magnitude) {
        entries.add(new Entry("wind", fmt(x, y, z) + " m=" + magnitude));
    }
    @Override public void temperatureSample(double x, double y, double z, double temperatureK) {
        entries.add(new Entry("temperature", fmt(x, y, z) + " T=" + temperatureK));
    }
    @Override public void pressureSample(double x, double y, double z, double pressurePa) {
        entries.add(new Entry("pressure", fmt(x, y, z) + " P=" + pressurePa));
    }
    @Override public void humiditySample(double x, double y, double z, double humidity) {
        entries.add(new Entry("humidity", fmt(x, y, z) + " H=" + humidity));
    }
    @Override public void fluidSample(double x, double y, double z, double density, double vx, double vy, double vz) {
        entries.add(new Entry("fluid", fmt(x, y, z) + " d=" + density + " v=(" + vx + "," + vy + "," + vz + ")"));
    }
    @Override public void smokeSample(double x, double y, double z, double density, double vx, double vy, double vz) {
        entries.add(new Entry("smoke", fmt(x, y, z) + " d=" + density + " v=(" + vx + "," + vy + "," + vz + ")"));
    }
    @Override public void precipitationSample(double x, double y, double z, double rate) {
        entries.add(new Entry("precip", fmt(x, y, z) + " rate=" + rate));
    }
    @Override public void snowSample(double x, double y, double z, double depth) {
        entries.add(new Entry("snow", fmt(x, y, z) + " d=" + depth));
    }
    @Override public void lodSample(long chunkKey, int tier) { entries.add(new Entry("lod", "chunk=" + chunkKey + " tier=" + tier)); }
    @Override public void streamingSample(String label, String state) { entries.add(new Entry("streaming", label + "=" + state)); }
    @Override public void activitySample(String label, boolean active) { entries.add(new Entry("activity", label + " active=" + active)); }
    @Override public void generationSample(String label, String state, long elapsedMs) { entries.add(new Entry("generation", label + " " + state + " " + elapsedMs + "ms")); }
    @Override public void marker(String label, String text) { entries.add(new Entry(label, text)); }

    private static String fmt(double x, double y, double z) { return "(" + x + "," + y + "," + z + ")"; }
    public List<Entry> entries() { return entries; }
    public int count() { return entries.size(); }
    public int countOf(String kind) {
        int c = 0;
        for (Entry e : entries) if (e.kind.equals(kind)) c++;
        return c;
    }
    public void clear() { entries.clear(); }
}
