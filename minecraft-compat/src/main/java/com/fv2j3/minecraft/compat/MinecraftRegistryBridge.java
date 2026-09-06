package com.fv2j3.minecraft.compat;

import com.fv2j3.api.Fv2j3Block;
import com.fv2j3.api.Fv2j3CreativeTab;
import com.fv2j3.api.Fv2j3Item;
import com.fv2j3.api.Fv2j3Registries;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phase 47 — real Minecraft registry bridge.
 *
 * The loader (Fv2j3Registries) records every mod's items, blocks and
 * creative tabs, but that alone proves nothing about the running game.
 * This bridge pushes that content into the REAL Minecraft 1.12.2
 * registries while the game JVM is still initializing, and then reads
 * the objects back out of Minecraft's own registry objects to prove
 * they exist inside Minecraft (not just inside the loader).
 *
 * Injection points (both verified against the vanilla 1.12.2 jar):
 *
 *   - {@code ni.c()V} is {@code Bootstrap.register()}, the method that
 *     registers all vanilla blocks/items/sounds. The bridge flushes at
 *     its end, so vanilla content exists first and mod content is
 *     registered exactly where vanilla registers its own.
 *
 *   - {@code ahp.<init>(int,String)} is {@code CreativeTabs(int index,
 *     String label)}. Vanilla stores the tab with
 *     {@code CREATIVE_TAB_ARRAY[index] = this} and does NOT grow the
 *     array, so any index past 11 would throw. The transformer grows the
 *     array first (the same expansion Forge applies), which is what lets
 *     210 stress tabs register beside the 12 vanilla ones.
 *
 * Mapping reference (from MCP 1.12.2 joined.srg, notch → human):
 *   ain=Item (REGISTRY field g), aow=Block (REGISTRY field h),
 *   fh=RegistryNamespaced (a(I,Object,Object)V=register, a(I)=getObjectById,
 *   c(Object)=getObject, d(Object)=containsKey, a(Object)I=getIDForObject),
 *   ey=RegistryNamespacedDefaultedByKey, fo=RegistrySimple,
 *   nf=ResourceLocation, aip=ItemStack, ahp=CreativeTabs,
 *   awt=IBlockState, bcz=Material, bda=MapColor.
 *
 * Only vanilla registration paths are used: the same
 * {@code RegistryNamespaced.register(id, ResourceLocation, object)} call
 * Bootstrap itself makes. No Forge, no coremod, no hardware APIs.
 */
public final class MinecraftRegistryBridge {

    private static final String BRIDGE_INTERNAL = "com/fv2j3/minecraft/compat/MinecraftRegistryBridge";
    private static final int FIRST_MOD_ITEM_ID = 10_000;
    private static final int FIRST_MOD_BLOCK_ID = 500;
    private static final int FIRST_MOD_TAB_INDEX = 12;

    /** Set by the bootstrap before Minecraft's main class is invoked. */
    private static volatile Definer definer;
    private static final AtomicBoolean flushed = new AtomicBoolean(false);
    /** Last completed flush, kept for tests and diagnostics. */
    static volatile FlushResult lastResult;

    /** Defines a generated class inside the Minecraft classloader. */
    public interface Definer {
        Class<?> define(String name, byte[] bytes);
    }

    public static void attachClassLoader(Definer definer) {
        MinecraftRegistryBridge.definer = definer;
    }

    // ------------------------------------------------------------------
    // ASM entry point
    // ------------------------------------------------------------------

    public static byte[] transform(String name, byte[] bytes) {
        if (bytes == null) {
            return bytes;
        }
        switch (name) {
            case "ni":
                return patchBootstrapRegister(bytes);
            case "ahp":
                return patchCreativeTabsConstructor(bytes);
            default:
                return bytes;
        }
    }

