package com.fv2j3.loader.runtime;

import com.fv2j3.loader.core.Fv2j3Loader;
import com.fv2j3.loader.core.LoaderEnvironment;
import com.fv2j3.loader.core.LoaderEventBus;
import com.fv2j3.loader.core.LoaderInitializationException;
import com.fv2j3.loader.core.LoaderLogger;
import com.fv2j3.loader.core.LoaderSide;
import com.fv2j3.loader.core.StandardLoaderLogger;
import com.fv2j3.minecraft.compat.Fv2j3RuntimeBridge;
import com.fv2j3.minecraft.compat.LoadingProgressTracker;
import com.fv2j3.minecraft.compat.MinecraftBootstrap;
import com.fv2j3.minecraft.compat.MinecraftBootstrap.MinecraftBootstrapResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class ServerBootstrap {
    private ServerBootstrap() {
    }

    public static void main(String[] args) {
        LoaderLogger logger = new StandardLoaderLogger("Fv2j3");
        LoaderEventBus eventBus = new LoaderEventBus();
        eventBus.subscribe(event -> logger.info(event.type() + " | " + event.source() + " | " + event.message()));

        LoaderEnvironment environment = LoaderEnvironment.detectCurrent("1.12.2", "0.1.0-SNAPSHOT", LoaderSide.DEDICATED_SERVER);
        Path modDirectory = resolveModDirectory(args);
        Path minecraftHome = resolveMinecraftHome(args);

        try {
            logger.info("Fv2j3 Dedicated Server starting");
            logger.info("Minecraft: " + environment.minecraftVersion());
            logger.info("Java: " + environment.javaVersion());
            logger.info("Mod sources: " + modDirectory);
            logger.info("Side: " + environment.side());

            MinecraftBootstrapResult runtime = MinecraftBootstrap.detect(minecraftHome);
            if (!runtime.available()) {
                throw new IllegalStateException(runtime.message());
            }
            logger.info("Minecraft runtime: " + runtime.runtimeHome());

            CountDownLatch loaderReadyLatch = new CountDownLatch(1);
            AtomicReference<Throwable> launchError = new AtomicReference<>();
            AtomicBoolean loaderReady = new AtomicBoolean(false);

            Thread minecraftThread = new Thread(() -> {
                try {
                    MinecraftBootstrap.launchServerWithLoaderCallback(minecraftHome, minecraftArguments(args),
                            (loader, success) -> {
                                if (loader != null && success) {
                                    Fv2j3RuntimeBridge.register(loader);
                                    loader.setProgressListener(progress -> {
                                        logger.info("Loading " + progress.phase() + ": "
                                                + (progress.modId() == null ? "Fv2j3" : progress.modId())
                                                + " (" + progress.completed() + "/" + progress.total() + ")");
                                        LoadingProgressTracker.getShared().onProgress(progress);
                                    });
                                    loaderReadyLatch.countDown();
                                    loaderReady.set(true);
                                }
                            });
                } catch (Throwable failure) {
                    launchError.set(failure);
                }
            }, "fv2j3-minecraft-server");
            minecraftThread.start();

            boolean loaderReadyShown = loaderReadyLatch.await(30, java.util.concurrent.TimeUnit.SECONDS);

            if (loaderReadyShown) {
                Fv2j3Loader loader = Fv2j3RuntimeBridge.activeLoader();
                if (loader != null) {
                    logger.info("Server Loader ready: " + loader.context().modRegistry().all().size() + " mods");
                    logger.info("Server Loader instance: " + loader.getClass().getName());
                    eventBus.publish("SERVER_READY", "ServerBootstrap", "Fv2j3 mod loader is active inside Minecraft Dedicated Server.");
                }
            } else {
                logger.info("Server Loader was not ready within 30 seconds.");
            }

            minecraftThread.join(300000);
            Throwable failure = launchError.get();
            if (failure != null) {
                throw new IllegalStateException("Minecraft Dedicated Server launch failed: " + failure.getMessage(), failure);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Minecraft Dedicated Server.", ex);
        } catch (IllegalStateException ex) {
            logger.error("ServerBootstrap failed: " + ex.getMessage(), ex);
            throw ex;
        }
    }

    private static Path resolveModDirectory(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--mods=")) {
                return Path.of(a.substring("--mods=".length())).toAbsolutePath().normalize();
            }
            if ("--mods".equals(a) && i + 1 < args.length) {
                return Path.of(args[i + 1]).toAbsolutePath().normalize();
            }
        }
        return Path.of("mods").toAbsolutePath().normalize();
    }

    private static Path resolveMinecraftHome(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--minecraft-home=")) {
                return Path.of(a.substring("--minecraft-home=".length())).toAbsolutePath().normalize();
            }
            if ("--minecraft-home".equals(a) && i + 1 < args.length) {
                return Path.of(args[i + 1]).toAbsolutePath().normalize();
            }
        }
        String prop = System.getProperty("fv2j3.minecraft.home");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop).toAbsolutePath().normalize();
        }
        return Path.of("minecraft").toAbsolutePath().normalize();
    }

    private static String[] minecraftArguments(String[] args) {
        java.util.List<String> filtered = new java.util.ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--minecraft-home".equals(a) || "--mods".equals(a)) {
                i++;
                continue;
            }
            if (a.startsWith("--minecraft-home=") || a.startsWith("--mods=")) {
                continue;
            }
            filtered.add(a);
        }
        return filtered.toArray(new String[0]);
    }
}
