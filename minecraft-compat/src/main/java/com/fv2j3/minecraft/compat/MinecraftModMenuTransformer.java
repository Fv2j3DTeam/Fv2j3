package com.fv2j3.minecraft.compat;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import com.fv2j3.api.ModLoadingProgress;
import com.fv2j3.minecraft.compat.monitor.SystemMonitorService;
import com.fv2j3.minecraft.compat.monitor.SystemStats;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;

public final class MinecraftModMenuTransformer {
    private static final String MAIN_MENU = "blr";
    private static final int MODS_BUTTON = 1729;
    private static final int BACK_BUTTON = 1730;
    private static final int ESC_KEY_CODE = 1;
    private static final int LEFT_MARGIN = 20;
    private static final int LIST_LEFT = 40;
    private static final int LIST_TOP = 90;
    private static final int LIST_ROW_HEIGHT = 22;
    private static final int LIST_PANEL_WIDTH = 280;
    private static final int BACK_BUTTON_WIDTH = 80;
    private static final int BACK_BUTTON_HEIGHT = 20;
    private static final int BACK_BUTTON_TOP = 20;
    private static volatile List<String> MODS = List.of();
    private static final AtomicBoolean LOADING_MARKER_WRITTEN = new AtomicBoolean();
    private static final AtomicInteger LOADING_PROGRESS_MARKER = new AtomicInteger(-1);
    private static final AtomicInteger LOADING_ANIMATION_MARKER = new AtomicInteger(-1);
    private static final String[] LOADING_FRAMES = {"Loading.", "Loading..", "Loading...", "Loading...."};
    private static volatile long loadingAnimationStartNanos;
    private static final AtomicInteger MENU_OPEN_COUNT = new AtomicInteger();
    private static volatile boolean open;
    private static volatile boolean loading;
    private static final java.util.Deque<List<Object>> MOD_BUTTON_HISTORY =
            new java.util.concurrent.ConcurrentLinkedDeque<>();
    private static volatile boolean wasLoading = false;
    private static final CountDownLatch FIRST_RENDER_LATCH = new CountDownLatch(1);
    private static volatile boolean firstFrameRendered = false;
    private static volatile int lastEscFrame = -1;

    private MinecraftModMenuTransformer() {
    }

    public static void setMods(List<String> mods) {
        MODS = mods == null ? List.of() : List.copyOf(mods);
        MENU_OPEN_COUNT.set(0);
    }

    public static void setLoading(boolean value) {
        loading = value;
        if (value) {
            LOADING_MARKER_WRITTEN.set(false);
            LOADING_PROGRESS_MARKER.set(-1);
            LOADING_ANIMATION_MARKER.set(-1);
            loadingAnimationStartNanos = System.nanoTime();
            firstFrameRendered = false;
        }
    }

    /** Bridges a real loader progress event into the shared tracker. */
    public static void setLoadingProgress(ModLoadingProgress progress) {
        LoadingProgressTracker.getShared().onProgress(progress);
    }

