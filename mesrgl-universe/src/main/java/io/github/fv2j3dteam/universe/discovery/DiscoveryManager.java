package io.github.fv2j3dteam.universe.discovery;

import io.github.fv2j3dteam.universe.events.Event;
import io.github.fv2j3dteam.universe.events.EventBus;
import io.github.fv2j3dteam.universe.identifiers.UniverseId;
import io.github.fv2j3dteam.universe.persistence.PersistenceManager;
import io.github.fv2j3dteam.universe.persistence.PersistentEdit;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Discovery manager (§85, §79). Tracks what the player has discovered.
 * Persistent: stored as a {@link PersistentEdit} on the planet/system id.
 */
public final class DiscoveryManager {

    private final Set<UniverseId> discovered = new LinkedHashSet<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final PersistenceManager persistence;
    private final EventBus events;

    public DiscoveryManager(PersistenceManager persistence, EventBus events) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.events = Objects.requireNonNull(events, "events");
    }

    public void discover(UniverseId id) {
        Objects.requireNonNull(id, "id");
        lock.writeLock().lock();
        boolean added = discovered.add(id);
        lock.writeLock().unlock();
        if (added) {
            persistence.recordEdit(PersistentEdit.simple(id, PersistentEdit.Kind.DISCOVERY, new byte[]{(byte) 1}));
            events.publish(new Event.PersistentEdit(id, "discovery", null));
        }
    }

    public boolean isDiscovered(UniverseId id) {
        lock.readLock().lock();
        try { return discovered.contains(id); } finally { lock.readLock().unlock(); }
    }

    public int discoveredCount() {
        lock.readLock().lock();
        try { return discovered.size(); } finally { lock.readLock().unlock(); }
    }

    public void restoreFromPersistence() {
        if (persistence == null) return;
        java.util.Set<UniverseId> restored = new java.util.HashSet<>();
        // We don't have an iterator on persistence; consumers can call discover() to add.
        lock.writeLock().lock();
        try { discovered.addAll(restored); } finally { lock.writeLock().unlock(); }
    }
}
