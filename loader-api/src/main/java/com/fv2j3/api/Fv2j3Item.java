package com.fv2j3.api;

public interface Fv2j3Item {
    String id();
    String name();
    int maxStackSize();
    default int durability() { return 0; }
}
