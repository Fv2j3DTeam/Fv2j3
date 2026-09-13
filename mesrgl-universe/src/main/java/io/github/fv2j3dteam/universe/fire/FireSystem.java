package io.github.fv2j3dteam.universe.fire;

import io.github.fv2j3dteam.universe.events.Event;
import io.github.fv2j3dteam.universe.events.EventBus;
import io.github.fv2j3dteam.universe.identifiers.ChunkCoord;
import io.github.fv2j3dteam.universe.smoke.SmokeSystem;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Fire system (§32). Tracks fire sources; each source produces heat, smoke,
 * light (renderer-consumable), and consumes fuel. Sources may be extinguished.
 */
public final class FireSystem {

    public static final class Source {
        public long id;
        public ChunkCoord chunk;
        public io.github.fv2j3dteam.universe.identifiers.UniverseId planetId;
        public int cellX, cellY, cellZ;
        public double temperatureK;
        public double fuelRemaining;
        public double consumptionPerSec;
        public double smokeEmissionPerSec;
        public double radius;
        public String smokeGasId;
        public long smokeSourceId;
    }

    private final EventBus events;
    private final SmokeSystem smoke;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<Long, Source> sources = new LinkedHashMap<>();
    private final AtomicLong nextId = new AtomicLong();

    public FireSystem(EventBus events, SmokeSystem smoke) {
        this.events = Objects.requireNonNull(events, "events");
        this.smoke = Objects.requireNonNull(smoke, "smoke");
    }

    public long ignite(io.github.fv2j3dteam.universe.identifiers.UniverseId planetId,
                      ChunkCoord chunk, int x, int y, int z,
                      double temperatureK,
                      double fuel,
                      double consumptionPerSec,
                      double smokeEmissionPerSec,
                      double radius,
                      String smokeGasId) {
        Objects.requireNonNull(planetId, "planetId");
        Objects.requireNonNull(chunk, "chunk");
        long id = nextId.incrementAndGet();
        Source s = new Source();
        s.id = id; s.chunk = chunk; s.planetId = planetId; s.cellX = x; s.cellY = y; s.cellZ = z;
        s.temperatureK = temperatureK;
        s.fuelRemaining = fuel;
        s.consumptionPerSec = consumptionPerSec;
        s.smokeEmissionPerSec = smokeEmissionPerSec;
        s.radius = radius;
        s.smokeGasId = smokeGasId;
        s.smokeSourceId = smoke.addSource(new SmokeSystem.Source(0L, chunk, x, y, z,
                smokeEmissionPerSec, temperatureK, 0, 1.0, 0, smokeGasId, fuel / Math.max(consumptionPerSec, 1e-9)));
        lock.writeLock().lock();
        try { sources.put(id, s); } finally { lock.writeLock().unlock(); }
        events.publish(new Event.FireStarted(planetId, id, temperatureK));
        return id;
    }

    public boolean extinguish(long id) {
        Source s;
        lock.writeLock().lock();
        try { s = sources.remove(id); } finally { lock.writeLock().unlock(); }
        if (s != null) {
            smoke.removeSource(s.smokeSourceId);
            events.publish(new Event.FireExtinguished(s.planetId, id));
            return true;
        }
        return false;
    }

    public void step(double dt) {
        if (dt <= 0) return;
        java.util.List<Long> toRemove = new java.util.ArrayList<>();
        lock.readLock().lock();
        for (Source s : sources.values()) {
            double consumed = s.consumptionPerSec * dt;
            s.fuelRemaining -= consumed;
            if (s.fuelRemaining <= 0) {
                toRemove.add(s.id);
                continue;
            }
            // Cool down over time
            s.temperatureK = Math.max(300.0, s.temperatureK - 1.0 * dt);
        }
        lock.readLock().unlock();
        for (long id : toRemove) extinguish(id);
    }

    public int activeSourceCount() {
        lock.readLock().lock();
        try { return sources.size(); } finally { lock.readLock().unlock(); }
    }

    public double totalHeatOutput() {
        lock.readLock().lock();
        try {
            double sum = 0;
            for (Source s : sources.values()) sum += s.temperatureK;
            return sum;
        } finally { lock.readLock().unlock(); }
    }
}
