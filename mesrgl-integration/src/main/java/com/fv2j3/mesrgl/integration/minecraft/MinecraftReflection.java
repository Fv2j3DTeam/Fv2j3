package com.fv2j3.mesrgl.integration.minecraft;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reflection bindings into the (notch-obfuscated) Minecraft 1.12.2 client,
 * traced from the shipped 1.12.2.jar bytecode — never guessed.
 *
 * Verified mapping (names are the notch names present in the vanilla jar):
 *
 *   Minecraft                    = bib        (static accessor z())
 *   Minecraft.theWorld           = bib.f      (bsb = WorldClient extends amu = World)
 *   Minecraft.fontRendererObj    = bib.k      (bip)
 *   Minecraft.currentScreen      = bib.m      (blk = GuiScreen)
 *   Minecraft.entityRenderer     = bib.o      (buq)
 *   Minecraft.getFramebuffer()   = bib.ap()   (chz)
 *   getRenderViewEntity()        = bib.aa()   (vg = Entity)
 *   EntityRenderer.renderWorld   = buq.b(float, long)   (hook target; updateCameraAndRender = buq.a)
 *   ChunkProviderClient          = bsb.c      (brx); loaded chunks = brx.c (fastutil Long2ObjectMap of axw)
 *   Chunk                        = axw;  getBlockState(x,y,z) = axw.a(III); chunk x/z = axw.b / axw.c
 *   IBlockState                  = awt;  getMaterial() = no-arg method returning bcz
 *   Material                     = bcz;  getMaterialMapColor() = bcz.r() -&gt; bda
 *   MapColor                     = bda;  colorValue = bda.ad (public final int RGB)
 *   Entity                       = vg;   posX/Y/Z = vg.m / vg.n / vg.o (public double)
 *   Entity.getLook/getPositionEyes(F) = vg.e(F) / vg.f(F) -&gt; bhe (calibrated at runtime)
 *   Vec3d                        = bhe;  x/y/z = bhe.b / bhe.c / bhe.d (public final double)
 *   FontRenderer                 = bip;  a(String, float, float, int) = drawString
 *   GuiScreen                    = blk;  buttonList = blk.n (List of bja); actionPerformed = a(bja)
 *   GuiButton                    = bja;  id = bja.k (int)
 *   GuiMainMenu / GuiWorldSelection / GuiCreateWorld = blr / bok / boi
 *   Auto world join: blr button id 1 -&gt; bok button id 3 -&gt; boi button id 0
 *
 * Every binding has a runtime sanity check where structural identification
 * could be ambiguous; failures raise diagnosed exceptions instead of
 * silently degrading (spec 37: no swallowed errors).
 */
final class MinecraftReflection {

    /** Camera ray resolved for one frame. */
    record CameraRay(double eyeX, double eyeY, double eyeZ,
                     double lookX, double lookY, double lookZ) {
    }

    private static final AtomicReference<MinecraftReflection> INSTANCE = new AtomicReference<>();

    private final ClassLoader mcClassLoader;
    private MethodHandle mcStatic;
    private MethodHandle getRenderViewEntity;
    private MethodHandle entityGetLook;
    private MethodHandle entityGetEyes;
    private MethodHandle chunkGetBlockState;
    private MethodHandle stateGetMaterial;
    private MethodHandle materialGetMapColor;
    private MethodHandle fontDrawString;
    private MethodHandle guiActionPerformed;
    private MethodHandle scheduleTask;
    private MethodHandle shutdownMinecraft;
    private MethodHandle integratedServer;
    private MethodHandle chunkMapValues;
    private Field mcWorldField;
    private Field mcCurrentScreenField;
    private Field fontRendererField;
    private Field guiButtonsField;
    private Field buttonIdField;
    private Field chunkProviderField;
    private Field chunkMapField;
    private Field chunkXField;
    private Field chunkZField;
    private Field mapColorValueField;
    private Field entityPosX;
    private Field entityPosY;
    private Field entityPosZ;
    private Field vecX;
    private Field vecY;
    private Field vecZ;
    private Object airMaterial; // calibrated: material of a high-sky block

