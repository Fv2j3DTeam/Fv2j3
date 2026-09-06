package com.fv2j3.universe.diagnostics;

/**
 * Profiler API. Wraps {@link Diagnostics} and adds a try-with-resources scope helper.
 */
public final class Profiler {
    private final Diagnostics diagnostics;

    public Profiler(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public Scope time(String name) {
        return new Scope(name);
    }

    public final class Scope implements AutoCloseable {
        private final String name;
        private final long startNanos;

        Scope(String name) {
            this.name = name;
            this.startNanos = System.nanoTime();
        }

        @Override
        public void close() {
            long dt = System.nanoTime() - startNanos;
            diagnostics.recordTiming(name, dt);
        }
    }
}
