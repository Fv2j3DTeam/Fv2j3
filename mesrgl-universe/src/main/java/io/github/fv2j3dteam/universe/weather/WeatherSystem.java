package io.github.fv2j3dteam.universe.weather;

import io.github.fv2j3dteam.universe.events.Event;
import io.github.fv2j3dteam.universe.events.EventBus;
import io.github.fv2j3dteam.universe.identifiers.UniverseId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Weather system (§25, §58). Schedules weather per planet, manages storms,
 * drives transitions. Multiple simultaneous weather events supported.
 */
public final class WeatherSystem {

    private final WeatherRegistry registry;
    private final EventBus events;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<UniverseId, String> currentWeather = new LinkedHashMap<>();
    private final Map<Long, Storm> storms = new LinkedHashMap<>();
    private final AtomicLong nextStormId = new AtomicLong();

    public WeatherSystem(WeatherRegistry registry, EventBus events) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.events = Objects.requireNonNull(events, "events");
    }

    public WeatherRegistry registry() { return registry; }

    public String currentWeatherFor(UniverseId planetId) {
        lock.readLock().lock();
        try { return currentWeather.getOrDefault(planetId, "clear"); }
        finally { lock.readLock().unlock(); }
    }

    public void setWeather(UniverseId planetId, String weatherId) {
        Objects.requireNonNull(planetId, "planetId");
        Objects.requireNonNull(weatherId, "weatherId");
        if (registry.get(weatherId) == null) throw new IllegalArgumentException("unknown weather: " + weatherId);
        lock.writeLock().lock();
        String old = currentWeather.get(planetId);
        currentWeather.put(planetId, weatherId);
        lock.writeLock().unlock();
        if (old == null || !old.equals(weatherId)) {
            events.publish(new Event.WeatherChanged(planetId, weatherId, old));
        }
    }

    public long spawnStorm(UniverseId planetId, double lat, double lon, double radiusM, double intensity) {
        long id = nextStormId.incrementAndGet();
        Storm storm = new Storm(planetId, id, lat, lon, radiusM, intensity);
        lock.writeLock().lock();
        try { storms.put(id, storm); } finally { lock.writeLock().unlock(); }
        events.publish(new Event.StormSpawned(planetId, id));
        return id;
    }

    public boolean dissipateStorm(long id) {
        Storm s;
        lock.writeLock().lock();
        try { s = storms.remove(id); } finally { lock.writeLock().unlock(); }
        if (s != null) {
            events.publish(new Event.StormDissipated(s.planetId(), id));
            return true;
        }
        return false;
    }

    public void step(double dt) {
        java.util.List<Long> toRemove = new java.util.ArrayList<>();
        lock.readLock().lock();
        for (Storm s : storms.values()) {
            s.step(dt);
            if (s.state() == Storm.State.DEAD) toRemove.add(s.id());
        }
        lock.readLock().unlock();
        for (long id : toRemove) dissipateStorm(id);
    }

    public int stormCount() {
        lock.readLock().lock();
        try { return storms.size(); } finally { lock.readLock().unlock(); }
    }

    public java.util.List<Storm> storms() {
        lock.readLock().lock();
        try { return new java.util.ArrayList<>(storms.values()); } finally { lock.readLock().unlock(); }
    }
}
