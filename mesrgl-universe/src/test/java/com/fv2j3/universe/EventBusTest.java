package com.fv2j3.universe;

import com.fv2j3.universe.biome.BiomeDefinition;
import com.fv2j3.universe.biome.BiomeRegistry;
import com.fv2j3.universe.events.Event;
import com.fv2j3.universe.events.EventBus;
import com.fv2j3.universe.identifiers.UniverseId;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    @Test
    void subscribeAndPublish() {
        EventBus bus = new EventBus();
        AtomicInteger count = new AtomicInteger();
        bus.subscribe(Event.RegionLoaded.class, e -> count.incrementAndGet());
        bus.publish(new Event.RegionLoaded(new com.fv2j3.universe.streaming.RegionKey(new UniverseId(1, 0, 0, 0, 0, 0, 0), 0, 0, 0)));
        assertEquals(1, count.get());
    }

    @Test
    void multipleSubscribers() {
        EventBus bus = new EventBus();
        AtomicInteger a = new AtomicInteger();
        AtomicInteger b = new AtomicInteger();
        bus.subscribe(Event.RegionLoaded.class, e -> a.incrementAndGet());
        bus.subscribe(Event.RegionLoaded.class, e -> b.incrementAndGet());
        bus.publish(new Event.RegionLoaded(new com.fv2j3.universe.streaming.RegionKey(new UniverseId(1, 0, 0, 0, 0, 0, 0), 0, 0, 0)));
        assertEquals(1, a.get());
        assertEquals(1, b.get());
    }

    @Test
    void listenerExceptionIsolated() {
        EventBus bus = new EventBus();
        AtomicInteger a = new AtomicInteger();
        bus.subscribe(Event.RegionLoaded.class, e -> { throw new RuntimeException("boom"); });
        bus.subscribe(Event.RegionLoaded.class, e -> a.incrementAndGet());
        bus.publish(new Event.RegionLoaded(new com.fv2j3.universe.streaming.RegionKey(new UniverseId(1, 0, 0, 0, 0, 0, 0), 0, 0, 0)));
        assertEquals(1, a.get());
        assertEquals(1, bus.failureCount());
    }
}