    public static boolean waitForFirstFrame(long timeoutMs) {
        try {
            return FIRST_RENDER_LATCH.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static void resetFirstFrameLatch() {
        FIRST_RENDER_LATCH.countDown();
    }

    public static byte[] transform(String name, byte[] bytes) {
        if (!MAIN_MENU.equals(name)) {
            return bytes;
        }
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String firstType, String secondType) {
                return "java/lang/Object";
            }
        };
        boolean[] transformed = new boolean[4];
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature, String[] exceptions) {
                MethodVisitor visitor = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                if ("b".equals(methodName) && "()V".equals(descriptor)) {
                    transformed[0] = true;
                    return new MethodVisitor(api, visitor) {
                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN) {
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        "com/fv2j3/minecraft/compat/MinecraftModMenuTransformer",
                                        "addButton",
                                        "(Ljava/lang/Object;)V",
                                        false
                                );
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
                if ("a".equals(methodName) && "(Lbja;)V".equals(descriptor)) {
                    transformed[1] = true;
                    return new MethodVisitor(api, visitor) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitVarInsn(Opcodes.ALOAD, 1);
                            super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "com/fv2j3/minecraft/compat/MinecraftModMenuTransformer",
                                    "handleButton",
                                    "(Ljava/lang/Object;Ljava/lang/Object;)V",
                                    false
                            );
                        }
                    };
                }
                if ("a".equals(methodName) && "(IIF)V".equals(descriptor)) {
                    transformed[2] = true;
                    return new MethodVisitor(api, visitor) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitVarInsn(Opcodes.ILOAD, 1);
                            super.visitVarInsn(Opcodes.ILOAD, 2);
                            super.visitVarInsn(Opcodes.FLOAD, 3);
                            super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "com/fv2j3/minecraft/compat/MinecraftModMenuTransformer",
                                    "render",
                                    "(Ljava/lang/Object;IIF)Z",
                                    false
                            );
                            Label continueRendering = new Label();
                            super.visitJumpInsn(Opcodes.IFEQ, continueRendering);
                            super.visitInsn(Opcodes.RETURN);
                            super.visitLabel(continueRendering);
                        }
                    };
                }
                if ("a".equals(methodName) && "(CI)V".equals(descriptor)) {
                    transformed[3] = true;
                    return new MethodVisitor(api, visitor) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitVarInsn(Opcodes.ILOAD, 2);
                            super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "com/fv2j3/minecraft/compat/MinecraftModMenuTransformer",
                                    "handleKey",
                                    "(Ljava/lang/Object;I)V",
                                    false
                            );
                        }
                    };
                }
                return visitor;
            }
        }, ClassReader.EXPAND_FRAMES);
        if (!transformed[0] || !transformed[1] || !transformed[2] || !transformed[3]) {
            throw new IllegalStateException("Minecraft 1.12.2 main-menu bytecode did not match the expected methods.");
        }
        return writer.toByteArray();
    }

    public static void addButton(Object screen) {
        try {
            open = false;
            List<Object> buttons = (List<Object>) field(screen, "n").get(screen);
            boolean present = false;
            for (Object button : buttons) {
                if (!"bja".equals(button.getClass().getName())) {
                    continue;
                }
                if (id(button) == MODS_BUTTON) {
                    present = true;
                    break;
                }
                Field label = field(button, "j");
                Object value = label.get(button);
                if (value instanceof String text && text.toLowerCase(java.util.Locale.ROOT).contains("realms")) {
                    field(button, "k").setInt(button, MODS_BUTTON);
                    label.set(button, "Mods");
                    present = true;
                    break;
                }
            }
            if (!present) {
                buttons.add(button(screen, MODS_BUTTON, "Mods"));
            }
            while (!MOD_BUTTON_HISTORY.isEmpty()) {
                MOD_BUTTON_HISTORY.poll();
            }
            marker("main-menu-transformed");
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to inject the Fv2j3 Mods button.", ex);
        }
    }

    public static void handleButton(Object screen, Object button) {
        try {
            int buttonId = id(button);
            if (buttonId != MODS_BUTTON && buttonId != BACK_BUTTON) {
                return;
            }
            if (buttonId == MODS_BUTTON) {
                openModsMenu(screen);
            } else if (buttonId == BACK_BUTTON) {
                closeModsMenu(screen);
            }
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to update the Fv2j3 Mods menu.", ex);
        }
    }

    public static void handleKey(Object screen, int keyCode) {
        if (!open) {
            return;
        }
        if (keyCode != ESC_KEY_CODE) {
            return;
        }
        try {
            closeModsMenu(screen);
            marker("mod-menu-escape");
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to close the Fv2j3 Mods menu via ESC.", ex);
        }
    }

    private static void openModsMenu(Object screen) throws ReflectiveOperationException {
        open = true;
        List<Object> buttons = (List<Object>) field(screen, "n").get(screen);
        List<Object> snapshot = new ArrayList<>(buttons);
        MOD_BUTTON_HISTORY.push(snapshot);
        buttons.clear();
        buttons.add(createBackButton(screen));
        marker("mod-menu-opened");
        if (!MODS.isEmpty() && MENU_OPEN_COUNT.incrementAndGet() >= 2) {
            marker("mod-menu-regression-pass");
        }
    }

    private static void closeModsMenu(Object screen) throws ReflectiveOperationException {
        if (!open) {
            return;
        }
        open = false;
        List<Object> buttons = (List<Object>) field(screen, "n").get(screen);
        if (!MOD_BUTTON_HISTORY.isEmpty()) {
            List<Object> saved = MOD_BUTTON_HISTORY.pop();
            buttons.clear();
            buttons.addAll(saved);
        }
        marker("mod-menu-back");
    }

    public static boolean render(Object screen, int mouseX, int mouseY, float partialTicks) {
        if (!loading && !open) {
            if (wasLoading) {
                wasLoading = false;
                marker("loading-screen-closed");
            }
            return false;
        }
        try {
            int width = (int) field(screen, "l").get(screen);
            int height = (int) field(screen, "m").get(screen);
            method(screen.getClass().getSuperclass(), "d_", int.class).invoke(screen, 0);
            Object font = field(screen, "q").get(screen);
            Method draw = font.getClass().getMethod("a", String.class, int.class, int.class, int.class);
            Object minecraft = field(screen, "j").get(screen);
            ClassLoader classLoader = screen.getClass().getClassLoader();
            if (loading) {
                wasLoading = true;
                if (!firstFrameRendered) {
                    firstFrameRendered = true;
                    FIRST_RENDER_LATCH.countDown();
                }
                renderLoading(font, draw, classLoader, width, height);
                return true;
            }
            drawBackground(classLoader, width, height);
            drawHeader(font, draw, width);
            drawModList(font, draw, classLoader, width, height, mouseX, mouseY);
            drawButtons(minecraft, screen, mouseX, mouseY, partialTicks);
            return true;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to render the Fv2j3 Mods menu.", ex);
        }
    }

    private static void drawBackground(ClassLoader classLoader, int width, int height) {
        fillRectangle(classLoader, 0, 0, width, height, 0xA0000000);
    }

    private static void drawHeader(Object font, Method draw, int width) throws ReflectiveOperationException {
        String title = "Fv2j3 Mods";
        String subtitle = "Installed modifications";
        drawCenteredString(draw, font, title, width / 2, 40, 0xFFFFFFFF, true);
        drawCenteredString(draw, font, subtitle, width / 2, 56, 0xFFAAAAAA, false);
    }

    private static void drawCenteredString(Method draw, Object font, String text, int centerX, int y, int color, boolean shadow) throws ReflectiveOperationException {
        int width = stringWidth(font, text);
        int x = centerX - width / 2;
        if (shadow) {
            Method shadowDraw = font.getClass().getMethod("a", String.class, float.class, float.class, int.class, boolean.class);
            shadowDraw.invoke(font, text, (float) x, (float) y, color, true);
        } else {
            draw.invoke(font, text, x, y, color);
        }
    }

    private static int stringWidth(Object font, String text) throws ReflectiveOperationException {
        Method width = font.getClass().getMethod("a", String.class);
        Object result = width.invoke(font, text);
        if (result instanceof Number num) {
            return num.intValue();
        }
        return ((Integer) result).intValue();
    }

    private static List<String> resolveMods() {
        List<String> bridgeMods = Fv2j3RuntimeBridge.currentMods();
        if (!bridgeMods.isEmpty()) {
            return bridgeMods;
        }
        return MODS;
    }

    private static void drawModList(Object font, Method draw, ClassLoader classLoader, int width, int height, int mouseX, int mouseY)
            throws ReflectiveOperationException {
        List<String> mods = resolveMods();
        int listLeft = (width - LIST_PANEL_WIDTH) / 2;
        int listTop = LIST_TOP;
        int listRight = listLeft + LIST_PANEL_WIDTH;
        int listBottom = listTop + LIST_ROW_HEIGHT * Math.max(mods.size(), 1) + 16;
        if (listBottom > height - 30) {
            listBottom = height - 30;
        }
        fillRectangle(classLoader, listLeft, listTop, listRight, listBottom, 0x80333333);
        drawRectOutline(classLoader, listLeft, listTop, listRight, listBottom, 0xFF555555);

        if (mods.isEmpty()) {
            drawCenteredString(draw, font, "No mods loaded.", width / 2, listTop + 14, 0xFFAAAAAA, false);
            return;
        }
        for (int index = 0; index < mods.size(); index++) {
            int rowTop = listTop + 8 + index * LIST_ROW_HEIGHT;
            int rowBottom = rowTop + LIST_ROW_HEIGHT - 4;
            boolean hovered = mouseX >= listLeft && mouseX < listRight
                    && mouseY >= rowTop && mouseY < rowBottom;
            if (hovered) {
                fillRectangle(classLoader, listLeft + 1, rowTop, listRight - 1, rowBottom, 0x55FFFFFF);
            }
            String mod = mods.get(index);
            int nameColor = hovered ? 0xFFFFFF55 : 0xFFFFFFFF;
            String name = modName(mod);
            String status = modStatus(mod);
            int textY = rowTop + (LIST_ROW_HEIGHT - 8) / 2;
            int textX = listLeft + 12;
            draw.invoke(font, shorten(name, LIST_PANEL_WIDTH - 70), textX, textY, nameColor);
            if (status != null && !status.isEmpty()) {
                int statusWidth = stringWidth(font, status);
                draw.invoke(font, status, listRight - statusWidth - 12, textY, 0xFFAAAAAA);
            }
            if (index < mods.size() - 1) {
                fillRectangle(classLoader, listLeft + 8, rowBottom, listRight - 8, rowBottom + 1, 0x40FFFFFF);
            }
        }
    }

    private static void drawButtons(Object minecraft, Object screen, int mouseX, int mouseY, float partialTicks)
            throws ReflectiveOperationException {
        Method drawButton = Class.forName("bja", true, screen.getClass().getClassLoader())
                .getMethod("a", minecraft.getClass(), int.class, int.class, float.class);
        int width = (int) field(screen, "l").get(screen);
        List<?> buttonList = (List<?>) field(screen, "n").get(screen);
        for (Object btn : buttonList) {
            if (!"bja".equals(btn.getClass().getName())) {
                continue;
            }
            int btnId = id(btn);
            if (btnId == BACK_BUTTON) {
                field(btn, "h").setInt(btn, LEFT_MARGIN);
                field(btn, "i").setInt(btn, BACK_BUTTON_TOP);
                field(btn, "f").setInt(btn, BACK_BUTTON_WIDTH);
                field(btn, "g").setInt(btn, BACK_BUTTON_HEIGHT);
            }
            drawButton.invoke(btn, minecraft, mouseX, mouseY, partialTicks);
        }
    }

    private static void drawRectOutline(ClassLoader classLoader, int left, int top, int right, int bottom, int color) {
        fillRectangle(classLoader, left, top, right, top + 1, color);
        fillRectangle(classLoader, left, bottom - 1, right, bottom, color);
        fillRectangle(classLoader, left, top, left + 1, bottom, color);
        fillRectangle(classLoader, right - 1, top, right, bottom, color);
    }

    private static String modName(String mod) {
        if (mod == null) return "";
        int pipe = mod.indexOf(" | ");
        return pipe < 0 ? mod : mod.substring(0, pipe);
    }

    private static String modStatus(String mod) {
        if (mod == null) return "";
        int lastPipe = mod.lastIndexOf(" | ");
        if (lastPipe < 0) return "";
        return mod.substring(lastPipe + 3);
    }

    private static String shorten(String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxWidth / 6) {
            return text;
        }
        int len = Math.max(0, maxWidth / 6 - 3);
        return text.substring(0, Math.min(text.length(), len)) + "...";
    }

    private static void renderLoading(Object font, Method draw, ClassLoader classLoader, int width, int height)
            throws ReflectiveOperationException {
        LoadingProgressTracker.Snapshot progress = LoadingProgressTracker.getShared().snapshot();
        SystemStats stats = SystemMonitorService.getShared().latest();

        drawCenteredString(draw, font, "Fv2j3", width / 2, height / 2 - 70, 0xFFFFFF55, true);
        drawCenteredString(draw, font, "Loading Minecraft...", width / 2, height / 2 - 55, 0xFFFFFFFF, true);
        long elapsed = (System.nanoTime() - loadingAnimationStartNanos) / 1_000_000L;
        int animationFrame = (int) ((elapsed / 250L) % LOADING_FRAMES.length);
        drawCenteredString(draw, font, progress.stage().display() + " " + LOADING_FRAMES[animationFrame],
                width / 2, height / 2 - 40, 0xFFAAAAAA, false);
        if (LOADING_ANIMATION_MARKER.getAndSet(animationFrame) != animationFrame) {
            marker("loading-screen-animation-" + animationFrame);
        }

        int left = width / 2 - 100;
        int top = height / 2 - 20;
        fillRectangle(classLoader, left, top, left + 200, top + 12, 0xFF333333);
        fillRectangle(classLoader, left, top, left + (int) (200 * progress.fraction()), top + 12, 0xFF55AA55);
        String percentText = (int) (progress.fraction() * 100) + "%";
        drawCenteredString(draw, font, percentText, width / 2, top + 16, 0xFFFFFFFF, false);

        String subject = progress.modId() == null ? "Fv2j3" : progress.modId();
        drawCenteredString(draw, font, subject, width / 2, top + 30, 0xFFFFFFFF, false);
        drawCenteredString(draw, font, "Mods: " + progress.completed() + " / " + progress.total(),
                width / 2, top + 44, 0xFFAAAAAA, false);

        int statsLeft = width / 2 - 100;
        int statsTop = top + 64;
        int lineHeight = 12;
        if (stats == null) {
            stats = SystemStats.unavailable(0, 0, 0);
        }
        draw.invoke(font, "CPU       " + stats.cpuUsagePercent(), statsLeft, statsTop, 0xFFFFFFFF);
        draw.invoke(font, "GPU       " + stats.gpuUsagePercent(), statsLeft, statsTop + lineHeight, 0xFFFFFFFF);
        draw.invoke(font, "RAM       " + stats.memoryGigabytes(), statsLeft, statsTop + lineHeight * 2, 0xFFFFFFFF);
        draw.invoke(font, "CPU Temp  " + stats.cpuTemperatureCelsius(), statsLeft, statsTop + lineHeight * 3, 0xFFFFFFFF);
        draw.invoke(font, "GPU Temp  " + stats.gpuTemperatureCelsius(), statsLeft, statsTop + lineHeight * 4, 0xFFFFFFFF);

        if (LOADING_MARKER_WRITTEN.compareAndSet(false, true)) {
            marker("loading-screen-visible");
            marker("loading-screen-rendered");
        }
        if (LOADING_PROGRESS_MARKER.getAndSet(progress.completed()) != progress.completed()) {
            marker("loading-screen-progress-" + progress.completed());
        }
        if (progress.stage() == LoadingStage.COMPLETE) {
            marker("loading-screen-complete");
        }
    }

    private static void fillRectangle(ClassLoader classLoader, int left, int top, int right, int bottom, int color) {
        try {
            Class<?> gui = Class.forName("bir", true, classLoader);
            Method fill = gui.getMethod("a", int.class, int.class, int.class, int.class, int.class);
            fill.invoke(null, left, top, right, bottom, color);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to render the Fv2j3 loading progress bar.", ex);
        }
    }

    private static Object button(Object screen, int id, String label) throws ReflectiveOperationException {
        Class<?> buttonType = Class.forName("bja", true, screen.getClass().getClassLoader());
        Constructor<?> constructor = buttonType.getConstructor(int.class, int.class, int.class, int.class, int.class, String.class);
        int width = (int) field(screen, "l").get(screen);
        return constructor.newInstance(id, width / 2 - 100, 200, 200, 20, label);
    }

    private static Object createBackButton(Object screen) throws ReflectiveOperationException {
        Class<?> buttonType = Class.forName("bja", true, screen.getClass().getClassLoader());
        Constructor<?> constructor = buttonType.getConstructor(int.class, int.class, int.class, int.class, int.class, String.class);
        return constructor.newInstance(BACK_BUTTON, LEFT_MARGIN, BACK_BUTTON_TOP, BACK_BUTTON_WIDTH, BACK_BUTTON_HEIGHT, "\u2190 Back");
    }

    private static int id(Object button) throws ReflectiveOperationException {
        return field(button, "k").getInt(button);
    }

    private static Field field(Object object, String name) throws NoSuchFieldException {
        Class<?> type = object instanceof Class<?> clazz ? clazz : object.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ex) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Method method(Class<?> type, String name) throws NoSuchMethodException {
        return method(type, name, new Class<?>[0]);
    }

    private static Method method(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static void marker(String name) {
        try {
            Path directory = Path.of(System.getProperty("fv2j3.verification.dir", "build/fv2j3-test-runtime"));
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(name), "ok\n");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Failed to write Fv2j3 GUI verification marker '" + name + ".", ex);
        }
    }
}
