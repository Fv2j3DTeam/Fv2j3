package io.github.fv2j3dteam.loader.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.fv2j3dteam.api.DependencyType;
import io.github.fv2j3dteam.api.Mod;
import io.github.fv2j3dteam.api.ModClassLoaderProvider;
import io.github.fv2j3dteam.api.ModContainer;
import io.github.fv2j3dteam.api.ModDependency;
import io.github.fv2j3dteam.api.ModContext;
import io.github.fv2j3dteam.api.ModDescriptor;
import io.github.fv2j3dteam.api.ModLoadingProgress;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModApiTest {
    @Test
    void createsSimpleDescriptor() {
        ModDescriptor descriptor = new ModDescriptor("demo_mod", "1.0.0", "Demo Mod");

        assertEquals("demo_mod", descriptor.id());
        assertEquals("1.0.0", descriptor.version());
        assertEquals("Demo Mod", descriptor.name());
        assertEquals(">=0.1.0", descriptor.requiredLoaderVersion());
    }

    @Test
    void rejectsInvalidIdAndVersion() {
        assertThrows(IllegalArgumentException.class, () -> new ModDescriptor("", "1.0.0", "Demo"));
        assertThrows(IllegalArgumentException.class, () -> new ModDescriptor("demo", "", "Demo"));
    }

    @Test
    void dependencyModelIsImmutableAndTyped() {
        ModDependency dependency = new ModDependency("other_mod", "^1.0.0", DependencyType.OPTIONAL);

        assertEquals("other_mod", dependency.id());
        assertEquals("^1.0.0", dependency.versionRange());
        assertEquals(DependencyType.OPTIONAL, dependency.type());
    }

    @Test
    void lifecycleInterfaceCanBeImplemented() {
        Mod mod = new TestMod();

        assertEquals("sample_mod", mod.id());
        assertEquals("Sample Mod", mod.name());
        assertNotNull(mod.descriptor());
    }

    @Test
    void modContextCarriesMetadataAndAttributes() {
        LoaderEnvironment environment = LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT");
        LoaderContext loaderContext = new LoaderContext(
                new Fv2j3Loader(environment),
                environment,
                List.of("sample_mod"),
                ClassLoader.getSystemClassLoader(),
                null,
                new StandardLoaderLogger("Fv2j3"),
                Map.of("mode", "development")
        );
        ModDescriptor descriptor = new ModDescriptor("sample_mod", "1.0.0", "Sample Mod");
        ModContext modContext = new ModContext(descriptor, loaderContext, new StandardLoaderLogger("Fv2j3"), Map.of("feature", "api"));

        assertEquals("sample_mod", modContext.descriptor().id());
        assertEquals(loaderContext, modContext.loaderContext());
        assertEquals("development", modContext.loaderContext(LoaderContext.class).configuration().get("mode"));
        assertEquals("api", modContext.attribute("feature", String.class));
        assertEquals("sample_mod", modContext.modId());
        assertEquals("Sample Mod", modContext.modName());
    }

    @Test
    void loadingProgressReportsAStableFraction() {
        ModLoadingProgress progress = new ModLoadingProgress("loading", "sample_mod", 2, 4);

        assertEquals(0.5, progress.fraction());
    }

    @Test
    void modContainerProvidesStableDescriptor() {
        ModDescriptor descriptor = new ModDescriptor("sample_mod", "1.0.0", "Sample Mod");
        ModContainer container = new TestContainer(new TestMod());

        assertEquals(descriptor.id(), container.id());
        assertFalse(container.descriptor().id().isBlank());
    }

    @Test
    void discoveryInterfaceRequiresSourceAndReturnsCandidates() {
        ModDiscovery discovery = source -> List.of(
                new ModCandidate("demo", source.sourceId(), source.sourceType(), new ModDescriptor("demo", "1.0.0", "Demo"))
        );

        List<ModCandidate> candidates = discovery.discover(new ModSource("mods-folder", "directory"));

        assertEquals(1, candidates.size());
        assertEquals("demo", candidates.getFirst().id());
    }

    @Test
    void classLoaderProviderContractRemainsSeparateFromRuntimeLoading() {
        ModClassLoaderProvider provider = container -> ClassLoader.getSystemClassLoader();
        ClassLoader loader = provider.createClassLoader(new TestContainer(new TestMod()));
        assertNotNull(loader);
    }

    private static final class TestMod implements Mod {
        @Override
        public ModDescriptor descriptor() {
            return new ModDescriptor("sample_mod", "1.0.0", "Sample Mod");
        }
    }

    private static final class TestContainer implements ModContainer {
        private final Mod mod;

        private TestContainer(Mod mod) {
            this.mod = mod;
        }

        @Override
        public Mod mod() {
            return mod;
        }

        @Override
        public ModDescriptor descriptor() {
            return mod.descriptor();
        }
    }
}
