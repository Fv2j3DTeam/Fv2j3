package io.github.fv2j3dteam.loader.core;

public final class PerformanceBenchmark {
    private PerformanceBenchmark() {
    }

    public static BenchmarkSnapshot snapshot(String label, Runnable action) {
        return snapshot(label, action, 3);
    }

    public static BenchmarkSnapshot snapshot(String label, Runnable action, int samples) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Benchmark label is required.");
        }
        if (action == null) {
            throw new IllegalArgumentException("Benchmark action is required.");
        }
        if (samples <= 0) {
            throw new IllegalArgumentException("Benchmark sample count must be positive.");
        }

        long[] durations = new long[samples];
        for (int index = 0; index < samples; index++) {
            long start = System.nanoTime();
            action.run();
            durations[index] = System.nanoTime() - start;
        }

        long total = 0L;
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (long duration : durations) {
            total += duration;
            if (duration < min) {
                min = duration;
            }
            if (duration > max) {
                max = duration;
            }
        }

        return new BenchmarkSnapshot(label, samples, total, total / samples, min, max);
    }

    public record BenchmarkSnapshot(String label, int samples, long totalNanos, long averageNanos, long minNanos, long maxNanos) {
        @Override
        public String toString() {
            return label + "[samples=" + samples
                    + ", avgMs=" + (averageNanos / 1_000_000.0)
                    + ", minMs=" + (minNanos / 1_000_000.0)
                    + ", maxMs=" + (maxNanos / 1_000_000.0)
                    + "]";
        }
    }
}
