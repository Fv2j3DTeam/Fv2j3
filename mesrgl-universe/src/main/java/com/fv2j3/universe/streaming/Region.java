package com.fv2j3.universe.streaming;

import com.fv2j3.universe.api.ApiObject;

import java.util.Objects;

/**
 * Resident region record. Holds the lifecycle state plus the generated content.
 */
public final class Region implements ApiObject {
    private final RegionKey key;
    private RegionState state;
    private final Object content; // RegionContent implementation specific
    private final long generationMillis;

    public Region(RegionKey key, RegionState state, Object content, long generationMillis) {
        this.key = Objects.requireNonNull(key, "key");
        this.state = Objects.requireNonNull(state, "state");
        this.content = content;
        this.generationMillis = Math.max(0, generationMillis);
    }

    public RegionKey key() { return key; }
    public RegionState state() { return state; }
    public Object content() { return content; }
    public long generationMillis() { return generationMillis; }

    public void transitionTo(RegionState target) {
        if (!state.canTransitionTo(target)) {
            throw new IllegalStateException("Invalid transition " + state + " -> " + target);
        }
        this.state = target;
    }

    @Override
    public String apiId() {
        return key.encode();
    }
}