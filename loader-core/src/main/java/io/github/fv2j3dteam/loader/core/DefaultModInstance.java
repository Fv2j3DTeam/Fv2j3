package io.github.fv2j3dteam.loader.core;

import io.github.fv2j3dteam.api.Mod;
import io.github.fv2j3dteam.api.ModContainer;
import io.github.fv2j3dteam.api.ModInstance;
import java.util.Objects;

public final class DefaultModInstance implements ModInstance {
    private final ModContainer container;
    private final Mod mod;

    public DefaultModInstance(ModContainer container, Mod mod) {
        this.container = Objects.requireNonNull(container, "container");
        this.mod = Objects.requireNonNull(mod, "mod");
    }

    @Override
    public ModContainer container() {
        return container;
    }

    @Override
    public Mod mod() {
        return mod;
    }
}
