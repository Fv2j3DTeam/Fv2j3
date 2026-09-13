package io.github.fv2j3dteam.mesrgl.integration.minecraft;

/**
 * Minimal growable primitive arrays for scene mesh accumulation.
 *
 * Boxed ArrayList&lt;Float&gt; costs ~24 bytes per value (16-byte Float header
 * + reference + Object[] slot) and a full scene build accumulates tens of
 * millions of vertex floats, which OOMs the 2 GB game heap. Primitives cost
 * exactly 4 bytes per value with no GC pressure.
 */
final class GrowableFloatArray {
    private float[] data = new float[4096];
    private int size;

    void add(float v) {
        if (size == data.length) {
            float[] grown = new float[data.length * 2];
            System.arraycopy(data, 0, grown, 0, size);
            data = grown;
        }
        data[size++] = v;
    }

    int size() {
        return size;
    }

    void append(GrowableFloatArray other) {
        if (size + other.size > data.length) {
            float[] grown = new float[Math.max(data.length * 2, size + other.size)];
            System.arraycopy(data, 0, grown, 0, size);
            data = grown;
        }
        System.arraycopy(other.data, 0, data, size, other.size);
        size += other.size;
    }

    float[] toArray() {
        float[] out = new float[size];
        System.arraycopy(data, 0, out, 0, size);
        return out;
    }
}

final class GrowableIntArray {
    private int[] data = new int[4096];
    private int size;

    void add(int v) {
        if (size == data.length) {
            int[] grown = new int[data.length * 2];
            System.arraycopy(data, 0, grown, 0, size);
            data = grown;
        }
        data[size++] = v;
    }

    int size() {
        return size;
    }

    int get(int i) {
        return data[i];
    }

    void append(GrowableIntArray other) {
        if (size + other.size > data.length) {
            int[] grown = new int[Math.max(data.length * 2, size + other.size)];
            System.arraycopy(data, 0, grown, 0, size);
            data = grown;
        }
        System.arraycopy(other.data, 0, data, size, other.size);
        size += other.size;
    }

    int[] toArray() {
        int[] out = new int[size];
        System.arraycopy(data, 0, out, 0, size);
        return out;
    }
}
