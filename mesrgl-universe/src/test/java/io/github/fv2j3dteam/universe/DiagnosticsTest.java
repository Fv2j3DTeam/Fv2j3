package io.github.fv2j3dteam.universe;

import io.github.fv2j3dteam.universe.diagnostics.CapturingDebugVisualizer;
import io.github.fv2j3dteam.universe.diagnostics.DefaultDiagnostics;
import io.github.fv2j3dteam.universe.diagnostics.Profiler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticsTest {

    @Test
    void counterAndTiming() {
        var d = new DefaultDiagnostics();
        d.increment("foo");
        d.increment("foo");
        d.record("bar", 5);
        assertEquals(2, d.counter("foo"));
        assertEquals(5, d.counter("bar"));
        try (var ignored = new Profiler(d).time("op")) {
            try { Thread.sleep(2); } catch (InterruptedException e) {}
        }
        var snap = d.snapshot();
        assertTrue(snap.timingsCount().getOrDefault("op", 0L) >= 1L);
    }

    @Test
    void debugVisualizerCaptures() {
        var v = new CapturingDebugVisualizer();
        v.windSample(0, 0, 0, 1, 0, 0, 1);
        v.temperatureSample(0, 0, 0, 300);
        v.smokeSample(0, 0, 0, 0.5, 0, 1, 0);
        assertEquals(1, v.countOf("wind"));
        assertEquals(1, v.countOf("temperature"));
        assertEquals(1, v.countOf("smoke"));
    }
}
