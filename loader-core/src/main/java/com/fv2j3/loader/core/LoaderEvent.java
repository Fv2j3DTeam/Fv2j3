package com.fv2j3.loader.core;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record LoaderEvent(String type, String source, String message, Map<String, String> metadata, Instant timestamp) {
    public LoaderEvent {
        type = Objects.requireNonNullElse(type, "unknown");
        source = Objects.requireNonNullElse(source, "unknown");
        message = Objects.requireNonNullElse(message, "");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public LoaderEvent(String type, String source, String message) {
        this(type, source, message, Map.of(), Instant.now());
    }
}
