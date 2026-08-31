package com.fv2j3.loader.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fv2j3.api.ClassLoaderOwnership;
import com.fv2j3.api.DependencyVisibility;
import com.fv2j3.api.Mod;
import com.fv2j3.api.ModClassLoaderProvider;
import com.fv2j3.api.ModContainer;
import com.fv2j3.api.ModDescriptor;
import com.fv2j3.api.ModInstance;
import com.fv2j3.api.ModLifecycleAdapter;
import com.fv2j3.api.ModRuntime;
import com.fv2j3.api.ModRuntimeState;
import com.fv2j3.api.PackageOwnership;
import com.fv2j3.api.RuntimeClosePolicy;
import com.fv2j3.api.RuntimeTransition;
import org.junit.jupiter.api.Test;

class RuntimeContractTest {
    @Test
    void runtimeTransitionsAreExplicitlyEvaluated() {
        RuntimeTransition allowed = RuntimeTransition.evaluate(ModRuntimeState.READY, ModRuntimeState.LOADING);
        RuntimeTransition denied = RuntimeTransition.evaluate(ModRuntimeState.STOPPED, ModRuntimeState.RUNNING);

        assertTrue(allowed.allowed());
        assertFalse(denied.allowed());
        assertEquals(ModRuntimeState.READY, allowed.fromState());
        assertEquals(ModRuntimeState.STOPPED, denied.fromState());
    }

    @Test
    void classLoaderOwnershipIsExplicitAndIdempotent() {
        ModContainer container = new FakeContainer(new FakeMod());
        ClassLoaderOwnership ownership = container.ownership();

        assertEquals("Fv2j3Loader", ownership.owner());
        assertEquals("fake_mod", ownership.containerId());
        assertTrue(ownership.closeOnStopped());
        assertTrue(ownership.closeOnFailed());
        assertTrue(ownership.idempotentClose());
    }

    @Test
    void closePoliciesDocumentTerminalStateCleanup() {
        assertEquals(RuntimeClosePolicy.BOTH, new FakeRuntime(new FakeContainer(new FakeMod())).closePolicy());
        assertTrue(RuntimeClosePolicy.BOTH.shouldCloseOn(ModRuntimeState.FAILED));
        assertTrue(RuntimeClosePolicy.BOTH.shouldCloseOn(ModRuntimeState.STOPPED));
        assertFalse(RuntimeClosePolicy.ON_STOPPED.shouldCloseOn(ModRuntimeState.FAILED));
    }

    @Test
    void dependencyVisibilityIsGovernedByGraphRatherThanAdHocLoading() {
        DependencyVisibility visibility = DependencyVisibility.resolved("dependency_mod", true, false);

        assertTrue(visibility.visible());
        assertTrue(visibility.parentVisible());
        assertFalse(visibility.sharedLayerVisible());
    }

    @Test
    void packageOwnershipRespectsParentApiBoundary() {
        PackageOwnership apiPackage = PackageOwnership.of("com.fv2j3.api", "Fv2j3 API parent", false);
        PackageOwnership modPackage = PackageOwnership.of("com.example.mod", "Mod ClassLoader", true);

        assertFalse(apiPackage.modCanOverride());
        assertTrue(modPackage.modCanOverride());
    }

    @Test
    void providerContractDefinesOwnershipWithoutLoadingRealModClasses() {
        ModClassLoaderProvider provider = container -> ClassLoader.getSystemClassLoader();
        FakeContainer container = new FakeContainer(new FakeMod());

        assertTrue(provider.supports(container));
        assertEquals(ClassLoader.getSystemClassLoader(), provider.createClassLoader(container));
    }

    private static final class FakeMod implements Mod {
        @Override
        public ModDescriptor descriptor() {
            return new ModDescriptor("fake_mod", "1.0.0", "Fake Mod");
        }
    }

    private static final class FakeContainer implements ModContainer {
        private final Mod mod;

        private FakeContainer(Mod mod) {
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
    }

    private static final class FakeRuntime implements ModRuntime {
        private final ModContainer container;
        private ModRuntimeState state = ModRuntimeState.READY;

        private FakeRuntime(ModContainer container) {
            this.container = container;
        }

        @Override
        public ModContainer container() {
            return container;
        }

        @Override
        public ModInstance instance() {
            return null;
        }

        @Override
        public ModRuntimeState state() {
            return state;
        }

        @Override
        public ClassLoader classLoader() {
            return ClassLoader.getSystemClassLoader();
        }

        @Override
        public ModLifecycleAdapter lifecycleAdapter() {
            return null;
        }

        @Override
        public void transitionTo(ModRuntimeState next) {
            if (!state.canTransitionTo(next)) {
                throw new IllegalStateException("Illegal mod runtime transition from " + state + " to " + next);
            }
            this.state = next;
        }
    }
}
