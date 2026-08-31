package com.fv2j3.api;

public interface ModInstance {
    ModContainer container();

    Mod mod();

    default String id() {
        return mod() != null ? mod().id() : container() != null ? container().id() : "";
    }

    default boolean isBound() {
        return container() != null && mod() != null;
    }
}
