package io.github.fv2j3dteam.universe;

import io.github.fv2j3dteam.universe.biome.BiomeDefinition;
import io.github.fv2j3dteam.universe.biome.BiomeRegistry;
import io.github.fv2j3dteam.universe.fluid.FluidDefinition;
import io.github.fv2j3dteam.universe.fluid.FluidRegistry;
import io.github.fv2j3dteam.universe.materials.MaterialDefinition;
import io.github.fv2j3dteam.universe.materials.MaterialRegistry;
import io.github.fv2j3dteam.universe.resources.ResourceDefinition;
import io.github.fv2j3dteam.universe.resources.ResourceRegistry;
import io.github.fv2j3dteam.universe.smoke.GasDefinition;
import io.github.fv2j3dteam.universe.smoke.GasRegistry;
import io.github.fv2j3dteam.universe.weather.WeatherType;
import io.github.fv2j3dteam.universe.weather.WeatherRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionRegistryTest {

    @Test
    void customBiomeRegistration() {
        BiomeRegistry r = new BiomeRegistry();
        BiomeDefinition custom = new BiomeDefinition("my_biome", 200, 300, 0.1, 0.8, 0, 1000, false, 0.5, 0.2, 0.3, 0.4);
        r.register(custom);
        assertEquals(custom, r.get("my_biome"));
    }

    @Test
    void duplicateRegistrationRejected() {
        BiomeRegistry r = new BiomeRegistry();
        BiomeDefinition d = new BiomeDefinition("dup_biome", 200, 300, 0.1, 0.8, 0, 1000, false, 0.5, 0.2, 0.3, 0.4);
        r.register(d);
        assertThrows(IllegalStateException.class, () -> r.register(d));
    }

    @Test
    void customFluidRegistration() {
        FluidRegistry r = new FluidRegistry();
        FluidDefinition custom = new FluidDefinition("acid", 1100, 0.005, 5e-6, 3000, 250, 400, 0.1, 1, false);
        r.register(custom);
        assertEquals(custom, r.get("acid"));
    }

    @Test
    void customGasRegistration() {
        GasRegistry r = new GasRegistry();
        GasDefinition custom = new GasDefinition("chlorine", 3.2, 1.5e-5, 500, 0.01, 0.3, 0.4, 1.0e-5);
        r.register(custom);
        assertEquals(custom, r.get("chlorine"));
    }

    @Test
    void customMaterialRegistration() {
        MaterialRegistry r = new MaterialRegistry();
        MaterialDefinition custom = new MaterialDefinition("titanium", 4500, 0.5, 0.0, 20, 520, 1941, 3560, Double.NaN, 0.7, 0.0, 0.0, false);
        r.register(custom);
        assertEquals(custom, r.get("titanium"));
    }

    @Test
    void customResourceRegistration() {
        ResourceRegistry r = new ResourceRegistry();
        ResourceDefinition custom = new ResourceDefinition("unobtanium", 5000, 0.99, 0, 1000, 0, 100, false, "", false, 0);
        r.register(custom);
        assertEquals(custom, r.get("unobtanium"));
    }

    @Test
    void customWeatherRegistration() {
        WeatherRegistry r = new WeatherRegistry();
        WeatherType custom = new WeatherType("blood_rain", WeatherType.Phase.RAIN, 0.7, 285);
        r.register(custom);
        assertEquals(custom, r.get("blood_rain"));
    }
}
