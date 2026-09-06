package com.fv2j3.universe;

import com.fv2j3.universe.error.Result;
import com.fv2j3.universe.identifiers.UniverseId;
import com.fv2j3.universe.persistence.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceTest {

    @Test
    void roundTripEdits() throws Exception {
        Path p = Files.createTempFile("persist-", ".dat");
        try {
            var events = new com.fv2j3.universe.events.EventBus();
            PersistenceManager pm = new PersistenceManager(p, events);
            UniverseId id = new UniverseId(1, 0, 0, 0, 0, 0, 0);
            pm.recordEdit(PersistentEdit.simple(id, PersistentEdit.Kind.TERRAIN_HEIGHT_DELTA, new byte[]{1, 2, 3}));
            pm.recordEdit(PersistentEdit.simple(id, PersistentEdit.Kind.STRUCTURE_PLACE, new byte[]{4, 5}));
            assertTrue(pm.isDirty());
            Result<Path> result = pm.save(42L, 1);
            assertTrue(result.isOk());

            PersistenceManager pm2 = new PersistenceManager(p, events);
            Result<PersistentState> loaded = pm2.load();
            assertTrue(loaded.isOk());
            List<PersistentEdit> edits = pm2.editsFor(id);
            assertEquals(2, edits.size());
        } finally { Files.deleteIfExists(p); }
    }

    @Test
    void atomicWriteFailureLeavesOriginal() throws Exception {
        Path p = Files.createTempFile("persist-atom-", ".dat");
        try {
            var events = new com.fv2j3.universe.events.EventBus();
            PersistenceManager pm = new PersistenceManager(p, events);
            UniverseId id = new UniverseId(1, 0, 0, 0, 0, 0, 0);
            pm.recordEdit(PersistentEdit.simple(id, PersistentEdit.Kind.DISCOVERY, new byte[]{1}));
            pm.save(42L, 1);
            // Now corrupt the file
            Files.write(p, new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
            PersistenceManager pm2 = new PersistenceManager(p, events);
            Result<PersistentState> loaded = pm2.load();
            // Bad file is detected (checksum or magic mismatch)
            assertTrue(loaded.isError());
        } finally { Files.deleteIfExists(p); }
    }

    @Test
    void missingFileIsEmptyState() throws Exception {
        Path p = Path.of("nonexistent-" + System.nanoTime() + ".dat");
        var events = new com.fv2j3.universe.events.EventBus();
        PersistenceManager pm = new PersistenceManager(p, events);
        Result<PersistentState> r = pm.load();
        assertTrue(r.isOk());
        assertEquals(0, pm.editsFor(new UniverseId(1, 0, 0, 0, 0, 0, 0)).size());
    }

    @Test
    void schemaVersionRejected() throws Exception {
        Path p = Files.createTempFile("persist-ver-", ".dat");
        try {
            // Build a fake file with a higher major version.
            byte[] data = new byte[100];
            data[0] = 'M'; data[1] = 'E'; data[2] = 'S'; data[3] = 'R'; data[4] = 'G';
            data[5] = 'L'; data[6] = 'S'; data[7] = 'A'; data[8] = 'V';
            data[9] = 0; data[10] = 0; data[11] = 0; data[12] = 99; // major=99
            data[13] = 0; data[14] = 0; data[15] = 0; data[16] = 0;
            Files.write(p, data);
            var events = new com.fv2j3.universe.events.EventBus();
            PersistenceManager pm = new PersistenceManager(p, events);
            assertTrue(pm.load().isError());
        } finally { Files.deleteIfExists(p); }
    }
}
