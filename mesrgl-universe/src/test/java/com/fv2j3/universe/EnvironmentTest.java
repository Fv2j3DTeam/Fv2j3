package com.fv2j3.universe;

import com.fv2j3.universe.environment.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentTest {

    @Test
    void windSamplesAtDifferentLatitudes() {
        WindField w = new WindField();
        com.fv2j3.universe.generation.PlanetGenerator pg = new com.fv2j3.universe.generation.PlanetGenerator();
        com.fv2j3.universe.identifiers.UniverseId root = new com.fv2j3.universe.identifiers.UniverseId(1, 0, 0, 0, 0, 0, 0);
        var planet = pg.generate(root, 42L, 1L, 5L, 1L);
        var eq = w.sample(planet, 0.0, 0.0, 0.0, 0);
        var pole = w.sample(planet, Math.PI / 2, 0.0, 0.0, 0);
        // Both should be finite and non-zero typically.
        assertTrue(Double.isFinite(eq.magnitude));
        assertTrue(Double.isFinite(pole.magnitude));
    }

    @Test
    void temperatureFieldLapse() {
        TemperatureField tf = new TemperatureField();
        double t1 = tf.sample(300, 0, 0);
        double t2 = tf.sample(300, 1000, 0);
        assertTrue(t2 < t1);
    }

    @Test
    void pressureFieldLapse() {
        PressureField pf = new PressureField();
        double p1 = pf.sampleAtmospheric(101325, 0);
        double p2 = pf.sampleAtmospheric(101325, 1000);
        assertTrue(p2 < p1);
    }

    @Test
    void humiditySaturateIncreasesWithTemp() {
        HumidityField hf = new HumidityField();
        double h1 = hf.saturate(280);
        double h2 = hf.saturate(320);
        assertTrue(h2 > h1);
    }

    @Test
    void environmentalForcesNonZero() {
        EnvironmentalForceQuery q = new EnvironmentalForceQuery();
        double b = q.buoyancyForce(com.fv2j3.universe.fluid.FluidDefinition.water(), 0.001);
        assertTrue(b > 0);
    }
}
