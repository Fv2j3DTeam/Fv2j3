package com.fv2j3.universe.persistence;

/**
 * Save format schema version. Bump on incompatible format changes (§86). Older
 * versions are loaded via {@link Migration}. Reject unsupported versions explicitly.
 */
public final class SaveSchema {
    private SaveSchema() {}

    /** Current schema major version. */
    public static final int MAJOR = 1;
    /** Current schema minor version. */
    public static final int MINOR = 0;
    /** Magic header: "MESRGLSAV" — 9 bytes. */
    public static final byte[] MAGIC = new byte[] {
            'M','E','S','R','G','L','S','A','V'
    };

    /** Combined version code. */
    public static int current() {
        return (MAJOR << 16) | (MINOR & 0xFFFF);
    }
}
