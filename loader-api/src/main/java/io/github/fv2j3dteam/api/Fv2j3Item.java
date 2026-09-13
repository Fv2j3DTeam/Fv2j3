package io.github.fv2j3dteam.api;

public interface Fv2j3Item {
    String id();
    String name();
    int maxStackSize();
    default int durability() { return 0; }
}
