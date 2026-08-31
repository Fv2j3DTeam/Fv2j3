package com.fv2j3.loader.core;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class LoaderEventBus {
    private final CopyOnWriteArrayList<Consumer<LoaderEvent>> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<LoaderEvent> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void publish(String type, String source, String message) {
        publish(new LoaderEvent(type, source, message));
    }

    public void publish(LoaderEvent event) {
        Objects.requireNonNull(event, "event");
        for (Consumer<LoaderEvent> listener : listeners) {
            listener.accept(event);
        }
    }
}
