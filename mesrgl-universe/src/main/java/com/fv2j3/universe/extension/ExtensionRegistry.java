package com.fv2j3.universe.extension;

import java.util.List;

/**
 * Extension registry (§63). Generic interface for any externally-registered
 * type (biomes, resources, fluids, gases, weather types, storm types,
 * environmental emitters, custom generators, custom materials, custom species).
 *
 * Registration conflicts are detected (duplicate id rejected). Registrations
 * made after the relevant system is "frozen" are rejected.
 */
public interface ExtensionRegistry<T> {
    void register(T def);
    boolean unregister(String id);
    T get(String id);
    List<T> all();
}
