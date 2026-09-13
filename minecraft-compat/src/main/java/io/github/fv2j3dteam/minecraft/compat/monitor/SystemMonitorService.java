package io.github.fv2j3dteam.minecraft.compat.monitor;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs the platform {@link SystemMonitor} on a single background daemon thread
 * and publishes immutable {@link SystemStats} snapshots through an atomic
 * reference. The render thread only reads {@link #latest()} and never performs
 * any sampling itself. Failures inside the monitor are swallowed (degrading to
 * N/A) and never propagate to the launcher.
 */
public final class SystemMonitorService {
    private static final long SAMPLE_INTERVAL_MS = 1000;
    private static final String THREAD_NAME = "fv2j3-system-monitor";
    private static final SystemMonitorService SHARED = new SystemMonitorService();

    private final AtomicReference<SystemStats> latest = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile boolean stopRequested;
    private volatile Thread samplerThread;
    private volatile SystemMonitor monitor;

    private SystemMonitorService() {
    }

    /** Test-visible instance for lifecycle tests; shared instance stays a singleton. */
    static SystemMonitorService forTesting() {
        return new SystemMonitorService();
    }

    public static SystemMonitorService getShared() {
        return SHARED;
    }

    public synchronized void start() {
        start(SystemMonitors.create());
    }

    synchronized void start(SystemMonitor customMonitor) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        stopRequested = false;
        monitor = customMonitor;
        Thread thread = new Thread(this::sampleLoop, THREAD_NAME);
        thread.setDaemon(true);
        samplerThread = thread;
        thread.start();
    }

    public synchronized void stop() {
        stopRequested = true;
        Thread thread = samplerThread;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(2000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        samplerThread = null;
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    /** Latest snapshot; null until the first sample completes. */
    public SystemStats latest() {
        return latest.get();
    }

    private void sampleLoop() {
        while (!stopRequested) {
            try {
                latest.set(monitor.snapshot());
            } catch (Throwable failure) {
                // Monitoring must never break the game; degrade to memory-only stats.
                Runtime runtime = Runtime.getRuntime();
                latest.set(SystemStats.unavailable(
                        runtime.totalMemory() - runtime.freeMemory(),
                        runtime.totalMemory(),
                        runtime.maxMemory()));
            }
            try {
                Thread.sleep(SAMPLE_INTERVAL_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Test/diagnostic support: true when no sampler thread is alive. */
    public static boolean noSamplerThreadsAlive() {
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            if (THREAD_NAME.equals(entry.getKey().getName()) && entry.getKey().isAlive()) {
                return false;
            }
        }
        return true;
    }
}
