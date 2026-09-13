package io.github.fv2j3dteam.loader.core;

import io.github.fv2j3dteam.api.Mod;
import io.github.fv2j3dteam.api.ModContainer;
import io.github.fv2j3dteam.api.ModContext;
import io.github.fv2j3dteam.api.ModLifecycleAdapter;
import io.github.fv2j3dteam.api.ModRuntime;
import java.lang.reflect.Method;
import java.util.Objects;

public final class DefaultModLifecycleAdapter implements ModLifecycleAdapter {
    private final ModContainer container;
    private final ModRuntime runtime;

    public DefaultModLifecycleAdapter(ModContainer container, ModRuntime runtime) {
        this.container = Objects.requireNonNull(container, "container");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public ModContainer container() {
        return container;
    }

    @Override
    public ModRuntime runtime() {
        return runtime;
    }

    public void beforeLoad() {
    }

    @Override
    public void onLoad() {
        invokeLifecycle("onLoad");
    }

    public void beforeInitialize() {
    }

    @Override
    public void onInitialize() {
        invokeLifecycle("onInitialize");
    }

    public void beforeStart() {
    }

    @Override
    public void onStart() {
        invokeLifecycle("onStart");
    }

    public void beforeStop() {
    }

    @Override
    public void onStop() {
        invokeLifecycle("onStop");
    }

    private void invokeLifecycle(String lifecycleName) {
        Mod mod = container.mod();
        if (mod == null) {
            return;
        }
        ModContext context = container.context();
        try {
            Method method = mod.getClass().getMethod(lifecycleName, ModContext.class);
            method.invoke(mod, context);
        } catch (NoSuchMethodException ignored) {
            return;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(
                    "Mod '" + container.id() + "' failed during '" + lifecycleName + "' lifecycle callback.",
                    ex
            );
        }
    }
}