    /**
     * Injects flush(bootstrapClass) before every RETURN of ni.c()V
     * (Bootstrap.register). Vanilla runs its own block/item registration
     * inside that method, so the flush must happen after it, never before.
     * The flush itself is guarded once-per-process, and Bootstrap.register
     * also has multiple return paths (its own already-registered guard),
     * which is why every RETURN gets the call.
     */
    private static byte[] patchBootstrapRegister(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String firstType, String secondType) {
                return "java/lang/Object";
            }
        };
        boolean[] found = new boolean[1];
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature, String[] exceptions) {
                MethodVisitor visitor = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                if ("c".equals(methodName) && "()V".equals(descriptor)) {
                    found[0] = true;
                    return new MethodVisitor(api, visitor) {
                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN) {
                                super.visitLdcInsn(Type.getObjectType("ni"));
                                super.visitMethodInsn(
                                        Opcodes.INVOKESTATIC,
                                        BRIDGE_INTERNAL,
                                        "flush",
                                        "(Ljava/lang/Object;)V",
                                        false
                                );
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
                return visitor;
            }
        }, ClassReader.EXPAND_FRAMES);
        if (!found[0]) {
            throw new IllegalStateException("Minecraft 1.12.2 Bootstrap bytecode did not match: register method 'c()V' not found.");
        }
        return writer.toByteArray();
    }

    /**
     * Grows CREATIVE_TAB_ARRAY at the head of ahp.<init>(int,String) when
     * the requested index does not fit:
     *
     *   if (index < CREATIVE_TAB_ARRAY.length) goto continue;
     *   CREATIVE_TAB_ARRAY = Arrays.copyOf(CREATIVE_TAB_ARRAY, index + 1);
     *   continue:
     */
    private static byte[] patchCreativeTabsConstructor(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String firstType, String secondType) {
                return "java/lang/Object";
            }
        };
        boolean[] found = new boolean[1];
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature, String[] exceptions) {
                MethodVisitor visitor = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                if ("<init>".equals(methodName) && "(ILjava/lang/String;)V".equals(descriptor)) {
                    found[0] = true;
                    return new MethodVisitor(api, visitor) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            // local 3 = CREATIVE_TAB_ARRAY (slots 0..2 are this/index/label)
                            super.visitFieldInsn(Opcodes.GETSTATIC, "ahp", "a", "[Lahp;");
                            super.visitVarInsn(Opcodes.ASTORE, 3);
                            super.visitVarInsn(Opcodes.ILOAD, 1);
                            super.visitVarInsn(Opcodes.ALOAD, 3);
                            super.visitInsn(Opcodes.ARRAYLENGTH);
                            Label grown = new Label();
                            super.visitJumpInsn(Opcodes.IF_ICMPLT, grown);
                            super.visitFieldInsn(Opcodes.GETSTATIC, "ahp", "a", "[Lahp;");
                            super.visitVarInsn(Opcodes.ILOAD, 1);
                            super.visitInsn(Opcodes.ICONST_1);
                            super.visitInsn(Opcodes.IADD);
                            super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "java/util/Arrays",
                                    "copyOf",
                                    "([Ljava/lang/Object;I)[Ljava/lang/Object;",
                                    false
                            );
                            super.visitTypeInsn(Opcodes.CHECKCAST, "[Lahp;");
                            super.visitFieldInsn(Opcodes.PUTSTATIC, "ahp", "a", "[Lahp;");
                            super.visitLabel(grown);
                        }
                    };
                }
                return visitor;
            }
        }, ClassReader.EXPAND_FRAMES);
        if (!found[0]) {
            throw new IllegalStateException("Minecraft 1.12.2 CreativeTabs bytecode did not match: <init>(int,String) not found.");
        }
        return writer.toByteArray();
    }

    // ------------------------------------------------------------------
    // Runtime flush — runs inside Minecraft's startup, on its classloader
    // ------------------------------------------------------------------

    public static void flush(Object bootstrapClass) {
        if (!flushed.compareAndSet(false, true)) {
            return;
        }
        ClassLoader mcLoader = bootstrapClass == null
                ? Thread.currentThread().getContextClassLoader()
                : ((Class<?>) bootstrapClass).getClassLoader();
        FlushResult result;
        try {
            result = registerAll(mcLoader);
        } catch (Throwable failure) {
            failure.printStackTrace();
            throw new IllegalStateException("Fv2j3 registry bridge failed: " + failure.getMessage(), failure);
        }
        lastResult = result;
        writeVerification(result);
    }

    static FlushResult registerAll(ClassLoader mcLoader) throws Exception {
        FlushResult result = new FlushResult();

        Map<String, Fv2j3Item> items = Fv2j3Registries.getAllItems();
        Map<String, Fv2j3Block> blocks = Fv2j3Registries.getAllBlocks();
        Map<String, Fv2j3CreativeTab> tabs = Fv2j3Registries.getAllCreativeTabs();
        result.loaderItems = items.size();
        result.loaderBlocks = blocks.size();
        result.loaderTabs = tabs.size();

        Class<?> itemClass = Class.forName("ain", true, mcLoader);
        Class<?> blockClass = Class.forName("aow", true, mcLoader);
        Class<?> registryClass = Class.forName("fh", true, mcLoader);
        Class<?> resourceLocationClass = Class.forName("nf", true, mcLoader);
        Class<?> itemStackClass = Class.forName("aip", true, mcLoader);
        Class<?> creativeTabClass = Class.forName("ahp", true, mcLoader);
        Class<?> stateClass = Class.forName("awt", true, mcLoader);
        Class<?> materialClass = Class.forName("bcz", true, mcLoader);
        Class<?> mapColorClass = Class.forName("bda", true, mcLoader);

        Object itemRegistry = staticField(itemClass, "g");
        Object blockRegistry = staticField(blockClass, "h");
        Method registerItem = registryClass.getMethod("a", int.class, Object.class, Object.class);
        Method getObjectById = registryClass.getMethod("a", int.class);
        Method getObject = registryClass.getMethod("c", Object.class);
        Method containsKey = registryClass.getMethod("d", Object.class);
        Method idForObject = registryClass.getMethod("a", Object.class);
        Constructor<?> resourceLocation = resourceLocationClass.getConstructor(String.class, String.class);
        Constructor<?> itemStackFromItem = itemStackClass.getConstructor(itemClass);
        Method setCreativeTab = null;
        for (Method m : itemClass.getMethods()) {
            if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == creativeTabClass) {
                setCreativeTab = m;
            }
        }
        if (setCreativeTab == null) {
            throw new IllegalStateException("Item.setCreativeTab(CreativeTabs) not found in vanilla jar.");
        }

        // Material + MapColor resolved from vanilla stone, so no static
        // Material field names are guessed.
        Object stoneBlock = getObjectById.invoke(blockRegistry, 1);
        Object material = null;
        Object mapColor = null;
        if (stoneBlock != null) {
            Field stateField = null;
            for (Field f : blockClass.getDeclaredFields()) {
                if (stateClass.equals(f.getType())) {
                    f.setAccessible(true);
                    stateField = f;
                    break;
                }
            }
            Method getMaterial = null;
            for (Method m : blockClass.getMethods()) {
                if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == stateClass
                        && m.getReturnType() == materialClass) {
                    getMaterial = m;
                }
            }
            if (stateField != null && getMaterial != null) {
                material = getMaterial.invoke(stoneBlock, stateField.get(stoneBlock));
            }
        }
        if (material == null) {
            throw new IllegalStateException("Could not resolve a vanilla Material instance from stone.");
        }
        for (Field f : material.getClass().getDeclaredFields()) {
            if (mapColorClass.equals(f.getType()) && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                mapColor = f.get(material);
                break;
            }
        }
        Constructor<?> blockConstructor = blockClass.getConstructor(materialClass, mapColorClass);

        // Group content by namespace so each mod gets one creative tab.
        Set<String> namespaces = new LinkedHashSet<>();
        items.keySet().forEach(k -> namespaces.add(namespace(k)));
        blocks.keySet().forEach(k -> namespaces.add(namespace(k)));
        tabs.keySet().forEach(k -> namespaces.add(namespace(k)));

        // Items are created first (without a tab) so that each mod's creative
        // tab icon can be a real ItemStack of that mod's first item.
        Map<String, Object> itemsByNamespace = new LinkedHashMap<>();
        int itemId = FIRST_MOD_ITEM_ID;
        for (Map.Entry<String, Fv2j3Item> entry : items.entrySet()) {
            String key = entry.getKey();
            String ns = namespace(key);
            String id = simpleName(key);
            Object rl = resourceLocation.newInstance(ns, id);
            if (Boolean.TRUE.equals(containsKey.invoke(itemRegistry, rl))) {
                result.itemsSkipped++;
                continue;
            }
            Object mcItem = itemClass.getConstructor().newInstance();
            registerItem.invoke(itemRegistry, itemId, rl, mcItem);
            itemsByNamespace.put(key, mcItem);
            result.itemsRegistered++;
            if (result.itemIdSamples.size() < 8) {
                Object probed = getObject.invoke(itemRegistry, rl);
                int numericId = (Integer) idForObject.invoke(itemRegistry, probed);
                result.itemIdSamples.put(key, numericId);
            }
            itemId++;
        }

        int blockId = FIRST_MOD_BLOCK_ID;
        for (Map.Entry<String, Fv2j3Block> entry : blocks.entrySet()) {
            String key = entry.getKey();
            String ns = namespace(key);
            String id = simpleName(key);
            Object rl = resourceLocation.newInstance(ns, id);
            if (Boolean.TRUE.equals(containsKey.invoke(blockRegistry, rl))) {
                result.blocksSkipped++;
                continue;
            }
            Object mcBlock = blockConstructor.newInstance(material, mapColor);
            registerItem.invoke(blockRegistry, blockId, rl, mcBlock);
            result.blocksRegistered++;
            if (result.blockIdSamples.size() < 8) {
                Object probed = getObject.invoke(blockRegistry, rl);
                int numericId = (Integer) idForObject.invoke(blockRegistry, probed);
                result.blockIdSamples.put(key, numericId);
            }
            blockId++;
        }

        // One real CreativeTabs per namespace, icon = first mod item.
        Map<String, Object> tabByNamespace = new LinkedHashMap<>();
        int tabIndex = FIRST_MOD_TAB_INDEX;
        for (String ns : namespaces) {
            Object icon = firstItemStackFor(ns, items, itemsByNamespace, itemStackFromItem, itemClass, itemRegistry);
            Object tab = createCreativeTab(ns, tabIndex, icon, mcLoader, creativeTabClass);
            if (tab != null) {
                tabByNamespace.put(ns, tab);
                result.tabsCreated++;
            }
            tabIndex++;
        }
        for (Map.Entry<String, Object> entry : itemsByNamespace.entrySet()) {
            Object tab = tabByNamespace.get(namespace(entry.getKey()));
            if (tab != null) {
                setCreativeTab.invoke(entry.getValue(), tab);
            }
        }

        // Read-back verification against Minecraft's own registry objects.
        result.verifiedItems = countProbes(itemRegistry, getObject, items.keySet(), result.itemProbeFailures);
        result.verifiedBlocks = countProbes(blockRegistry, getObject, blocks.keySet(), result.blockProbeFailures);

        // Creative tab verification: read the real static array.
        Object tabArray = staticField(creativeTabClass, "a");
        int arrayLength = java.lang.reflect.Array.getLength(tabArray);
        int nonNull = 0;
        for (int i = 0; i < arrayLength; i++) {
            if (java.lang.reflect.Array.get(tabArray, i) != null) {
                nonNull++;
            }
        }
        result.tabArrayLength = arrayLength;
        result.tabArrayNonNull = nonNull;
        result.tabLabels.addAll(readTabLabels(tabArray, creativeTabClass, 24));

        result.side = String.valueOf(System.getProperty("fv2j3.launch.target", "unknown"));
        return result;
    }

    /**
     * Creates one real CreativeTabs instance for a mod namespace. CreativeTabs
     * is abstract (getTabIconItem), so a tiny subclass is generated into the
     * Minecraft classloader and its ICON static is filled with an ItemStack.
     */
    private static Object createCreativeTab(String ns, int index, Object icon, ClassLoader mcLoader,
                                            Class<?> creativeTabClass) throws Exception {
        Definer local = definer;
        if (local == null) {
            return null;
        }
        String internal = "com/fv2j3/gen/Fv2j3TabGen" + index;
        byte[] bytes = generateTabClass(internal, "ahp", "Laip;");
        Class<?> tabClass = local.define(internal.replace('/', '.'), bytes);
        tabClass.getField("ICON").set(null, icon);
        return tabClass.getConstructor(int.class, String.class).newInstance(index, ns);
    }

    private static byte[] generateTabClass(String internalName, String superInternal, String iconDescriptor) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(org.objectweb.asm.Opcodes.V1_8,
                org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_SUPER | org.objectweb.asm.Opcodes.ACC_FINAL,
                internalName, null, superInternal, null);
        cw.visitField(org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_STATIC,
                "ICON", iconDescriptor, null, null).visitEnd();
        MethodVisitor ctor = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "<init>", "(ILjava/lang/String;)V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 0);
        ctor.visitVarInsn(org.objectweb.asm.Opcodes.ILOAD, 1);
        ctor.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 2);
        ctor.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESPECIAL, superInternal, "<init>", "(ILjava/lang/String;)V", false);
        ctor.visitInsn(org.objectweb.asm.Opcodes.RETURN);
        ctor.visitMaxs(3, 3);
        ctor.visitEnd();
        MethodVisitor icon = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC, "e", "()" + iconDescriptor, null, null);
        icon.visitCode();
        icon.visitFieldInsn(org.objectweb.asm.Opcodes.GETSTATIC, internalName, "ICON", iconDescriptor);
        icon.visitInsn(org.objectweb.asm.Opcodes.ARETURN);
        icon.visitMaxs(1, 1);
        icon.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static Object firstItemStackFor(String ns, Map<String, Fv2j3Item> items,
                                            Map<String, Object> itemsByNamespace,
                                            Constructor<?> itemStackFromItem,
                                            Class<?> itemClass,
                                            Object itemRegistry) throws Exception {
        for (String key : items.keySet()) {
            if (namespace(key).equals(ns) && itemsByNamespace.containsKey(key)) {
                return itemStackFromItem.newInstance(itemsByNamespace.get(key));
            }
        }
        Object vanilla = getItemById(itemRegistry, 1);
        return itemStackFromItem.newInstance(vanilla != null ? vanilla : itemClass.getConstructor().newInstance());
    }

    private static Object getItemById(Object registry, int id) throws Exception {
        Method byId = registry.getClass().getMethod("a", int.class);
        return byId.invoke(registry, id);
    }

    private static int countProbes(Object registry, Method getObject, Set<String> keys, List<String> failures) throws Exception {
        int ok = 0;
        List<String> sorted = new ArrayList<>(keys);
        sorted.sort(Comparator.naturalOrder());
        for (String key : sorted) {
            int idx = key.indexOf(':');
            Object rl = probeResourceLocation(registry, key.substring(0, idx), key.substring(idx + 1));
            try {
                if (getObject.invoke(registry, rl) != null) {
                    ok++;
                } else {
                    failures.add(key);
                }
            } catch (Exception ex) {
                failures.add(key + " (" + ex.getMessage() + ")");
            }
        }
        return ok;
    }

    private static Object probeResourceLocation(Object registry, String ns, String id) throws Exception {
        Class<?> rlClass = Class.forName("nf", true, registry.getClass().getClassLoader());
        return rlClass.getConstructor(String.class, String.class).newInstance(ns, id);
    }

    private static List<String> readTabLabels(Object tabArray, Class<?> tabClass, int limit) throws Exception {
        Method label = null;
        for (Method m : tabClass.getMethods()) {
            if (m.getParameterCount() == 0 && m.getReturnType() == String.class && "b".equals(m.getName())) {
                label = m;
                break;
            }
        }
        List<String> out = new ArrayList<>();
        if (label == null) {
            return out;
        }
        int length = java.lang.reflect.Array.getLength(tabArray);
        for (int i = 0; i < length && out.size() < limit; i++) {
            Object tab = java.lang.reflect.Array.get(tabArray, i);
            if (tab != null) {
                out.add(String.valueOf(label.invoke(tab)));
            }
        }
        return out;
    }

    private static Object staticField(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static String namespace(String key) {
        int idx = key.indexOf(':');
        return idx < 0 ? "fv2j3" : key.substring(0, idx);
    }

    private static String simpleName(String key) {
        int idx = key.indexOf(':');
        return idx < 0 ? key : key.substring(idx + 1);
    }

    // ------------------------------------------------------------------
    // Verification report — proves the content exists in Minecraft itself
    // ------------------------------------------------------------------

    private static void writeVerification(FlushResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"side\": \"").append(result.side).append("\",\n");
        json.append("  \"loaderItems\": ").append(result.loaderItems).append(",\n");
        json.append("  \"loaderBlocks\": ").append(result.loaderBlocks).append(",\n");
        json.append("  \"loaderTabs\": ").append(result.loaderTabs).append(",\n");
        json.append("  \"itemsRegisteredInMinecraft\": ").append(result.itemsRegistered).append(",\n");
        json.append("  \"blocksRegisteredInMinecraft\": ").append(result.blocksRegistered).append(",\n");
        json.append("  \"tabsCreatedInMinecraft\": ").append(result.tabsCreated).append(",\n");
        json.append("  \"verifiedItemsInMinecraftRegistry\": ").append(result.verifiedItems).append(",\n");
        json.append("  \"verifiedBlocksInMinecraftRegistry\": ").append(result.verifiedBlocks).append(",\n");
        json.append("  \"tabArrayLength\": ").append(result.tabArrayLength).append(",\n");
        json.append("  \"tabArrayNonNull\": ").append(result.tabArrayNonNull).append(",\n");
        json.append("  \"itemProbeFailures\": ").append(result.itemProbeFailures.size()).append(",\n");
        json.append("  \"blockProbeFailures\": ").append(result.blockProbeFailures.size()).append(",\n");
        json.append("  \"itemIds\": ").append(jsonMap(result.itemIdSamples)).append(",\n");
        json.append("  \"blockIds\": ").append(jsonMap(result.blockIdSamples)).append(",\n");
        json.append("  \"tabLabels\": ").append(jsonList(result.tabLabels)).append("\n");
        json.append("}\n");

        String dir = System.getProperty("fv2j3.verification.dir");
        Path target;
        if (dir != null && !dir.isBlank()) {
            target = Path.of(dir, "minecraft-registry.json");
        } else {
            target = Path.of("fv2j3-verification", "minecraft-registry.json");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write the Minecraft registry verification file.", ex);
        }
        System.out.println("[Fv2j3] Minecraft registry bridge: items=" + result.itemsRegistered
                + " blocks=" + result.blocksRegistered
                + " tabs=" + result.tabsCreated
                + " verifiedItems=" + result.verifiedItems
                + " verifiedBlocks=" + result.verifiedBlocks
                + " tabArray=" + result.tabArrayNonNull + "/" + result.tabArrayLength
                + " report=" + target);
    }

    private static String jsonMap(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append('"').append(e.getKey()).append("\": ").append(e.getValue());
        }
        return sb.append("}").toString();
    }

    private static String jsonList(List<String> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('"').append(list.get(i)).append('"');
        }
        return sb.append("]").toString();
    }

    static final class FlushResult {
        String side;
        int loaderItems;
        int loaderBlocks;
        int loaderTabs;
        int itemsRegistered;
        int blocksRegistered;
        int itemsSkipped;
        int blocksSkipped;
        int tabsCreated;
        int verifiedItems;
        int verifiedBlocks;
        int tabArrayLength;
        int tabArrayNonNull;
        final List<String> itemProbeFailures = new ArrayList<>();
        final List<String> blockProbeFailures = new ArrayList<>();
        final Map<String, Integer> itemIdSamples = new LinkedHashMap<>();
        final Map<String, Integer> blockIdSamples = new LinkedHashMap<>();
        final List<String> tabLabels = new ArrayList<>();
    }
}
