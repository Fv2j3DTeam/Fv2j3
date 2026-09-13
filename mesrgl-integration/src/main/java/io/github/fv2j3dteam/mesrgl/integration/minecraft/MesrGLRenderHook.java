package io.github.fv2j3dteam.mesrgl.integration.minecraft;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * The Minecraft 1.12.2 render hook: transforms the vanilla EntityRenderer
 * ({@code buq}) so that {@code renderWorld(float partialTicks, long
 * finishTimeNano)} ({@code buq.b(FJ)V}) first consults MesrGL.
 *
 * Call chain traced from the vanilla 1.12.2 jar bytecode (notch names):
 *
 *   Minecraft.runGameLoop (bib)
 *     -&gt; EntityRenderer.updateCameraAndRender (buq.a(FJ)V)
 *          -&gt; EntityRenderer.renderWorld (buq.b(FJ)V)   &lt;- injected hook head
 *
 * When the injected call returns true, the vanilla body is skipped entirely:
 * the frame was produced by MesrGL and presented into the bound framebuffer.
 * When it returns false (renderer unavailable, world not ready, failure),
 * the original body executes and Minecraft renders as before - an explicit,
 * logged fallback, never a silent one.
 *
 * The transformer follows the exact mechanism of the Fv2j3 mod-menu
 * transformer: name+descriptor method matching, COMPUTE_FRAMES ClassWriter
 * with a non-loading super-class resolver, and INVOKESTATIC callbacks into
 * this class, which lives on the parent classloader.
 */
public final class MesrGLRenderHook {

    /** EntityRenderer class in the 1.12.2 vanilla jar. */
    static final String ENTITY_RENDERER = "buq";
    /** renderWorld method: name in the class, descriptor (float, long) -&gt; void. */
    static final String RENDER_WORLD_METHOD = "b";
    private static final String RENDER_WORLD_DESCRIPTOR = "(FJ)V";
    private static final String HOOK_OWNER = "io/github/fv2j3dteam/mesrgl/integration/minecraft/MesrGLRenderHook";

    private static volatile boolean transformerVerified;

    static {
        WorldAutoJoin.maybeStart(Thread.currentThread().getContextClassLoader());
    }

    private MesrGLRenderHook() {
    }

    /**
     * Entry point invoked reflectively by MinecraftBootstrap's class loading
     * pipeline for every Minecraft class. Returns the transformed bytes.
     */
    public static byte[] transform(String name, byte[] bytes) {
        if (!ENTITY_RENDERER.equals(name) || bytes == null) {
            return bytes;
        }
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String firstType, String secondType) {
                return "java/lang/Object";
            }
        };
        boolean[] injected = new boolean[1];
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName, String descriptor,
                                             String signature, String[] exceptions) {
                MethodVisitor visitor = super.visitMethod(access, methodName, descriptor, signature, exceptions);
                if (RENDER_WORLD_METHOD.equals(methodName) && RENDER_WORLD_DESCRIPTOR.equals(descriptor)) {
                    injected[0] = true;
                    return new MethodVisitor(api, visitor) {
                        @Override
                        public void visitCode() {
                            super.visitCode();
                            // if (MesrGLRenderHook.replaceWorldRender(this, partialTicks, nanoTime)) return;
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitVarInsn(Opcodes.FLOAD, 1);
                            super.visitVarInsn(Opcodes.LLOAD, 2);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK_OWNER,
                                    "replaceWorldRender", "(Ljava/lang/Object;FJ)Z", false);
                            Label continueVanilla = new Label();
                            super.visitJumpInsn(Opcodes.IFEQ, continueVanilla);
                            super.visitInsn(Opcodes.RETURN);
                            super.visitLabel(continueVanilla);
                        }
                    };
                }
                return visitor;
            }
        }, ClassReader.EXPAND_FRAMES);
        if (!injected[0]) {
            // A wrong assumption about the vanilla bytecode must be loud, not
            // a silent no-op pretending integration exists (spec: no placebo).
            throw new IllegalStateException("EntityRenderer.renderWorld(buq." + RENDER_WORLD_METHOD
                    + RENDER_WORLD_DESCRIPTOR + ") not found in the vanilla 1.12.2 jar; "
                    + "the MesrGL render hook cannot be installed.");
        }
        transformerVerified = true;
        return writer.toByteArray();
    }

    /** True once the transformer has matched and patched the vanilla class. */
    public static boolean isTransformerVerified() {
        return transformerVerified;
    }

    /**
     * Injected callback: returns true when MesrGL rendered the world for this
     * frame and the vanilla world render must be skipped.
     *
     * @param entityRenderer the EntityRenderer instance ({@code this})
     * @param partialTicks   the vanilla partial-ticks value
     * @param finishTimeNano the vanilla frame deadline (kept for parity)
     */
    public static boolean replaceWorldRender(Object entityRenderer, float partialTicks, long finishTimeNano) {
        String mode = System.getProperty(MesrGLMinecraftRenderer.MODE_PROPERTY, "on");
        if ("off".equalsIgnoreCase(mode)) {
            return false;
        }
        if (MesrGLMinecraftRenderer.isInitializationFailed()) {
            return false;
        }
        try {
            MesrGLMinecraftRenderer renderer = MesrGLMinecraftRenderer.acquire(
                    entityRenderer.getClass().getClassLoader());
            Object mc = MinecraftReflection.minecraftInstanceOf(entityRenderer.getClass().getClassLoader());
            if (mc == null) {
                return false;
            }
            if (!renderer.isInitialized()) {
                java.awt.Dimension size = DisplayAccess.size(entityRenderer.getClass().getClassLoader());
                renderer.initialize(size.width, size.height);
            }
            return renderer.renderWorld(mc, partialTicks);
        } catch (Throwable t) {
            // Recoverable: log once and let the vanilla renderer take this
            // frame (spec 37: explicit fallback, never a swallowed error).
            System.err.println("[MesrGL] world render failed, vanilla fallback this frame: " + t);
            t.printStackTrace();
            return false;
        }
    }

    /** Display size accessor shared with the renderer (LWJGL2 reflection). */
    static final class DisplayAccess {
        static java.awt.Dimension size(ClassLoader cl) {
            try {
                Class<?> display = cl.loadClass("org.lwjgl.opengl.Display");
                int w = (Integer) display.getMethod("getWidth").invoke(null);
                int h = (Integer) display.getMethod("getHeight").invoke(null);
                return new java.awt.Dimension(w, h);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Display size unavailable: " + e, e);
            }
        }
    }
}
