package com.fv2j3.api;

public interface Mod extends ModLifecycle {
    ModDescriptor descriptor();

    default String id() {
        return descriptor().id();
    }

    default String name() {
        return descriptor().name();
    }
}
