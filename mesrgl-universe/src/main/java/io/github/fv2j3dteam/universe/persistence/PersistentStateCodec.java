package io.github.fv2j3dteam.universe.persistence;

import io.github.fv2j3dteam.universe.identifiers.UniverseId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Binary codec for {@link PersistentState}. Stable, versioned, checksummed.
 *
 * Layout (version 1):
 * <pre>
 *   magic[9]            // "MESRGLSAV"
 *   major               // int
 *   minor               // int
 *   flags               // int (reserved)
 *   universeRootSeed    // long
 *   generatorVersion    // int
 *   timestampMillis     // long
 *   editGroupCount      // int
 *   for each group:
 *     id(7 long)        // UniverseId fields
 *     editCount         // int
 *     for each edit:
 *       timestampMillis // long
 *       kind            // byte (PersistentEdit.Kind ordinal)
 *       payloadLength   // int
 *       payload         // bytes
 *   crc32               // long (Java CRC-32 of bytes 0..endOfEdits)
 * </pre>
 */
public final class PersistentStateCodec {

    private PersistentStateCodec() {}

    public static byte[] encode(PersistentState state) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);

            out.write(SaveSchema.MAGIC);
            out.writeInt(SaveSchema.MAJOR);
            out.writeInt(SaveSchema.MINOR);
            out.writeInt(0); // flags
            out.writeLong(state.universeRootSeed());
            out.writeInt(state.generatorVersion());
            out.writeLong(state.timestampMillis());

            out.writeInt(state.editsById().size());
            for (var e : state.editsById().entrySet()) {
                writeId(out, e.getKey());
                List<PersistentEdit> edits = e.getValue();
                out.writeInt(edits.size());
                for (PersistentEdit ed : edits) {
                    out.writeLong(ed.timestampMillis());
                    out.writeByte(ed.kind().ordinal());
                    out.writeInt(ed.payload().length);
                    out.write(ed.payload());
                }
            }
            out.flush();
            byte[] body = baos.toByteArray();
            long crc = crc32(body);
            DataOutputStream finalOut = new DataOutputStream(baos);
            finalOut.writeLong(crc);
            finalOut.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("encode failed", e);
        }
    }

    public static PersistentState decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            byte[] magic = new byte[SaveSchema.MAGIC.length];
            in.readFully(magic);
            for (int i = 0; i < magic.length; i++) {
                if (magic[i] != SaveSchema.MAGIC[i]) throw new IOException("bad magic");
            }
            int major = in.readInt();
            int minor = in.readInt();
            int flags = in.readInt();
            int version = (major << 16) | (minor & 0xFFFF);
            if (major > SaveSchema.MAJOR) {
                throw new IOException("unsupported schema major=" + major);
            }
            long seed = in.readLong();
            int genVer = in.readInt();
            long ts = in.readLong();
            int groupCount = in.readInt();

            int consumed = SaveSchema.MAGIC.length + 4 + 4 + 4 + 8 + 4 + 8 + 4;
            Map<UniverseId, List<PersistentEdit>> edits = new HashMap<>();
            for (int g = 0; g < groupCount; g++) {
                UniverseId id = readId(in);
                int editCount = in.readInt();
                consumed += UniverseId.BYTES + 4;
                List<PersistentEdit> list = new ArrayList<>();
                for (int e = 0; e < editCount; e++) {
                    long edTs = in.readLong();
                    int kindOrd = in.readUnsignedByte();
                    int len = in.readInt();
                    byte[] payload = new byte[len];
                    in.readFully(payload);
                    consumed += 8 + 1 + 4 + len;
                    PersistentEdit.Kind kind = PersistentEdit.Kind.values()[kindOrd];
                    list.add(new PersistentEdit(id, edTs, kind, payload));
                }
                edits.put(id, list);
            }
            long crcRead = in.readLong();
            // verify crc
            byte[] forCrc = new byte[consumed];
            System.arraycopy(bytes, 0, forCrc, 0, consumed);
            long crcExpected = crc32(forCrc);
            if (crcRead != crcExpected) {
                throw new IOException("checksum mismatch");
            }
            // Apply migration if minor differs
            Migration.migrate(edits, version);
            return new PersistentState(seed, genVer, version, ts, edits);
        } catch (IOException e) {
            throw new RuntimeException("decode failed", e);
        }
    }

    private static void writeId(DataOutputStream out, UniverseId id) throws IOException {
        out.writeLong(id.universe());
        out.writeLong(id.galaxy());
        out.writeLong(id.system());
        out.writeLong(id.body());
        out.writeLong(id.region());
        out.writeLong(id.chunk());
        out.writeLong(id.salt());
    }

    private static UniverseId readId(DataInputStream in) throws IOException {
        long u = in.readLong();
        long g = in.readLong();
        long s = in.readLong();
        long b = in.readLong();
        long r = in.readLong();
        long c = in.readLong();
        long sa = in.readLong();
        return new UniverseId(u, g, s, b, r, c, sa);
    }

    /** Java's CRC-32 of the bytes. Stored as a long. */
    public static long crc32(byte[] bytes) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(bytes);
        return crc.getValue();
    }
}
