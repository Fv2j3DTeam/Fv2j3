package com.fv2j3.universe.api;

import com.fv2j3.universe.identifiers.UniverseId;
import com.fv2j3.universe.universe.Galaxy;
import com.fv2j3.universe.universe.Planet;
import com.fv2j3.universe.universe.StarSystem;

import java.util.Optional;

/**
 * Top-level universe API. Stable, minimal surface.
 *
 * All methods are thread-safe and non-blocking with respect to heavy generation:
 * metadata queries may return cached data or {@link Optional#empty()} until the
 * streaming pipeline completes generation.
 */
public interface UniverseApi {
    UniverseId universeId();
    long universeSeed();
    int generatorVersion();

    Optional<Galaxy> galaxy(long galaxy);
    Optional<StarSystem> starSystem(long galaxy, long system);
    Optional<Planet> planet(long galaxy, long system, long body);

    boolean isClosed();
}