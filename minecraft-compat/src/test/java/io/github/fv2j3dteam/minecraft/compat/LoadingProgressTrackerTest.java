package io.github.fv2j3dteam.minecraft.compat;

import io.github.fv2j3dteam.api.ModLoadingProgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadingProgressTrackerTest {
    private final LoadingProgressTracker tracker = new LoadingProgressTracker();

    @BeforeEach
    void reset() {
        tracker.reset();
    }

    @Test
    void initialStateIsBootstrapAtZero() {
        LoadingProgressTracker.Snapshot snapshot = tracker.snapshot();
        assertEquals(LoadingStage.BOOTSTRAP, snapshot.stage());
        assertEquals(0.0, snapshot.fraction());
        assertEquals(0, snapshot.completed());
        assertEquals(0, snapshot.total());
    }

    @Test
    void updateReflectsRealLoaderEvent() {
        tracker.onProgress(new ModLoadingProgress("loading", "mymod", 5, 10));
        LoadingProgressTracker.Snapshot snapshot = tracker.snapshot();
        assertEquals(LoadingStage.LOADING_MODS, snapshot.stage());
        assertEquals(5, snapshot.completed());
        assertEquals(10, snapshot.total());
        double fraction = snapshot.fraction();
        assertTrue(fraction >= 0.28 && fraction < 0.72,
                "loading stage fraction should stay inside its slice: " + fraction);
    }

    @Test
    void fractionNeverExceedsOne() {
        for (LoadingStage stage : LoadingStage.values()) {
            for (int completed : new int[] {0, 5, Integer.MAX_VALUE / 2}) {
                double fraction = stage.fraction(completed, 10);
                assertTrue(fraction >= 0.0 && fraction <= 1.0,
                        stage + " fraction out of range: " + fraction);
            }
        }
    }

    @Test
    void fractionNeverBelowZero() {
        for (LoadingStage stage : LoadingStage.values()) {
            double fraction = stage.fraction(-5, 10);
            assertTrue(fraction >= 0.0, stage + " fraction below zero: " + fraction);
        }
    }

    @Test
    void onlyRealCompletionReachesOneHundredPercent() {
        // Halfway through the final per-mod "running" event is still below 100%.
        tracker.onProgress(new ModLoadingProgress("running", "mymod", 299, 300));
        assertTrue(tracker.snapshot().fraction() < 1.0);
        // Even a total-complete "running" event is the finalizing slice.
        tracker.onProgress(new ModLoadingProgress("running", null, 300, 300));
        assertTrue(tracker.snapshot().fraction() < 1.0);
        // Only the loader's true complete event reaches exactly 100%.
        tracker.onProgress(new ModLoadingProgress("complete", null, 300, 300));
        assertEquals(1.0, tracker.snapshot().fraction());
        assertEquals(LoadingStage.COMPLETE, tracker.snapshot().stage());
    }

    @Test
    void nullEventsAreIgnored() {
        tracker.onProgress(new ModLoadingProgress("loading", "mod", 1, 2));
        tracker.onProgress(null);
        assertEquals(LoadingStage.LOADING_MODS, tracker.snapshot().stage());
    }

    @Test
    void snapshotIsImmutable() {
        LoadingProgressTracker.Snapshot snapshot = tracker.snapshot();
        tracker.onProgress(new ModLoadingProgress("complete", null, 1, 1));
        assertEquals(LoadingStage.BOOTSTRAP, snapshot.stage());
        assertEquals(0.0, snapshot.fraction());
    }
}
