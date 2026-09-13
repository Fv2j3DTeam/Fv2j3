package io.github.fv2j3dteam.minecraft.compat;

import io.github.fv2j3dteam.api.ModLoadingProgress;
import io.github.fv2j3dteam.loader.core.LoaderLogger;
import io.github.fv2j3dteam.loader.core.StandardLoaderLogger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Single, thread-safe source of truth for loading-screen progress.
 *
 * The only writer is {@link #onProgress(ModLoadingProgress)}, which is fed by
 * the real {@code Fv2j3Loader} progress listener. No timers, no fake
 * increments: 100% is only ever reached when the loader reports its final
 * "complete" lifecycle event.
 */
public final class LoadingProgressTracker {
    private static final LoaderLogger LOGGER = new StandardLoaderLogger("Fv2j3");
    private static final LoadingProgressTracker SHARED = new LoadingProgressTracker();

    /**
     * Immutable snapshot read by the render thread.
     */
    public record Snapshot(LoadingStage stage, String modId, int completed, int total) {
        public Snapshot {
            stage = stage == null ? LoadingStage.BOOTSTRAP : stage;
        }

        public double fraction() {
            return stage.fraction(completed, total);
        }
    }

    private final AtomicReference<Snapshot> current =
            new AtomicReference<>(new Snapshot(LoadingStage.BOOTSTRAP, null, 0, 0));
    private volatile LoadingStage lastLoggedStage = null;
    private volatile int lastLoggedPercent = -1;

    public static LoadingProgressTracker getShared() {
        return SHARED;
    }

    public Snapshot snapshot() {
        return current.get();
    }

    /**
     * Called from the real loader progress listener. Logs stage changes and
     * whole-percent progress changes only (never per frame).
     */
    public void onProgress(ModLoadingProgress progress) {
        if (progress == null) {
            return;
        }
        LoadingStage stage = LoadingStage.fromPhase(progress.phase());
        Snapshot snapshot = new Snapshot(stage, progress.modId(), progress.completed(), progress.total());
        current.set(snapshot);

        if (stage != lastLoggedStage) {
            LOGGER.info("Loading stage: " + stage.name());
            lastLoggedStage = stage;
            lastLoggedPercent = -1;
        }
        int percent = (int) Math.round(snapshot.fraction() * 100.0);
        if (percent != lastLoggedPercent) {
            LOGGER.info("Loading progress: " + percent + "%");
            lastLoggedPercent = percent;
        }
        if (stage == LoadingStage.COMPLETE) {
            LOGGER.info("Loading complete");
        }
    }

    /** Test support: restores the initial state. */
    public void reset() {
        current.set(new Snapshot(LoadingStage.BOOTSTRAP, null, 0, 0));
        lastLoggedStage = null;
        lastLoggedPercent = -1;
    }
}
