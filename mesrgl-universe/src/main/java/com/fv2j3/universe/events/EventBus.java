package com.fv2j3.universe.events;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Thread-safe event bus. Listeners are invoked on the thread that calls {@code publish()}.
 * Delivery failures are isolated: a listener throwing does not affect other listeners (§64).
 */
public final class EventBus {
    private final CopyOnWriteArrayList<Entry<?>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong publishCount = new AtomicLong();
    private final AtomicLong failureCount = new AtomicLong();

    public <T> void subscribe(Class<T> type, Consumer<T> listener) {
        if (type == null || listener == null) return;
        listeners.add(new Entry<>(type, listener));
    }

    public <T> void unsubscribe(Class<T> type, Consumer<T> listener) {
        if (type == null || listener == null) return;
        listeners.removeIf(e -> e.type == type && e.listener == listener);
    }

    public <T> void publish(T event) {
        if (event == null) return;
        publishCount.incrementAndGet();
        for (Entry<?> e : listeners) {
            if (e.type.isInstance(event)) {
                try {
                    @SuppressWarnings("unchecked")
                    Entry<T> typed = (Entry<T>) e;
                    typed.listener.accept(event);
                } catch (Throwable t) {
                    failureCount.incrementAndGet();
                }
            }
        }
    }

    public long publishCount() { return publishCount.get(); }
    public long failureCount() { return failureCount.get(); }
    public int listenerCount() { return listeners.size(); }

    public List<Class<?>> subscribedTypes() {
        return listeners.stream().<Class<?>>map(e -> e.type).distinct().toList();
    }

    private static final class Entry<T> {
        final Class<T> type;
        final Consumer<T> listener;
        Entry(Class<T> type, Consumer<T> listener) {
            this.type = type;
            this.listener = listener;
        }
    }
}
