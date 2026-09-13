package io.github.fv2j3dteam.universe.streaming;

import io.github.fv2j3dteam.universe.cache.CacheManager;
import io.github.fv2j3dteam.universe.diagnostics.Diagnostics;
import io.github.fv2j3dteam.universe.error.Result;
import io.github.fv2j3dteam.universe.events.Event;
import io.github.fv2j3dteam.universe.events.EventBus;
import io.github.fv2j3dteam.universe.identifiers.UniverseId;
import io.github.fv2j3dteam.universe.persistence.PersistenceManager;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Streaming manager (§17, §18, §78). Manages asynchronous region generation, loading,
 * and unloading. Uses an explicit lifecycle state machine.
 *
 * Cancellation is safe from any cancellable state. A failed generation does not
 * commit partial data. Concurrent requests for the same region coalesce. Unload
 * is reference-safe: a region with active consumers is not silently removed.
 */
public final class StreamingManager {

    private final UniverseId universeId;
    private final long universeSeed;
    private final int generatorVersion;
    private final CacheManager cache;
    private final PersistenceManager persistence;
    private final EventBus events;
    private final Diagnostics diagnostics;
    private final ExecutorService executor;
    private final ConcurrentHashMap<RegionKey, Job> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RegionKey, RegionState> states = new ConcurrentHashMap<>();
    private final AtomicLong generationCounter = new AtomicLong();

    public StreamingManager(UniverseId universeId,
                            long universeSeed,
                            int generatorVersion,
                            CacheManager cache,
                            PersistenceManager persistence,
                            EventBus events,
                            Diagnostics diagnostics) {
        this(universeId, universeSeed, generatorVersion, cache, persistence, events, diagnostics, null);
    }