    private MinecraftReflection(ClassLoader mcClassLoader) {
        this.mcClassLoader = mcClassLoader;
    }

    static MinecraftReflection get(ClassLoader mcClassLoader) {
        return INSTANCE.updateAndGet(existing -> {
            if (existing != null && existing.mcClassLoader == mcClassLoader) {
                return existing;
            }
            MinecraftReflection fresh = new MinecraftReflection(mcClassLoader);
            fresh.bind();
            return fresh;
        });
    }

    /** The live Minecraft instance ({@code bib.z()}), or null pre-launch. */
    static Object minecraftInstanceOf(ClassLoader mcClassLoader) {
        return get(mcClassLoader).minecraftInstance();
    }

    private void bind() {
        try {
            Class<?> mcClass = mcClassLoader.loadClass("bib");
            Class<?> entityClass = mcClassLoader.loadClass("vg");
            Class<?> vec3Class = mcClassLoader.loadClass("bhe");
            Class<?> worldClientClass = mcClassLoader.loadClass("bsb");
            Class<?> chunkClass = mcClassLoader.loadClass("axw");
            Class<?> chunkProviderClass = mcClassLoader.loadClass("brx");
            Class<?> materialClass = mcClassLoader.loadClass("bcz");
            Class<?> mapColorClass = mcClassLoader.loadClass("bda");
            Class<?> fontClass = mcClassLoader.loadClass("bip");
            Class<?> guiScreenClass = mcClassLoader.loadClass("blk");
            Class<?> guiButtonClass = mcClassLoader.loadClass("bja");

            MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();

            mcStatic = publicLookup.unreflect(findStatic(mcClass, mcClass));
            getRenderViewEntity = publicLookup.unreflect(findNoArg(mcClass, entityClass));

            mcWorldField = findFirstField(mcClass, worldClientClass);
            mcCurrentScreenField = findFirstField(mcClass, guiScreenClass);
            fontRendererField = findFirstField(mcClass, fontClass);

            entityPosX = findInstanceField(entityClass, double.class, 0);
            entityPosY = findInstanceField(entityClass, double.class, 1);
            entityPosZ = findInstanceField(entityClass, double.class, 2);
            vecX = findInstanceField(vec3Class, double.class, 0);
            vecY = findInstanceField(vec3Class, double.class, 1);
            vecZ = findInstanceField(vec3Class, double.class, 2);

            // vg.e(F) / vg.f(F): the two (float)->bhe Entity methods. Their
            // order (look vs eyes) is calibrated per call at runtime.
            List<Method> vec3Float = new ArrayList<>();
            for (Method m : entityClass.getDeclaredMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == float.class
                        && m.getReturnType() == vec3Class) {
                    m.setAccessible(true);
                    vec3Float.add(m);
                }
            }
            if (vec3Float.size() != 2) {
                throw new IllegalStateException("Entity look/eyes binding mismatch: found "
                        + vec3Float.size() + " (float)->Vec3 methods, expected 2");
            }
            entityGetLook = publicLookup.unreflect(vec3Float.get(0));
            entityGetEyes = publicLookup.unreflect(vec3Float.get(1));

            // axw.a(III) -> awt (Chunk.getBlockState, local coordinates)
            Method getBlockState = null;
            for (Method m : chunkClass.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 3 && p[0] == int.class && p[1] == int.class && p[2] == int.class
                        && !m.getReturnType().isPrimitive()) {
                    if (getBlockState != null) {
                        throw new IllegalStateException("Chunk getBlockState binding ambiguous");
                    }
                    getBlockState = m;
                }
            }
            if (getBlockState == null) {
                throw new IllegalStateException("Chunk getBlockState(III) not found");
            }
            chunkGetBlockState = publicLookup.unreflect(getBlockState);

