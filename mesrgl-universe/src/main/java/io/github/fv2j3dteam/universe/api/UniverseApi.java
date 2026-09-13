package io.github.fv2j3dteam.universe.api;

import io.github.fv2j3dteam.universe.identifiers.UniverseId;
import io.github.fv2j3dteam.universe.universe.Galaxy;
import io.github.fv2j3dteam.universe.universe.Planet;
import io.github.fv2j3dteam.universe.universe.StarSystem;

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