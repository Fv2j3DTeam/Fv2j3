package com.fv2j3.loader.core;

import com.fv2j3.api.Mod;
import com.fv2j3.api.ModContainer;
import com.fv2j3.api.ModInstance;
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
