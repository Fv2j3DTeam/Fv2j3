package com.fv2j3.universe.universe;

import com.fv2j3.universe.api.UniverseApi;
import com.fv2j3.universe.cache.CacheManager;
import com.fv2j3.universe.diagnostics.Diagnostics;
import com.fv2j3.universe.events.EventBus;
import com.fv2j3.universe.identifiers.UniverseId;
import com.fv2j3.universe.persistence.PersistenceManager;
import com.fv2j3.universe.seeds.SeedDerivation;
import com.fv2j3.universe.streaming.StreamingManager;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Logical universe root. Holds universe seed, registry of galaxies, and is the
 * access point to {@link StreamingManager}, {@link PersistenceManager}, and
 * {@link Diagnostics}.
 *
 * Galaxy / system / planet metadata is materialized on demand and cached.
 * The instance is thread-safe for queries; generation runs only via the
 * streaming manager to ensure cancellation safety.
 */
public final class Universe implements UniverseApi {

    private final UniverseId universeId;
    private final long universeSeed;
    private final int generatorVersion;
    private final ConcurrentMap<Long, Galaxy> galaxyCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, StarSystem> systemCache = new ConcurrentHashMap<>();
    private final StreamingManager streaming;
    private final PersistenceManager persistence;
    private final Diagnostics diagnostics;
    private final EventBus events;
    private final CacheManager cache;
    private volatile boolean closed;

    public Universe(UniverseId universeId,
                    long universeSeed,
                    int generatorVersion,
                    StreamingManager streaming,
                    PersistenceManager persistence,
                    Diagnostics diagnostics,
                    EventBus events,
                    CacheManager cache) {
        this.universeId = Objects.requireNonNull(universeId, "universeId");
        this.universeSeed = SeedDerivation.deriveUniverseSeed(universeSeed);
        this.generatorVersion = generatorVersion;
        this.streaming = Objects.requireNonNull(streaming, "streaming");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.events = Objects.requireNonNull(events, "events");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    @Override public UniverseId universeId() { return universeId; }
    @Override public long universeSeed() { return universeSeed; }
    @Override public int generatorVersion() { return generatorVersion; }

    public StreamingManager streaming() { return streaming; }
    public PersistenceManager persistence() { return persistence; }
    public Diagnostics diagnostics() { return diagnostics; }
    public EventBus events() { return events; }
    public CacheManager cache() { return cache; }

    public void cacheGalaxy(Galaxy g) { galaxyCache.put(g.id().galaxy(), g); }
    public void cacheSystem(StarSystem s) { systemCache.put(s.id().system(), s); }

    @Override
    public Optional<Galaxy> galaxy(long galaxy) {
        return Optional.ofNullable(galaxyCache.get(galaxy));
    }

    @Override
    public Optional<StarSystem> starSystem(long galaxy, long system) {
        return Optional.ofNullable(systemCache.get(system));
    }

    @Override
    public Optional<Planet> planet(long galaxy, long system, long body) {
        return Optional.empty(); // populated by generation pipeline
    }

    public Galaxy galaxyOrThrow(long galaxy) {
        Galaxy g = galaxyCache.get(galaxy);
        if (g == null) throw new IllegalStateException("galaxy not cached: " + galaxy);
        return g;
    }

    public StarSystem starSystemOrThrow(long system) {
        StarSystem s = systemCache.get(system);
        if (s == null) throw new IllegalStateException("system not cached: " + system);
        return s;
    }

    @Override
    public boolean isClosed() { return closed; }

    public void close() {
        closed = true;
        galaxyCache.clear();
        systemCache.clear();
        cache.clear();
        streaming.shutdown();
    }
}
