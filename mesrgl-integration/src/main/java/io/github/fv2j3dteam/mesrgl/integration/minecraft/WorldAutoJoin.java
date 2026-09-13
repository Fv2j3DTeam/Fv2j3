package io.github.fv2j3dteam.mesrgl.integration.minecraft;

/**
 * Drives the vanilla GUIs into a singleplayer world automatically, so the
 * MesrGL world-render pipeline can be verified end-to-end without manual
 * clicks. The chain was traced from the vanilla jar bytecode:
 *
 *   GuiMainMenu (blr):      button id 1  -&gt; "Singleplayer"
 *   GuiWorldSelection (bok): button id 3  -&gt; "Create New World"
 *   GuiCreateWorld (boi):    button id 0  -&gt; "Create New World" (creates +
 *                            launches the integrated server)
 *
 * The driver only invokes the screens' own actionPerformed handlers through
 * reflection - it never fabricates game state. Off by default is never the
 * case for verification runs; disable with -Dfv2j3.mesrgl.autoJoin=off.
 */
final class WorldAutoJoin {

    private static final String[] JOIN_CHAIN = {"blr", "bok", "boi"};
    private static final int[] JOIN_BUTTONS = {1, 3, 0};

    private WorldAutoJoin() {
    }

    static void maybeStart(ClassLoader mcClassLoader) {
        if ("off".equalsIgnoreCase(System.getProperty("fv2j3.mesrgl.autoJoin", "on"))) {
            return;
        }
        Thread driver = new Thread(() -> run(mcClassLoader), "MesrGL-AutoJoin");
        driver.setDaemon(true);
        driver.start();
    }

    private static void run(ClassLoader mcClassLoader) {
        long deadline = System.currentTimeMillis() + 15 * 60_000L;
        try {
            MinecraftReflection mc = MinecraftReflection.get(mcClassLoader);
            waitForMenuInteractive(mcClassLoader, deadline);
            String lastStep = "";
            long lastStateLog = 0;
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(700);
                Object minecraft = mc.minecraftInstance();
                long now = System.currentTimeMillis();
                if (now - lastStateLog > 10_000) {
                    lastStateLog = now;
                    Object screenProbe = minecraft == null ? null : mc.currentScreen(minecraft);
                    StringBuilder ids = new StringBuilder();
                    if (screenProbe != null) {
                        for (Object b : mc.screenButtons(screenProbe)) {
                            ids.append(' ').append(mc.buttonId(b));
                        }
                    }
                    System.out.println("[MesrGL] auto-join state: mc=" + (minecraft != null)
                            + " screen=" + (screenProbe == null ? "null" : screenProbe.getClass().getName())
                            + " world=" + (minecraft == null ? "?" : (mc.currentWorld(minecraft) != null))
                            + " buttons=[" + ids + "]");
                }
                if (minecraft == null) {
                    continue;
                }
                // A non-null integrated server means a world is starting or
                // live. Clicking in that window re-triggers world creation and
                // tears the joining world back down (observed as a bok re-click
                // racing "Preparing spawn area"), so this window is click-free.
                if (mc.integratedServer(minecraft) != null) {
                    continue;
                }
                Object world = mc.currentWorld(minecraft);
                if (world != null) {
                    System.out.println("[MesrGL] auto-join: world is live - pipeline can engage");
                    return;
                }
                Object screen = mc.currentScreen(minecraft);
                if (screen == null) {
                    continue; // loading/transition screen
                }
                String screenClass = screen.getClass().getName();
                int step = stepIndex(screenClass);
                if (step < 0) {
                    continue; // not part of the join chain (options, etc.)
                }
                String stepKey = screenClass + "#" + JOIN_BUTTONS[step];
                if (stepKey.equals(lastStep)) {
                    continue; // already clicked this screen; wait for the next
                }
                Object button = findButton(mc, screen, JOIN_BUTTONS[step]);
                if (button == null) {
                    continue; // button not initialized yet
                }
                System.out.println("[MesrGL] auto-join: " + screenClass + " -> button "
                        + JOIN_BUTTONS[step]);
                try {
                    mc.clickButtonOnRenderThread(minecraft, screen, button);
                } catch (Throwable clickFailure) {
                    // A click can legitimately race the vanilla menu state; log
                    // and retry the step rather than abandoning the run.
                    System.out.println("[MesrGL] auto-join: click failed ("
                            + clickFailure + ") - will retry");
                    lastStep = "";
                    continue;
                }
                lastStep = stepKey;
            }
            System.out.println("[MesrGL] auto-join: timed out waiting for a world");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            System.err.println("[MesrGL] auto-join stopped: " + t);
        }
    }

    /**
     * Clicks before the mod-menu loading screen clears hit a vanilla NPE in
     * the world-selection screen; wait for ClientBootstrap's
     * setLoading(false) (observed via the transformer's static flag).
     */
    private static void waitForMenuInteractive(ClassLoader mcClassLoader, long deadline)
            throws Exception {
        Class<?> transformer = Class.forName(
                "io.github.fv2j3dteam.minecraft.compat.MinecraftModMenuTransformer", true, mcClassLoader);
        java.lang.reflect.Field loading = transformer.getDeclaredField("loading");
        loading.setAccessible(true);
        while (System.currentTimeMillis() < deadline) {
            Object value = loading.get(null);
            if (Boolean.FALSE.equals(value)) {
                System.out.println("[MesrGL] auto-join: menu is interactive");
                return;
            }
            Thread.sleep(500);
        }
        System.out.println("[MesrGL] auto-join: proceeding despite the loading flag");
    }

    private static int stepIndex(String screenClass) {
        for (int i = 0; i < JOIN_CHAIN.length; i++) {
            if (JOIN_CHAIN[i].equals(screenClass)) {
                return i;
            }
        }
        return -1;
    }

    private static Object findButton(MinecraftReflection mc, Object screen, int id) {
        for (Object button : mc.screenButtons(screen)) {
            if (mc.buttonId(button) == id) {
                return button;
            }
        }
        return null;
    }
}
