package com.fv2j3.universe.events;

import com.fv2j3.universe.identifiers.UniverseId;
import com.fv2j3.universe.streaming.RegionKey;

/** Base for all event records. Events are immutable. */
public sealed interface Event permits
        Event.GenerationCompleted,
        Event.RegionLoaded,
        Event.RegionUnloaded,
        Event.SaveCompleted,
        Event.WeatherChanged,
        Event.StormSpawned,
        Event.StormDissipated,
        Event.FireStarted,
        Event.FireExtinguished,
        Event.SmokeSourceAdded,
        Event.SmokeSourceRemoved,
        Event.PersistentEdit {
    record GenerationCompleted(UniverseId id, long millis) implements Event {}
    record RegionLoaded(RegionKey key) implements Event {}
    record RegionUnloaded(RegionKey key) implements Event {}
    record SaveCompleted(boolean success, String details) implements Event {}
    record WeatherChanged(UniverseId id, String newWeather, String oldWeather) implements Event {}
    record StormSpawned(UniverseId id, long stormId) implements Event {}
    record StormDissipated(UniverseId id, long stormId) implements Event {}
    record FireStarted(UniverseId id, long sourceId, double strength) implements Event {}
    record FireExtinguished(UniverseId id, long sourceId) implements Event {}
    record SmokeSourceAdded(UniverseId id, long sourceId) implements Event {}
    record SmokeSourceRemoved(UniverseId id, long sourceId) implements Event {}
    record PersistentEdit(UniverseId id, String kind, RegionKey region) implements Event {}
}