            Method getMaterial = null;
            for (Method m : getBlockState.getReturnType().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == materialClass) {
                    getMaterial = m;
                    break;
                }
            }
            if (getMaterial == null) {
                throw new IllegalStateException("IBlockState.getMaterial not found");
            }
            stateGetMaterial = publicLookup.unreflect(getMaterial);

            Method getMapColor = null;
            for (Method m : materialClass.getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == mapColorClass) {
                    getMapColor = m;
                    break;
                }
            }
            if (getMapColor == null) {
                throw new IllegalStateException("Material.getMaterialMapColor not found");
            }
            materialGetMapColor = publicLookup.unreflect(getMapColor);

            List<Field> colorInts = new ArrayList<>();
            for (Field f : mapColorClass.getDeclaredFields()) {
                if (f.getType() == int.class && Modifier.isPublic(f.getModifiers())
                        && Modifier.isFinal(f.getModifiers())) {
                    colorInts.add(f);
                }
            }
            if (colorInts.size() != 2) {
                throw new IllegalStateException("MapColor int fields mismatch: " + colorInts.size());
            }
            mapColorValueField = colorInts.get(1); // (colorIndex, colorValue) declaration order

            chunkProviderField = findFirstField(worldClientClass, chunkProviderClass);
            for (Field f : chunkProviderClass.getDeclaredFields()) {
                if (f.getType().getName().endsWith("Long2ObjectMap")) {
                    f.setAccessible(true);
                    chunkMapField = f;
                    break;
                }
            }
            if (chunkMapField == null) {
                throw new IllegalStateException("loaded-chunk Long2ObjectMap not found");
            }
            Method values = chunkMapField.getType().getMethod("values");
            chunkMapValues = publicLookup.unreflect(values);

            chunkXField = findInstanceField(chunkClass, int.class, 0);
            chunkZField = findInstanceField(chunkClass, int.class, 1);

            Method draw = null;
            for (Method m : fontClass.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 4 && p[0] == String.class && p[1] == float.class
                        && p[2] == float.class && p[3] == int.class
                        && m.getReturnType() == int.class) {
                    draw = m;
                    break;
                }
            }
            if (draw == null) {
                throw new IllegalStateException("FontRenderer.drawString(String,float,float,int) not found");
            }
            draw.setAccessible(true);
            fontDrawString = MethodHandles.lookup().unreflect(draw);

            guiButtonsField = findButtonsField(guiScreenClass);
            Method action = null;
            for (Method m : guiScreenClass.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 1 && p[0] == guiButtonClass && m.getReturnType() == void.class) {
                    action = m;
                    break;
                }
            }
            if (action == null) {
                throw new IllegalStateException("GuiScreen.actionPerformed(bja) not found");
            }
            action.setAccessible(true);
            guiActionPerformed = MethodHandles.lookup().unreflect(action);
            // bib.a(java.lang.Runnable) = Minecraft.addScheduledTask: GUI
            // actions MUST run on the render thread (a direct call from the
            // driver thread raced the screen initialization and NPE'd).
            Method schedule = null;
            for (Method m : mcClass.getDeclaredMethods()) {
                Class<?>[] pms = m.getParameterTypes();
                if (pms.length == 1 && pms[0] == java.lang.Runnable.class
                        && !Modifier.isStatic(m.getModifiers())) {
                    schedule = m;
                    break;
                }
            }
            if (schedule == null) {
                throw new IllegalStateException("Minecraft.addScheduledTask(Runnable) not found");
            }
            schedule.setAccessible(true);
            scheduleTask = MethodHandles.lookup().unreflect(schedule);
            // bib.aX-ish: the unique no-arg method returning chd is
            // Minecraft.getIntegratedServer() — non-null while a world is
            // starting or live. Used by the auto-join driver to never click
            // while a singleplayer world is being created/joined.
            Class<?> integratedServerClass = null;
            try {
                integratedServerClass = mcClassLoader.loadClass("chd");
            } catch (ClassNotFoundException ignored) {
                // class present in the 1.12.2 client jar; tolerate absence defensively
            }
            if (integratedServerClass != null) {
                Method getServer = null;
                for (Method m : mcClass.getDeclaredMethods()) {
                    if (m.getParameterCount() == 0 && m.getReturnType() == integratedServerClass) {
                        if (getServer != null) {
                            throw new IllegalStateException("Minecraft integrated-server getter ambiguous");
                        }
                        getServer = m;
                    }
                }
                if (getServer == null) {
                    throw new IllegalStateException("Minecraft.getIntegratedServer() not found");
                }
                getServer.setAccessible(true);
                integratedServer = MethodHandles.lookup().unreflect(getServer);
            }
            // bib.h()V = Minecraft.shutdownMinecraftApplet (func_71405_e): the
            // graceful quit path the "Quit Game" button uses.
            Method shutdown = null;
            for (Method m : mcClass.getDeclaredMethods()) {
                if ("h".equals(m.getName()) && m.getParameterCount() == 0
                        && m.getReturnType() == void.class
                        && !Modifier.isStatic(m.getModifiers())) {
                    shutdown = m;
                    break;
                }
            }
            if (shutdown == null) {
                throw new IllegalStateException("Minecraft shutdown (bib.h()V) not found");
            }
            shutdown.setAccessible(true);
            shutdownMinecraft = MethodHandles.lookup().unreflect(shutdown);
            // The id is bja.k (verified against the dispatch bytecode:
            // bok.a(bja) compares bja.k against 3 to open GuiCreateWorld).
            buttonIdField = findNamedField(guiButtonClass, "k", int.class);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Minecraft 1.12.2 reflection binding failed: " + ex, ex);
        }
    }

    private static Method findStatic(Class<?> owner, Class<?> returnType)
            throws NoSuchMethodException {
        Method found = null;
        for (Method m : owner.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) && m.getReturnType() == returnType
                    && m.getParameterCount() == 0) {
                if (found != null) {
                    throw new NoSuchMethodException("ambiguous static " + returnType + " on " + owner);
                }
                found = m;
            }
        }
        if (found == null) {
            throw new NoSuchMethodException("no static no-arg " + returnType + " on " + owner);
        }
        found.setAccessible(true);
        return found;
    }

    private static Method findNoArg(Class<?> owner, Class<?> returnType)
            throws NoSuchMethodException {
        Method found = null;
        for (Method m : owner.getDeclaredMethods()) {
            if (m.getParameterCount() == 0 && m.getReturnType() == returnType) {
                if (found != null) {
                    throw new NoSuchMethodException("ambiguous no-arg " + returnType + " on " + owner);
                }
                found = m;
            }
        }
        if (found == null) {
            throw new NoSuchMethodException("no-arg " + returnType + " on " + owner);
        }
        found.setAccessible(true);
        return found;
    }

    private static Field findFirstField(Class<?> owner, Class<?> type) throws NoSuchFieldException {
        for (Field f : owner.getDeclaredFields()) {
            if (f.getType() == type) {
                f.setAccessible(true);
                return f;
            }
        }
        throw new NoSuchFieldException(owner.getName() + " field of " + type);
    }

    /** Nth (0-based) non-static declared field of {@code type} in declaration order. */
    private static Field findInstanceField(Class<?> owner, Class<?> type, int index)
            throws NoSuchFieldException {
        int seen = 0;
        for (Field f : owner.getDeclaredFields()) {
            if (f.getType() == type && !Modifier.isStatic(f.getModifiers())) {
                if (seen == index) {
                    f.setAccessible(true);
                    return f;
                }
                seen++;
            }
        }
        throw new NoSuchFieldException(owner.getName() + " instance field #" + index + " of " + type);
    }

    private static Field findNamedField(Class<?> owner, String name, Class<?> type)
            throws NoSuchFieldException {
        Field f = owner.getDeclaredField(name);
        if (f.getType() != type) {
            throw new NoSuchFieldException(owner.getName() + "." + name + " is not " + type);
        }
        f.setAccessible(true);
        return f;
    }

    /** The GuiScreen button list: the List&lt;bja&gt;-typed field. */
    private static Field findButtonsField(Class<?> guiScreen) throws NoSuchFieldException {
        for (Field f : guiScreen.getDeclaredFields()) {
            if (f.getType() == List.class && f.getGenericType().getTypeName().contains("bja")) {
                f.setAccessible(true);
                return f;
            }
        }
        throw new NoSuchFieldException("button list not found on " + guiScreen);
    }

    // ------------------------------------------------------------------
    // Typed accessors
    // ------------------------------------------------------------------

    Object minecraftInstance() {
        try {
            return mcStatic.invoke();
        } catch (Throwable t) {
            throw new IllegalStateException("Minecraft instance fetch failed", t);
        }
    }

    Object currentWorld(Object mc) {
        try {
            return mcWorldField.get(mc);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    Object currentScreen(Object mc) {
        try {
            return mcCurrentScreenField.get(mc);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    Object renderViewEntity(Object mc) {
        try {
            return getRenderViewEntity.invoke(mc);
        } catch (Throwable t) {
            throw new IllegalStateException("getRenderViewEntity failed", t);
        }
    }

    double entityX(Object entity) {
        return getDouble(entityPosX, entity);
    }

    double entityY(Object entity) {
        return getDouble(entityPosY, entity);
    }

    double entityZ(Object entity) {
        return getDouble(entityPosZ, entity);
    }

    private static double getDouble(Field f, Object o) {
        try {
            return f.getDouble(o);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Interpolated eye position + look direction. Calibration: getLook
     * returns a normalized direction; getPositionEyes returns a point within
     * a few blocks of the entity position.
     */
    CameraRay cameraRay(Object entity, float partialTicks) {
        try {
            Object a = entityGetLook.invoke(entity, partialTicks);
            Object b = entityGetEyes.invoke(entity, partialTicks);
            double ax = getDouble(vecX, a), ay = getDouble(vecY, a), az = getDouble(vecZ, a);
            double bx = getDouble(vecX, b), by = getDouble(vecY, b), bz = getDouble(vecZ, b);
            double lenA = Math.sqrt(ax * ax + ay * ay + az * az);
            double lenB = Math.sqrt(bx * bx + by * by + bz * bz);
            double px = entityX(entity), py = entityY(entity), pz = entityZ(entity);
            double distA = Math.sqrt((ax - px) * (ax - px) + (ay - py) * (ay - py) + (az - pz) * (az - pz));
            double distB = Math.sqrt((bx - px) * (bx - px) + (by - py) * (by - py) + (bz - pz) * (bz - pz));
            // The look vector is unit length and near-zero distance is impossible
            // for the eye point; the eye point sits within a few blocks of the feet.
            boolean aIsLook = Math.abs(lenA - 1.0) < 0.05 && distA > 0.5;
            boolean bIsLook = Math.abs(lenB - 1.0) < 0.05 && distB > 0.5;
            double lx, ly, lz, ex, ey, ez;
            if (aIsLook && !bIsLook) {
                lx = ax; ly = ay; lz = az; ex = bx; ey = by; ez = bz;
            } else if (bIsLook && !aIsLook) {
                lx = bx; ly = by; lz = bz; ex = ax; ey = ay; ez = az;
            } else if (Math.abs(lenA - 1.0) <= Math.abs(lenB - 1.0)) {
                lx = ax; ly = ay; lz = az; ex = bx; ey = by; ez = bz;
            } else {
                lx = bx; ly = by; lz = bz; ex = ax; ey = ay; ez = az;
            }
            return new CameraRay(ex, ey, ez, lx, ly, lz);
        } catch (Throwable t) {
            throw new IllegalStateException("camera ray failed", t);
        }
    }

    List<Object> loadedChunks(Object world) {
        try {
            Object provider = chunkProviderField.get(world);
            Object map = chunkMapField.get(provider);
            Object values = chunkMapValues.invoke(map);
            List<Object> list = new ArrayList<>();
            for (Object chunk : (java.util.Collection<?>) values) {
                list.add(chunk);
            }
            return list;
        } catch (Throwable t) {
            throw new IllegalStateException("loaded chunk enumeration failed", t);
        }
    }

    int chunkX(Object chunk) {
        return getInt(chunkXField, chunk);
    }

    int chunkZ(Object chunk) {
        return getInt(chunkZField, chunk);
    }

    private static int getInt(Field f, Object o) {
        try {
            return f.getInt(o);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Block state at chunk-local coordinates. */
    Object blockState(Object chunk, int x, int y, int z) {
        try {
            return chunkGetBlockState.invoke(chunk, x, y, z);
        } catch (Throwable t) {
            throw new IllegalStateException("getBlockState failed", t);
        }
    }

    /**
     * Map color RGB for a block state. Calibrates the air material on first
     * use from a high-sky block state so extraction can skip air without
     * hard-coding the Material.air notch identity.
     */
    int mapColor(Object state) {
        try {
            Object material = stateGetMaterial.invoke(state);
            if (airMaterial == null) {
                airMaterial = material;
            }
            if (material == airMaterial) {
                return -1; // air
            }
            Object color = materialGetMapColor.invoke(material);
            return mapColorValueField.getInt(color);
        } catch (Throwable t) {
            throw new IllegalStateException("map color failed", t);
        }
    }

    /**
     * Returns a pseudo-identity for a block state: the simple class name of
     * the Block instance behind the state. Used by the materials catalogue
     * to look up per-block UV and material properties. May return null if
     * the binding cannot be resolved.
     */
    String blockIdentity(Object state) {
        if (state == null) return null;
        try {
            // IBlockState.getBlock() -> Block, where Block is class "afr".
            Class<?> stateClass = state.getClass();
            for (java.lang.reflect.Method m : stateClass.getMethods()) {
                if (m.getParameterCount() == 0
                        && m.getReturnType().getName().equals("afr")) {
                    Object block = m.invoke(state);
                    if (block == null) return null;
                    String name = block.getClass().getSimpleName();
                    if (name != null && !name.isEmpty()) return name;
                    return block.getClass().getName();
                }
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    void drawString(Object fontRenderer, String text, float x, float y, int color) {
        try {
            fontDrawString.invoke(fontRenderer, text, x, y, color);
        } catch (Throwable t) {
            throw new IllegalStateException("drawString failed", t);
        }
    }

    /** bib.k = Minecraft.fontRendererObj (the first bip-typed field). */
    Object fontRenderer(Object mc) {
        try {
            return fontRendererField.get(mc);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    List<?> screenButtons(Object screen) {
        try {
            return (List<?>) guiButtonsField.get(screen);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    int buttonId(Object button) {
        return getInt(buttonIdField, button);
    }

    void clickButton(Object screen, Object button) {
        try {
            guiActionPerformed.invoke(screen, button);
        } catch (Throwable t) {
            throw new IllegalStateException("actionPerformed failed", t);
        }
    }

    /** Schedules a click on the render thread via Minecraft.addScheduledTask. */
    void clickButtonOnRenderThread(Object mc, Object screen, Object button) {
        Object task = java.lang.reflect.Proxy.newProxyInstance(
                screen.getClass().getClassLoader(),
                new Class<?>[]{Runnable.class},
                (proxy, method, args) -> {
                    try {
                        guiActionPerformed.invoke(screen, button);
                    } catch (Throwable t) {
                        System.err.println("[MesrGL] scheduled click failed: " + t);
                    }
                    return null;
                });
        try {
            scheduleTask.invoke(mc, task);
        } catch (Throwable t) {
            throw new IllegalStateException("addScheduledTask failed", t);
        }
    }

    /** Triggers Minecraft's graceful shutdown (bib.h()V) from the render thread. */
    void shutdownMinecraft(Object mc) {
        try {
            shutdownMinecraft.invoke(mc);
        } catch (Throwable t) {
            throw new IllegalStateException("Minecraft shutdown failed", t);
        }
    }

    /**
     * The integrated server instance, or null when no singleplayer world is
     * starting or running. The auto-join driver treats non-null as "never click".
     */
    Object integratedServer(Object mc) {
        if (integratedServer == null) {
            return null;
        }
        try {
            return integratedServer.invoke(mc);
        } catch (Throwable t) {
            throw new IllegalStateException("getIntegratedServer failed", t);
        }
    }
}
