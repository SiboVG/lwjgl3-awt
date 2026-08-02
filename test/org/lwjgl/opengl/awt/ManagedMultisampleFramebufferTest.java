package org.lwjgl.opengl.awt;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.lwjgl.opengl.ARBMultisample.GL_SAMPLES_ARB;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glGetError;
import static org.lwjgl.opengl.GL11.glGetInteger;

class ManagedMultisampleFramebufferTest {

    @Test
    void initialCapacityMatchesTheRequiredSize() {
        assertEquals(640, ManagedMultisampleFramebuffer.growCapacity(0, 640));
    }

    @Test
    void capacityGrowsByHalfForSmallIncreases() {
        assertEquals(960, ManagedMultisampleFramebuffer.growCapacity(640, 641));
    }

    @Test
    void capacityGrowsDirectlyToLargerRequirements() {
        assertEquals(1_200, ManagedMultisampleFramebuffer.growCapacity(640, 1_200));
    }

    @Test
    void capacityGrowthDoesNotOverflow() {
        assertEquals(Integer.MAX_VALUE,
                ManagedMultisampleFramebuffer.growCapacity(Integer.MAX_VALUE - 1, Integer.MAX_VALUE));
    }

    @Test
    void managedResolveUsesAValidDefaultFramebufferBuffer() throws Exception {
        AtomicReference<JFrame> frameRef = new AtomicReference<>();
        AtomicReference<AWTGLCanvas> canvasRef = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            GLData data = new GLData();
            data.majorVersion = 3;
            data.minorVersion = 2;
            data.profile = GLData.Profile.CORE;
            data.managedSamples = 4;
            data.swapInterval = 0;

            AWTGLCanvas canvas = new AWTGLCanvas(data) {
                @Override
                public void initGL() {
                    GL.createCapabilities();
                }

                @Override
                public void paintGL() {
                    assertNotEquals(0, getDefaultFramebuffer());
                    assertEquals(4, glGetInteger(GL_SAMPLES_ARB));
                    assertEquals(GL_NO_ERROR, glGetError(), "Managed framebuffer setup generated a GL error");

                    glClear(GL_COLOR_BUFFER_BIT);
                    swapBuffers();

                    assertEquals(GL_NO_ERROR, glGetError(), "Managed framebuffer resolve generated a GL error");
                }
            };
            canvas.setPreferredSize(new Dimension(320, 240));

            JFrame frame = new JFrame("managed-multisample-resolve");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.getContentPane().add(canvas);
            frame.pack();
            frame.setVisible(true);

            canvasRef.set(canvas);
            frameRef.set(frame);
        });

        try {
            // Render in a separate event so AWT has delivered the initial resize event and the canvas has non-zero
            // framebuffer dimensions.
            SwingUtilities.invokeAndWait(canvasRef.get()::render);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                GL.setCapabilities(null);
                frameRef.get().dispose();
            });
        }
    }
}
