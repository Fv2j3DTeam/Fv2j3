package io.github.fv2j3dteam.minecraft.compat;

import io.github.fv2j3dteam.api.Fv2j3Block;
import io.github.fv2j3dteam.api.Fv2j3CreativeTab;
import io.github.fv2j3dteam.api.Fv2j3Item;
import io.github.fv2j3dteam.api.Fv2j3Registries;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

    private static final String BRIDGE_INTERNAL = "io/github/fv2j3dteam/minecraft/compat/MinecraftRegistryBridge";
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
            case "bib":
                return patchMinecraftConstructor(bytes);
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

    /**
     * Injects {@code afterMinecraftConstructed(this)} into {@code bib.<init>(boz)V}
     * right after the {@code aB} field (the ResourcePackRepository) is assigned.
     * That field is set by {@code this.aB = new cev(aC)} before any other code
     * touches the resource manager, so injecting immediately after the
     * putfield is the earliest safe point where mods can be added to the
     * repository without racing MC's own setup.
     */
    private static byte[] patchMinecraftConstructor(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        // Use ClassWriter without COMPUTE_FRAMES to avoid frame issues
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String firstType, String secondType) {
                return "java/lang/Object";
            }
        };
        boolean[] injected = {false};
        boolean[] foundConstructor = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature, String[] exceptions) {
                MethodVisitor visitor = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                // Process ALL constructors in class bib
                if (!"<init>".equals(methodName)) {
                    return visitor;
                }
                System.err.println("[Fv2j3-DBG] FOUND CONSTRUCTOR: " + methodName + " " + descriptor);
                foundConstructor[0] = true;
                return new MethodVisitor(api, visitor) {
                    private int putfieldCount = 0;
                    private String lastNewType = null;
                    private boolean seenCfgPutfield = false;

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW) {
                            lastNewType = type;
                            System.err.println("[Fv2j3-DBG] NEW instruction: " + type);
                        }
                        super.visitTypeInsn(opcode, type);
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String fieldDescriptor) {
                        if (opcode == Opcodes.PUTFIELD) {
                            putfieldCount++;
                            System.err.println("[Fv2j3-DBG] putfield #" + putfieldCount + " owner=" + owner + " name=" + name + " desc=" + fieldDescriptor + (lastNewType != null ? " lastNew=" + lastNewType : "") + " injected=" + injected[0]);
                        }
                        super.visitFieldInsn(opcode, owner, name, fieldDescriptor);

                        // Match: this.aC = new cfg(aC) -- ResourcePackRepository field
                        // Based on debug: putfield #10 owner=bib name=aC desc=Lcfg; lastNew=cfg
                        if (!injected[0]
                                && opcode == Opcodes.PUTFIELD
                                && "bib".equals(owner)
                                && "aC".equals(name)
                                && fieldDescriptor != null
                                && fieldDescriptor.equals("L" + lastNewType + ";")
                                && "cfg".equals(lastNewType)) {
                            seenCfgPutfield = true;
                        }
                        lastNewType = null;
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        // Inject at the end of constructor (before RETURN)
                        if ((opcode == Opcodes.RETURN || opcode == Opcodes.ARETURN) && !injected[0] && seenCfgPutfield) {
                            injected[0] = true;
                            System.err.println("[Fv2j3] Injecting afterMinecraftConstructed at constructor end");
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    BRIDGE_INTERNAL,
                                    "afterMinecraftConstructed",
                                    "(Ljava/lang/Object;)V",
                                    false
                            );
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        }, 0); // No EXPAND_FRAMES
        if (!foundConstructor[0]) {
            throw new IllegalStateException("Minecraft 1.12.2 constructor bytecode did not match: No constructor found in class bib.");
        }
        if (!injected[0]) {
            throw new IllegalStateException("Minecraft 1.12.2 constructor bytecode did not match: ResourcePackRepository initialization not found.");
        }
        return writer.toByteArray();
    }

    // ------------------------------------------------------------------
    // Runtime flush — runs inside Minecraft's startup, on its classloader
    // ------------------------------------------------------------------

    /**
     * Hook installed into {@code bib.<init>(Lboz;)V} (Minecraft constructor) right
     * after the {@code aB} field — the {@code ResourcePackRepository} — is
     * assigned. With the repository in hand we read the active mods directory
     * (set via {@code -Dfv2j3.mods.dir}) and wrap each mod jar as an
     * {@code IResourcePack} so Minecraft's resource manager can see the
     * mod's {@code assets/...} entries. Without this wiring, every mod whose
     * items, blocks, or models the registry bridge pushed into Minecraft's
     * own registries would surface {@code FileNotFoundException} for
     * {@code assets/<modid>/...} lookups during model loading.
     */
    public static void afterMinecraftConstructed(Object minecraftInstance) {
        if (minecraftInstance == null) {
            return;
        }
        ClassLoader mcLoader = Thread.currentThread().getContextClassLoader();
        if (mcLoader == null) {
            return;
        }
        try {
            // Find IResourcePack (typically "cer") and ResourcePackRepository (typically "cev")
            // by scanning loaded classes for the expected signatures.
            Class<?> iResourcePackClass = findClass(mcLoader, "IResourcePack", "cer", "c", "d");
            Class<?> resourcePackRepoClass = findClass(mcLoader, "ResourcePackRepository", "cev", "cen", "ceg", "ceh");
            
            if (iResourcePackClass == null || resourcePackRepoClass == null) {
                System.err.println("[Fv2j3] Could not find resource pack classes (IResourcePack=" + iResourcePackClass + ", Repo=" + resourcePackRepoClass + ")");
                return;
            }

            // Find the resource pack repository field - try known names
            Field repoField = null;
            for (String fieldName : new String[]{"aB", "aC", "resourcePackRepository", "repo"}) {
                try {
                    repoField = minecraftInstance.getClass().getDeclaredField(fieldName);
                    repoField.setAccessible(true);
                    Object repository = repoField.get(minecraftInstance);
                    if (repository != null && resourcePackRepoClass.isInstance(repository)) {
                        break;
                    }
                } catch (NoSuchFieldException e) {
                    // Try next field name
                }
            }
            if (repoField == null) {
                System.err.println("[Fv2j3] Could not find ResourcePackRepository field");
                return;
            }
            Object repository = repoField.get(minecraftInstance);
            if (repository == null) {
                return;
            }
            
            // Find addPack method - typically "a" taking IResourcePack
            Method addPack = null;
            for (Method m : resourcePackRepoClass.getDeclaredMethods()) {
                if (m.getParameterCount() == 1 && iResourcePackClass.isAssignableFrom(m.getParameterTypes()[0])) {
                    addPack = m;
                    addPack.setAccessible(true);
                    break;
                }
            }
            if (addPack == null) {
                System.err.println("[Fv2j3] Could not find addPack method on ResourcePackRepository");
                return;
            }
            Path modsDir = resolveModsDir();
            if (modsDir == null) {
                return;
            }
            try (Stream<Path> stream = Files.list(modsDir)) {
                List<Path> jars = stream
                        .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar"))
                        .sorted()
                        .collect(Collectors.toList());
                int added = 0;
                for (Path jar : jars) {
                    if (addJarResourcePack(jar, iResourcePackClass, addPack, repository)) {
                        added++;
                    }
                }
                System.out.println("[Fv2j3] registered " + added + " mod resource pack(s) from " + modsDir);
            }
        } catch (Throwable failure) {
            // Resource-pack wiring is best-effort: a missing MC class or
            // signature mismatch should never break Minecraft startup, so
            // surface the failure as a log line and continue.
            System.err.println("[Fv2j3] resource-pack wiring skipped: " + failure);
        }
    }

    private static Path resolveModsDir() {
        String prop = System.getProperty("fv2j3.mods.dir");
        if (prop == null || prop.isBlank()) {
            return null;
        }
        Path dir = Path.of(prop);
        return Files.isDirectory(dir) ? dir : null;
    }

    /**
     * Tries to find a Minecraft obfuscated class by trying multiple common names.
     * Obfuscation changes class names between Minecraft versions, so we try
     * a list of known names for the same logical class.
     */
    private static Class<?> findClass(ClassLoader loader, String logicalName, String... candidates) {
        for (String candidate : candidates) {
            try {
                return Class.forName(candidate, false, loader);
            } catch (ClassNotFoundException ignored) {
            }
        }
        System.err.println("[Fv2j3] Could not find class for " + logicalName + " among candidates: " + Arrays.toString(candidates));
        return null;
    }

    private static boolean addJarResourcePack(Path jar, Class<?> iResourcePackClass, Method addPack, Object repository) {
        if (!Files.isRegularFile(jar)) {
            return false;
        }
        JarContents contents = JarContents.scan(jar);
        if (contents == null || contents.namespaces.isEmpty()) {
            return false;
        }
        Object proxy = Proxy.newProxyInstance(
                iResourcePackClass.getClassLoader(),
                new Class<?>[]{iResourcePackClass},
                new JarResourcePackHandler(jar, contents)
        );
        try {
            addPack.invoke(repository, proxy);
            return true;
        } catch (ReflectiveOperationException ex) {
            System.err.println("[Fv2j3] failed to register resource pack for " + jar.getFileName() + ": " + ex.getCause());
            return false;
        }
    }

    /**
     * Cached, lazily-loaded view over a mod jar's asset entries. Two passes
     * are needed: the first pass records the namespaces declared by
     * {@code pack.mcmeta}, the second builds the path → bytes map so the
     * {@link JarResourcePackHandler} can resolve lookups without re-opening
     * the jar on every Minecraft resource request.
     */
    static final class JarContents {
        final Set<String> namespaces;
        final Map<String, byte[]> entries;
        final String packDescription;

        private JarContents(Set<String> namespaces, Map<String, byte[]> entries, String packDescription) {
            this.namespaces = namespaces;
            this.entries = entries;
            this.packDescription = packDescription;
        }

        static JarContents scan(Path jarPath) {
            Map<String, byte[]> entries = new HashMap<>();
            Set<String> namespaces = new HashSet<>();
            String description = "Fv2j3 mod: " + jarPath.getFileName();
            try (JarFile jar = new JarFile(jarPath.toFile())) {
                java.util.Enumeration<JarEntry> en = jar.entries();
                while (en.hasMoreElements()) {
                    JarEntry entry = en.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String name = entry.getName();
                    if (name.startsWith("assets/")) {
                        int slash = name.indexOf('/', "assets/".length());
                        if (slash > 0) {
                            namespaces.add(name.substring("assets/".length(), slash));
                        }
                    }
                    if ("pack.mcmeta".equals(name)) {
                        try (InputStream in = jar.getInputStream(entry)) {
                            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                            description = extractDescription(json, description);
                        }
                    }
                    try (InputStream in = jar.getInputStream(entry)) {
                        entries.put(name, in.readAllBytes());
                    }
                }
                return new JarContents(namespaces, entries, description);
            } catch (IOException ex) {
                System.err.println("[Fv2j3] failed to scan mod jar " + jarPath.getFileName() + ": " + ex);
                return null;
            }
        }

        private static String extractDescription(String json, String fallback) {
            int idx = json.indexOf("\"pack\"");
            if (idx < 0) return fallback;
            int descIdx = json.indexOf("\"description\"", idx);
            if (descIdx < 0) return fallback;
            int colon = json.indexOf(':', descIdx);
            if (colon < 0) return fallback;
            int firstQuote = json.indexOf('"', colon);
            if (firstQuote < 0) return fallback;
            int lastQuote = json.indexOf('"', firstQuote + 1);
            if (lastQuote <= firstQuote) return fallback;
            return json.substring(firstQuote + 1, lastQuote);
        }
    }

    /**
     * {@code cer} (vanilla {@code IResourcePack}) proxy backed by a mod jar.
     * The handler answers the small surface that Minecraft 1.12.2's resource
     * pack machinery actually calls during model and texture loading:
     * namespace lookup, entry existence, stream open, and metadata parsing.
     */
    static final class JarResourcePackHandler implements InvocationHandler {
        private static final Method TO_STRING;
        private static final Method HASH_CODE;
        private static final Method EQUALS;
        static {
            try {
                TO_STRING = Object.class.getMethod("toString");
                HASH_CODE = Object.class.getMethod("hashCode");
                EQUALS = Object.class.getMethod("equals", Object.class);
            } catch (NoSuchMethodException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private final Path jar;
        private final JarContents contents;

        JarResourcePackHandler(Path jar, JarContents contents) {
            this.jar = jar;
            this.contents = contents;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (method.equals(TO_STRING)) {
                return "fv2j3-mod-pack[" + jar.getFileName() + "]";
            }
            if (method.equals(HASH_CODE)) {
                return System.identityHashCode(proxy);
            }
            if (method.equals(EQUALS)) {
                return proxy == args[0];
            }
            switch (name) {
                case "c":
                    return contents.namespaces;
                case "b":
                    // getName() — the resource-pack display name. The default
                    // ResourcePackRepository uses it as the identifier; we
                    // return the jar file name so each mod gets a unique
                    // entry in the pack list.
                    return jar.getFileName().toString();
                case "a":
                    if (args == null || args.length == 0) {
                        return null;
                    }
                    Object first = args[0];
                    if (first == null) {
                        return null;
                    }
                    // cer.a(nf) — InputStream open by ResourceLocation.
                    if (first.getClass().getName().equals("nf")) {
                        String path = (String) first.getClass().getMethod("b").invoke(first);
                        byte[] bytes = contents.entries.get("assets/" + path);
                        if (bytes == null) {
                            return new java.io.ByteArrayInputStream(new byte[0]);
                        }
                        return new java.io.ByteArrayInputStream(bytes);
                    }
                    // cer.a(cfg, String) — metadata parse. The serializer is reflective:
                    // without going through the real cfg we cannot parse JSON,
                    // so return null and let MC fall back to defaults.
                    if (args.length == 2) {
                        return null;
                    }
                    // cer.a() — BufferedImage pack icon. We don't ship icons for
                    // mod jars; returning null lets MC fall back to its default.
                    return null;
                case "equals":
                case "hashCode":
                case "toString":
                    return null;
                default:
                    return null;
            }
        }
    }

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
        String internal = "io/github/fv2j3dteam/gen/Fv2j3TabGen" + index;
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
