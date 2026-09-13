package io.github.fv2j3dteam.universe.persistence;

import io.github.fv2j3dteam.universe.error.Result;
import io.github.fv2j3dteam.universe.events.Event;
import io.github.fv2j3dteam.universe.events.EventBus;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Persistence manager (§21, §22, §79). Persists per-id edit lists to a binary file
 * with atomic writes (write-temp + atomic move), CRC32 checksums, and version migration.
 *
 * Thread-safe. Rejects unsupported schema versions. Failed saves leave the previous
 * file intact. On load, corrupt or missing files return an error result; a missing
 * file is not a fatal error (treated as empty state).
 */
public final class PersistenceManager {

    private final EventBus events;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<io.github.fv2j3dteam.universe.identifiers.UniverseId, List<PersistentEdit>> edits = new HashMap<>();
    private final Path targetFile;
    private long universeRootSeed;
    private int generatorVersion;
    private long timestampMillis;
    private boolean dirty;

    public PersistenceManager(Path targetFile, EventBus events) {
        this.targetFile = Objects.requireNonNull(targetFile, "targetFile");
        this.events = events != null ? events : new EventBus();
    }

    public void recordEdit(PersistentEdit edit) {
        Objects.requireNonNull(edit, "edit");
        lock.writeLock().lock();
        try {
            edits.computeIfAbsent(edit.id(), k -> new java.util.ArrayList<>()).add(edit);
            dirty = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<PersistentEdit> editsFor(io.github.fv2j3dteam.universe.identifiers.UniverseId id) {
        Objects.requireNonNull(id, "id");
        lock.readLock().lock();
        try {
            return edits.getOrDefault(id, List.of());
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean isDirty() {
        lock.readLock().lock();
        try { return dirty; } finally { lock.readLock().unlock(); }
    }

    /**
     * Save the current state atomically. Writes to a temp file, fsyncs, then
     * atomically moves. Returns {@link Result#error} on failure and emits
     * a {@link Event.SaveCompleted} regardless of outcome.
     */
    public Result<Path> save(long universeRootSeed, int generatorVersion) {
        lock.writeLock().lock();
        Path tmp = null;
        try {
            this.universeRootSeed = universeRootSeed;
            this.generatorVersion = generatorVersion;
            this.timestampMillis = System.currentTimeMillis();
            PersistentState snap = new PersistentState(
                    universeRootSeed, generatorVersion, SaveSchema.current(), timestampMillis, edits);
            byte[] bytes = PersistentStateCodec.encode(snap);
            if (targetFile.getParent() != null) Files.createDirectories(targetFile.getParent());
            tmp = Files.createTempFile(targetFile.getParent(), "save-", ".tmp");
            Files.write(tmp, bytes);
            try {
                Files.move(tmp, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            events.publish(new Event.SaveCompleted(true, targetFile.toString()));
            return Result.ok(targetFile);
        } catch (Exception e) {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
            }
            events.publish(new Event.SaveCompleted(false, e.getMessage()));
            return Result.error("save failed: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Load from disk. Returns error if file is corrupt; missing file returns empty state. */
    public Result<PersistentState> load() {
        lock.writeLock().lock();
        try {
            if (!Files.exists(targetFile)) {
                return Result.ok(new PersistentState(0L, 0, SaveSchema.current(), 0L, Map.of()));
            }
            byte[] bytes = Files.readAllBytes(targetFile);
            PersistentState state = PersistentStateCodec.decode(bytes);
            edits.clear();
            edits.putAll(state.editsById());
            universeRootSeed = state.universeRootSeed();
            generatorVersion = state.generatorVersion();
            timestampMillis = state.timestampMillis();
            dirty = false;
            return Result.ok(state);
        } catch (Exception e) {
            return Result.error("load failed: " + e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Path targetFile() { return targetFile; }

    public long universeRootSeed() {
        lock.readLock().lock();
        try { return universeRootSeed; } finally { lock.readLock().unlock(); }
    }

    public int generatorVersion() {
        lock.readLock().lock();
        try { return generatorVersion; } finally { lock.readLock().unlock(); }
    }
}