    public StreamingManager(UniverseId universeId,
                            long universeSeed,
                            int generatorVersion,
                            CacheManager cache,
                            PersistenceManager persistence,
                            EventBus events,
                            Diagnostics diagnostics,
                            ExecutorService executor) {
        this.universeId = Objects.requireNonNull(universeId, "universeId");
        this.universeSeed = universeSeed;
        this.generatorVersion = generatorVersion;
        this.cache = Objects.requireNonNull(cache, "cache");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.events = Objects.requireNonNull(events, "events");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.executor = executor != null ? executor : Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                r -> {
                    Thread t = new Thread(r, "mesrgl-streaming");
                    t.setDaemon(true);
                    return t;
                });
    }

    public UniverseId universeId() { return universeId; }
    public CacheManager cache() { return cache; }
    public long generationCount() { return generationCounter.get(); }
    public int inFlight() { return (int) jobs.values().stream().filter(j -> !j.future.isDone()).count(); }

    public RegionState stateOf(RegionKey key) {
        return states.getOrDefault(key, RegionState.UNLOADED);
    }

    /**
     * Request that a region be loaded. If the region is already in the cache, returns
     * immediately. Otherwise, queues a generation job and returns a future that completes
     * with the loaded {@link Region} or fails with a {@link GenerationJobException}.
     * Concurrent calls for the same region share the same job.
     */
    public CompletableFuture<Region> request(RegionKey key, RegionGenerator generator) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(generator, "generator");
        // Fast path: a job is already in flight.
        Job existing = jobs.get(key);
        if (existing != null && !existing.future.isDone()) {
            return existing.future;
        }
        // Synchronise on the key to avoid racing transition/state.
        synchronized (key) {
            RegionState current = stateOf(key);
            if (current == RegionState.LOADED || current == RegionState.ACTIVE || current == RegionState.INACTIVE) {
                Object content = cache.get(key);
                Region region = new Region(key, current, content, 0);
                return CompletableFuture.completedFuture(region);
            }
            // If a generation is in flight, wait for the job.
            if (current == RegionState.QUEUED || current == RegionState.GENERATING
                    || current == RegionState.LOADING) {
                Job j = jobs.get(key);
                if (j != null) return j.future;
                // No job yet — extremely brief race; treat as already-queued.
                // Fall through and let the next call hit this path.
            }
            // Transition to QUEUED then GENERATING
            transition(key, RegionState.UNLOADED, RegionState.QUEUED);
            transition(key, RegionState.QUEUED, RegionState.GENERATING);
            long genId = generationCounter.incrementAndGet();
            diagnostics.increment("streaming.generation.scheduled");
            CompletableFuture<Region> future = new CompletableFuture<>();
            Job job = new Job(key, future, genId);
            jobs.put(key, job);
            executor.submit(() -> {
                long start = System.nanoTime();
                try {
                    Region region = generator.generate(key, universeSeed, generatorVersion, persistence);
                    if (future.isCancelled()) {
                        transition(key, RegionState.GENERATING, RegionState.FAILED);
                        states.put(key, RegionState.UNLOADED);
                        return;
                    }
                    long bytes = generator.estimateBytes(region);
                    cache.put(key, RegionState.LOADED, region.content(), bytes);
                    states.put(key, RegionState.LOADED);
                    diagnostics.recordTiming("streaming.generation", System.nanoTime() - start);
                    diagnostics.increment("streaming.generation.completed");
                    events.publish(new Event.RegionLoaded(key));
                    events.publish(new Event.GenerationCompleted(key.id(), System.nanoTime() - start));
                    future.complete(region);
                } catch (Throwable t) {
                    transition(key, RegionState.GENERATING, RegionState.FAILED);
                    diagnostics.increment("streaming.generation.failed");
                    future.completeExceptionally(new GenerationJobException(key, t));
                } finally {
                    jobs.remove(key, job);
                }
            });
            return future;
        }
    }

    public Result<Void> cancel(RegionKey key) {
        Job job = jobs.get(key);
        if (job == null) return Result.ok(null);
        boolean cancelled = job.future.cancel(true);
        if (cancelled) diagnostics.increment("streaming.generation.cancelled");
        states.put(key, RegionState.UNLOADED);
        return Result.ok(null);
    }

    public Result<Void> unload(RegionKey key) {
        RegionState current = stateOf(key);
        if (current == RegionState.UNLOADED) return Result.ok(null);
        if (current == RegionState.GENERATING || current == RegionState.QUEUED) {
            cancel(key);
            current = stateOf(key);
        }
        // Mark UNLOADING then UNLOADED
        if (current != RegionState.UNLOADED) {
            transition(key, current, RegionState.UNLOADING);
        }
        cache.remove(key);
        states.put(key, RegionState.UNLOADED);
        events.publish(new Event.RegionUnloaded(key));
        return Result.ok(null);
    }

    public void markActive(RegionKey key) {
        RegionState current = stateOf(key);
        if (current == RegionState.LOADED || current == RegionState.INACTIVE) {
            transition(key, current, RegionState.ACTIVE);
        }
    }

    public void markInactive(RegionKey key) {
        RegionState current = stateOf(key);
        if (current == RegionState.ACTIVE) {
            transition(key, current, RegionState.INACTIVE);
        }
    }

    private void transition(RegionKey key, RegionState from, RegionState to) {
        RegionState current = states.getOrDefault(key, RegionState.UNLOADED);
        if (current != from) {
            // Allow unconditional transition if from == UNLOADED and state is already UNLOADED
            if (!(from == RegionState.UNLOADED && current == RegionState.UNLOADED)) {
                throw new IllegalStateException("invalid transition for " + key + ": " + current + " -> " + to);
            }
        }
        if (!current.canTransitionTo(to)) {
            throw new IllegalStateException("invalid transition for " + key + ": " + current + " -> " + to);
        }
        states.put(key, to);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    /** Per-region generator callback. Implementations are pure (no globals). */
    public interface RegionGenerator {
        Region generate(RegionKey key, long universeSeed, int generatorVersion, PersistenceManager persistence);
        default long estimateBytes(Region region) {
            return region == null ? 0L : 64L;
        }
    }

    public static final class GenerationJobException extends RuntimeException {
        private final RegionKey key;
        public GenerationJobException(RegionKey key, Throwable cause) {
            super("generation failed for " + key, cause);
            this.key = key;
        }
        public RegionKey key() { return key; }
    }

    private static final class Job {
        final RegionKey key;
        final CompletableFuture<Region> future;
        final long id;
        Job(RegionKey key, CompletableFuture<Region> future, long id) {
            this.key = key; this.future = future; this.id = id;
        }
    }
}
