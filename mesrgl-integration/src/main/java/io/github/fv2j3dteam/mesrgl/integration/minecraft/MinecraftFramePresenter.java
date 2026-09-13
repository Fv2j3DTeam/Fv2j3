package io.github.fv2j3dteam.mesrgl.integration.minecraft;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;

/**
 * Presents the MesrGL framebuffer inside Minecraft's render target and draws
 * the activity overlay.
 *
 * Boundary note (spec 01): OpenGL is used here strictly as the *display
 * surface* of the already computed MesrGL pixels (glDrawPixels of the
 * finished RGBA8 buffer) - no lighting, geometry, or image content is
 * produced by GL. Every pixel on screen was computed by the MesrGL software
 * ray tracer, exactly like the CPU/GPU split inside MesrGL itself.
 *
 * Presentation strategy:
 *  - identity projection/modelview so raster coordinates are window coords
 *  - glPixelZoom scales the internal render buffer to the window size
 *  - the buffer's top row is drawn at the window top via a negative y zoom
 *  - after the pixels, a 2D ortho is set and the vanilla FontRenderer draws
 *    the activity line; all GL state is saved/restored with attrib+matrix
 *    pushes so the vanilla overlay that follows is unaffected
 */
final class MinecraftFramePresenter {

    private final ClassLoader mcClassLoader;
    private boolean bound;

    private MethodHandle glClear;
    private MethodHandle glDrawPixels;
    private MethodHandle glPixelZoom;
    private MethodHandle glRasterPos2i;
    private MethodHandle glMatrixMode;
    private MethodHandle glLoadIdentity;
    private MethodHandle glOrtho;
    private MethodHandle glPushMatrix;
    private MethodHandle glPopMatrix;
    private MethodHandle glPushAttrib;
    private MethodHandle glPopAttrib;
    private MethodHandle glDepthMask;
    private MethodHandle glDisable;
    private int glRgba;
    private int glUnsignedByte;
    private int glDepthBufferBit;
    private int glDepthTest;
    private int glProjection;
    private int glModelview;
    private int glAllAttribBits;

    MinecraftFramePresenter(ClassLoader mcClassLoader) {
        this.mcClassLoader = mcClassLoader;
    }

    private void bind() {
        if (bound) {
            return;
        }
        try {
            Class<?> gl11 = mcClassLoader.loadClass("org.lwjgl.opengl.GL11");
            MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();
            glClear = staticMethod(gl11, "glClear", int.class);
            glDrawPixels = staticMethod(gl11, "glDrawPixels",
                    int.class, int.class, int.class, int.class, ByteBuffer.class);
            glPixelZoom = staticMethod(gl11, "glPixelZoom", float.class, float.class);
            glRasterPos2i = staticMethod(gl11, "glRasterPos2i", int.class, int.class);
            glMatrixMode = staticMethod(gl11, "glMatrixMode", int.class);
            glLoadIdentity = staticMethod(gl11, "glLoadIdentity");
            glOrtho = staticMethod(gl11, "glOrtho",
                    double.class, double.class, double.class, double.class, double.class, double.class);
            glPushMatrix = staticMethod(gl11, "glPushMatrix");
            glPopMatrix = staticMethod(gl11, "glPopMatrix");
            glPushAttrib = staticMethod(gl11, "glPushAttrib", int.class);
            glPopAttrib = staticMethod(gl11, "glPopAttrib");
            glDepthMask = staticMethod(gl11, "glDepthMask", boolean.class);
            glDisable = staticMethod(gl11, "glDisable", int.class);

            glRgba = staticInt(gl11, "GL_RGBA");
            glUnsignedByte = staticInt(gl11, "GL_UNSIGNED_BYTE");
            glDepthBufferBit = staticInt(gl11, "GL_DEPTH_BUFFER_BIT");
            glDepthTest = staticInt(gl11, "GL_DEPTH_TEST");
            glProjection = staticInt(gl11, "GL_PROJECTION");
            glModelview = staticInt(gl11, "GL_MODELVIEW");
            glAllAttribBits = staticInt(gl11, "GL_ALL_ATTRIB_BITS");
            bound = true;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("LWJGL GL11 presentation binding failed: " + ex, ex);
        }
    }

    private static MethodHandle staticMethod(Class<?> owner, String name, Class<?>... params)
            throws NoSuchMethodException, IllegalAccessException {
        Method m = owner.getMethod(name, params);
        return MethodHandles.publicLookup().unreflect(m);
    }

    private static int staticInt(Class<?> owner, String name)
            throws NoSuchFieldException, IllegalAccessException {
        Field f = owner.getField(name);
        if (f.getType() != int.class || !Modifier.isStatic(f.getModifiers())) {
            throw new NoSuchFieldException(owner.getName() + "." + name + " is not a static int");
        }
        return f.getInt(null);
    }

    /**
     * Draws the MesrGL RGBA8 buffer over the full window and the overlay line.
     *
     * @param buffer   direct RGBA8 buffer, tightly packed, rows top-to-bottom
     * @param bufferW  buffer width in pixels
     * @param bufferH  buffer height in pixels
     * @param windowW  window framebuffer width
     * @param windowH  window framebuffer height
     */
    void present(ByteBuffer buffer, int bufferW, int bufferH,
                 int windowW, int windowH, Object fontRenderer, String overlayText) {
        bind();
        try {
            if (buffer.position() != 0) {
                buffer.rewind();
            }
            float zoomX = (float) windowW / (float) bufferW;
            float zoomY = (float) windowH / (float) bufferH;

            glPushAttrib.invokeExact(glAllAttribBits);
            glMatrixMode.invokeExact(glProjection);
            glPushMatrix.invoke();
            glLoadIdentity.invokeExact();
            glMatrixMode.invokeExact(glModelview);
            glPushMatrix.invoke();
            glLoadIdentity.invokeExact();
            glDisable.invokeExact(glDepthTest);
            glDepthMask.invokeExact(false);

            // Clear stale depth from the skipped vanilla pass so the GUI that
            // renders after us is never clipped by old depth values.
            glClear.invokeExact(glDepthBufferBit);

            // Negative y zoom: buffer row 0 (top) lands at the window top.
            glRasterPos2i.invokeExact(0, windowH);
            glPixelZoom.invokeExact(zoomX, -zoomY);
            glDrawPixels.invokeExact(bufferW, bufferH, glRgba, glUnsignedByte, buffer);

            // 2D ortho for the overlay text (GUI coordinate space, y down).
            glMatrixMode.invokeExact(glProjection);
            glLoadIdentity.invokeExact();
            glOrtho.invokeExact(0.0, (double) windowW, (double) windowH, 0.0, -1.0, 1.0);
            glMatrixMode.invokeExact(glModelview);
            glLoadIdentity.invokeExact();
            if (fontRenderer != null && overlayText != null && !overlayText.isEmpty()) {
                MinecraftReflection.get(mcClassLoader)
                        .drawString(fontRenderer, overlayText, 4.0f, 4.0f, 0xFFFFFFFF);
            }

            glMatrixMode.invokeExact(glProjection);
            glPopMatrix.invoke();
            glMatrixMode.invokeExact(glModelview);
            glPopMatrix.invoke();
            glPopAttrib.invoke();
        } catch (Throwable t) {
            throw new IllegalStateException("MesrGL framebuffer presentation failed: " + t, t);
        }
    }
}
