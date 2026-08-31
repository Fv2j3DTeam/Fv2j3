package com.fv2j3.loader.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fv2j3.api.Mod;
import com.fv2j3.api.ModClassLoaderProvider;
import com.fv2j3.api.ModContainer;
import com.fv2j3.api.ModContainerState;
import com.fv2j3.api.ModContext;
import com.fv2j3.api.ModDescriptor;
import com.fv2j3.api.ModInstance;
import com.fv2j3.api.ModLifecycleAdapter;
import com.fv2j3.api.ModRuntime;
import com.fv2j3.api.ModRuntimeState;
import com.fv2j3.minecraft.compat.MinecraftCompatibility;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModRuntimeContractTest {
    @Test
    void modContainerStateSupportsFutureRuntimePhases() {
        ModContainerState state = ModContainerState.DISCOVERED;

        assertTrue(state.canTransitionTo(ModContainerState.VALIDATED));
        assertTrue(ModContainerState.VALIDATED.canTransitionTo(ModContainerState.READY));
        assertTrue(ModContainerState.READY.canTransitionTo(ModContainerState.LOADING));
        assertTrue(ModContainerState.LOADING.canTransitionTo(ModContainerState.LOADED));
        assertTrue(ModContainerState.LOADED.canTransitionTo(ModContainerState.INITIALIZING));
        assertTrue(ModContainerState.INITIALIZED.canTransitionTo(ModContainerState.STARTING));
        assertTrue(ModContainerState.STARTING.canTransitionTo(ModContainerState.RUNNING));
        assertTrue(ModContainerState.RUNNING.canTransitionTo(ModContainerState.STOPPING));
        assertTrue(ModContainerState.STOPPING.canTransitionTo(ModContainerState.STOPPED));
    }

    @Test
    void illegalStateTransitionsAreRejected() {
        assertFalse(ModContainerState.READY.canTransitionTo(ModContainerState.READY));
        assertFalse(ModContainerState.STOPPED.canTransitionTo(ModContainerState.RUNNING));
    }

    @Test
    void classLoaderProviderCanDefineOwnershipAndShutdownContract() {
        ModContainer container = new TestContainer(new TestMod());
        TrackingProvider provider = new TrackingProvider();

        ClassLoader loader = provider.createClassLoader(container);
        provider.closeClassLoader(container, loader);

        assertNotNull(loader);
        assertTrue(provider.closed);
        assertTrue(provider.supports(container));
    }

    @Test
    void runtimeLifecycleCanBindWithoutLoadingRealModClasses() {
        ModContainer container = new TestContainer(new TestMod());
        FakeRuntime runtime = new FakeRuntime(container);

        runtime.transitionTo(ModRuntimeState.LOADING);
        runtime.transitionTo(ModRuntimeState.LOADED);
        runtime.transitionTo(ModRuntimeState.INITIALIZING);
        runtime.transitionTo(ModRuntimeState.INITIALIZED);
        runtime.transitionTo(ModRuntimeState.STARTING);
        runtime.transitionTo(ModRuntimeState.RUNNING);

        assertEquals(ModRuntimeState.RUNNING, runtime.state());
        assertNotNull(runtime.instance());
        assertNotNull(runtime.classLoader());
    }

    @Test
    void illegalRuntimeTransitionsAreRejected() {
        FakeRuntime runtime = new FakeRuntime(new TestContainer(new TestMod()));

        assertThrows(IllegalStateException.class, () -> runtime.transitionTo(ModRuntimeState.STOPPED));
    }

    @Test
    void lifecycleAdapterHasExplicitBindingContract() {
        ModContainer container = new TestContainer(new TestMod());
        FakeRuntime runtime = new FakeRuntime(container);
        FakeLifecycleAdapter adapter = new FakeLifecycleAdapter(container, runtime);

        adapter.bind(container, runtime);
        adapter.onInitialize();
        adapter.onStart();

        assertEquals(container, adapter.container());
        assertEquals(runtime, adapter.runtime());
        assertTrue(adapter.initialized);
        assertTrue(adapter.started);
    }

    @Test
    void minecraftCompatibilityDefinesThePackageBoundary() {
        assertTrue(MinecraftCompatibility.isMinecraftPackage("net.minecraft.client.Minecraft"));
        assertTrue(MinecraftCompatibility.isFv2j3ApiPackage("com.fv2j3.api.Mod"));
        assertFalse(MinecraftCompatibility.isFv2j3ApiPackage("net.minecraft.client.Minecraft"));
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
        public ModDescriptor descriptor() {
            return mod.descriptor();
        }

        @Override
        public Mod mod() {
            return mod;
        }

        @Override
        public ModContext context() {
            return new ModContext(mod.descriptor(), Map.of("runtime", "contract"), null, Map.of("mode", "test"));
        }
    }

    private static final class TrackingProvider implements ModClassLoaderProvider {
        private boolean closed;

        @Override
        public ClassLoader createClassLoader(ModContainer container) {
            return ClassLoader.getSystemClassLoader();
        }

        @Override
        public boolean supports(ModContainer container) {
            return container != null && container.descriptor() != null;
        }

        @Override
        public void closeClassLoader(ModContainer container, ClassLoader classLoader) {
            closed = true;
        }
    }

    private static final class FakeRuntime implements ModRuntime {
        private final ModContainer container;
        private final ModInstance instance;
        private final ClassLoader classLoader;
        private ModRuntimeState state = ModRuntimeState.READY;

        private FakeRuntime(ModContainer container) {
            this.container = container;
            this.instance = new FakeInstance(container);
            this.classLoader = ClassLoader.getSystemClassLoader();
        }

        @Override
        public ModContainer container() {
            return container;
        }

        @Override
        public ModInstance instance() {
            return instance;
        }

        @Override
        public ModRuntimeState state() {
            return state;
        }

        @Override
        public ClassLoader classLoader() {
            return classLoader;
        }

        @Override
        public ModLifecycleAdapter lifecycleAdapter() {
            return new FakeLifecycleAdapter(container, this);
        }

        @Override
        public void transitionTo(ModRuntimeState next) {
            if (!state.canTransitionTo(next)) {
                throw new IllegalStateException("Illegal mod runtime transition from " + state + " to " + next);
            }
            this.state = next;
        }
    }

    private static final class FakeLifecycleAdapter implements ModLifecycleAdapter {
        private final ModContainer container;
        private final ModRuntime runtime;
        private boolean initialized;
        private boolean started;

        private FakeLifecycleAdapter(ModContainer container, ModRuntime runtime) {
            this.container = container;
            this.runtime = runtime;
        }

        @Override
        public ModContainer container() {
            return container;
        }

        @Override
        public ModRuntime runtime() {
            return runtime;
        }

        @Override
        public void onInitialize() {
            initialized = true;
        }

        @Override
        public void onStart() {
            started = true;
        }
    }

    private static final class FakeInstance implements ModInstance {
        private final ModContainer container;

        private FakeInstance(ModContainer container) {
            this.container = container;
        }

        @Override
        public ModContainer container() {
            return container;
        }

        @Override
        public Mod mod() {
            return container.mod();
        }
    }
}
