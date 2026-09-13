package io.github.fv2j3dteam.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class Fv2j3Events {
    private static final List<Consumer<Fv2j3Event>> CLIENT_STARTING = new ArrayList<>();
    private static final List<Consumer<Fv2j3Event>> CLIENT_STARTED = new ArrayList<>();
    private static final List<Consumer<Fv2j3Event>> CLIENT_STOPPING = new ArrayList<>();
    private static final List<Consumer<Fv2j3Event>> SERVER_STARTING = new ArrayList<>();
    private static final List<Consumer<Fv2j3Event>> SERVER_STARTED = new ArrayList<>();
    private static final List<Consumer<Fv2j3Event>> SERVER_STOPPING = new ArrayList<>();
    private static final List<Consumer<Fv2j3Event>> REGISTRY_BUILD = new ArrayList<>();

    private Fv2j3Events() {}

    public static void onClientStarting(Consumer<Fv2j3Event> listener) { CLIENT_STARTING.add(listener); }
    public static void onClientStarted(Consumer<Fv2j3Event> listener) { CLIENT_STARTED.add(listener); }
    public static void onClientStopping(Consumer<Fv2j3Event> listener) { CLIENT_STOPPING.add(listener); }
    public static void onServerStarting(Consumer<Fv2j3Event> listener) { SERVER_STARTING.add(listener); }
    public static void onServerStarted(Consumer<Fv2j3Event> listener) { SERVER_STARTED.add(listener); }
    public static void onServerStopping(Consumer<Fv2j3Event> listener) { SERVER_STOPPING.add(listener); }
    public static void onRegistryBuild(Consumer<Fv2j3Event> listener) { REGISTRY_BUILD.add(listener); }

    public static void fireClientStarting(Fv2j3Event event) { CLIENT_STARTING.forEach(l -> fire(l, event)); }
    public static void fireClientStarted(Fv2j3Event event) { CLIENT_STARTED.forEach(l -> fire(l, event)); }
    public static void fireClientStopping(Fv2j3Event event) { CLIENT_STOPPING.forEach(l -> fire(l, event)); }
    public static void fireServerStarting(Fv2j3Event event) { SERVER_STARTING.forEach(l -> fire(l, event)); }
    public static void fireServerStarted(Fv2j3Event event) { SERVER_STARTED.forEach(l -> fire(l, event)); }
    public static void fireServerStopping(Fv2j3Event event) { SERVER_STOPPING.forEach(l -> fire(l, event)); }
    public static void fireRegistryBuild(Fv2j3Event event) { REGISTRY_BUILD.forEach(l -> fire(l, event)); }

    private static void fire(Consumer<Fv2j3Event> listener, Fv2j3Event event) {
        try { listener.accept(event); }
        catch (Throwable t) { /* log and continue */ }
    }

    public static int clientStartingCount() { return CLIENT_STARTING.size(); }
    public static int clientStartedCount() { return CLIENT_STARTED.size(); }
    public static int clientStoppingCount() { return CLIENT_STOPPING.size(); }
    public static int serverStartingCount() { return SERVER_STARTING.size(); }
    public static int serverStartedCount() { return SERVER_STARTED.size(); }
    public static int serverStoppingCount() { return SERVER_STOPPING.size(); }
    public static int registryBuildCount() { return REGISTRY_BUILD.size(); }

    public static void reset() {
        CLIENT_STARTING.clear();
        CLIENT_STARTED.clear();
        CLIENT_STOPPING.clear();
        SERVER_STARTING.clear();
        SERVER_STARTED.clear();
        SERVER_STOPPING.clear();
        REGISTRY_BUILD.clear();
    }
}
