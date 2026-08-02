package org.lwjgl.opengl.awt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.lwjgl.opengl.GL;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.ARBMultisample.GL_SAMPLES_ARB;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.GL_RENDERER;
import static org.lwjgl.opengl.GL11.GL_VENDOR;
import static org.lwjgl.opengl.GL11.GL_VERSION;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glGetError;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL11.glGetString;
import static org.lwjgl.opengl.GL11.glViewport;

/**
 * Manual regression test for https://github.com/LWJGLX/lwjgl3-awt/issues/48.
 * Enable with {@code -Dlwjgl3awt.test.win32CoreResizeStress=true}.
 */
@EnabledOnOs(OS.WINDOWS)
class Win32CoreProfileResizeTest {

    @Test
    void repeatedlyResizesCoreProfileCanvasWithoutLosingTheRenderer() throws Exception {
        assumeTrue(Boolean.getBoolean("lwjgl3awt.test.win32CoreResizeStress"),
                "Enable the Windows core-profile resize stress test explicitly");

        int cycles = Integer.getInteger("lwjgl3awt.test.win32CoreResizeCycles", 2_000);
        int samples = Integer.getInteger("lwjgl3awt.test.win32Samples", 4);
        int managedSamples = Integer.getInteger("lwjgl3awt.test.win32ManagedSamples", 0);
        boolean createContext = !Boolean.getBoolean("lwjgl3awt.test.win32NoContext");
        boolean resize = !Boolean.getBoolean("lwjgl3awt.test.win32CoreNoResizeControl");
        boolean renderEachCycle = createContext && !Boolean.getBoolean("lwjgl3awt.test.win32NoRender");
        boolean swapBuffers = !Boolean.getBoolean("lwjgl3awt.test.win32NoSwap");
        boolean finishBeforeSwap = Boolean.getBoolean("lwjgl3awt.test.win32FinishBeforeSwap");
        boolean compatibilityProfile = Boolean.getBoolean("lwjgl3awt.test.win32CompatibilityProfile");
        int memorySampleInterval = Integer.getInteger("lwjgl3awt.test.win32MemorySampleInterval", 0);
        int settleMillis = Integer.getInteger("lwjgl3awt.test.win32SettleMillis", 0);
        int disposeSettleMillis = Integer.getInteger("lwjgl3awt.test.win32DisposeSettleMillis", 0);
        AtomicReference<JFrame> frameRef = new AtomicReference<>();
        AtomicReference<AWTGLCanvas> canvasRef = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            GLData data = new GLData();
            data.samples = managedSamples > 0 ? 0 : samples;
            data.managedSamples = managedSamples;
            data.swapInterval = 0;
            data.majorVersion = 3;
            data.minorVersion = 2;
            data.profile = compatibilityProfile ? GLData.Profile.COMPATIBILITY : GLData.Profile.CORE;

            AWTGLCanvas canvas = new AWTGLCanvas(data) {
                private boolean managedSamplesVerified;
                private boolean managedResolveVerified;

                @Override
                public void initGL() {
                    GL.createCapabilities();
                    System.out.println("Resize stress JVM: " + ManagementFactory.getRuntimeMXBean().getName());
                    System.out.println("Workload: " + (resize ? "resize" : "fixed-size render control"));
                    System.out.println("Render each cycle: " + renderEachCycle + ", swap: " + swapBuffers
                            + ", finish before swap: " + finishBeforeSwap);
                    System.out.println("Requested profile: " + data.profile + ", window samples: " + data.samples
                            + ", managed samples: " + managedSamples);
                    System.out.println("OpenGL vendor: " + glGetString(GL_VENDOR));
                    System.out.println("OpenGL renderer: " + glGetString(GL_RENDERER));
                    System.out.println("OpenGL version: " + glGetString(GL_VERSION));
                    glClearColor(0.3f, 0.4f, 0.5f, 1.0f);
                }

                @Override
                public void paintGL() {
                    int width = getFramebufferWidth();
                    int height = getFramebufferHeight();
                    if (managedSamples > 0 && !managedSamplesVerified) {
                        int actualSamples = glGetInteger(GL_SAMPLES_ARB);
                        if (actualSamples != managedSamples || getDefaultFramebuffer() == 0) {
                            throw new IllegalStateException("Managed framebuffer has " + actualSamples
                                    + " samples and id " + getDefaultFramebuffer());
                        }
                        System.out.println("Verified managed framebuffer samples: " + actualSamples);
                        managedSamplesVerified = true;
                    }
                    glViewport(0, 0, width, height);
                    glClear(GL_COLOR_BUFFER_BIT);
                    if (finishBeforeSwap) {
                        org.lwjgl.opengl.GL11.glFinish();
                    }
                    if (swapBuffers) {
                        swapBuffers();
                        if (managedSamples > 0 && !managedResolveVerified) {
                            int error = glGetError();
                            if (error != GL_NO_ERROR) {
                                throw new IllegalStateException("Managed framebuffer resolve failed with GL error "
                                        + error);
                            }
                            System.out.println("Verified managed framebuffer resolve");
                            managedResolveVerified = true;
                        }
                    }
                }
            };
            canvas.setPreferredSize(new Dimension(640, 480));

            JFrame frame = new JFrame("Win32 core-profile resize stress");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.getContentPane().add(canvas);
            frame.pack();
            frame.setVisible(true);
            frameRef.set(frame);
            canvasRef.set(canvas);
        });

        MemorySample memoryBefore = null;
        try {
            if (createContext) {
                SwingUtilities.invokeAndWait(canvasRef.get()::render);
            } else {
                System.out.println("Resize stress JVM: " + ManagementFactory.getRuntimeMXBean().getName());
                System.out.println("Workload: " + (resize ? "pure AWT resize" : "fixed-size AWT control"));
                System.out.println("OpenGL context: not created");
            }
            final MemorySample baselineMemory = memorySample();
            memoryBefore = baselineMemory;
            System.out.println("Memory before: " + memoryBefore);

            for (int i = 0; i < cycles; i++) {
                int width = 480 + (i % 17) * 43;
                int height = 360 + (i % 13) * 37;
                SwingUtilities.invokeAndWait(() -> {
                    if (resize) {
                        frameRef.get().setSize(width, height);
                    }
                    if (renderEachCycle) {
                        canvasRef.get().render();
                    }
                });
                if (i % 100 == 0) {
                    System.out.println("Completed cycle " + i + " of " + cycles);
                }
                if (memorySampleInterval > 0 && (i + 1) % memorySampleInterval == 0) {
                    MemorySample memoryAtCycle = memorySample();
                    System.out.println("Memory at cycle " + (i + 1) + ": " + memoryAtCycle
                            + "; delta=" + memoryAtCycle.minus(baselineMemory));
                }
                Thread.sleep(5);
            }
        } finally {
            try {
                MemorySample memoryAfter = memorySample();
                System.out.println("Memory after resize: " + memoryAfter);
                if (memoryBefore != null) {
                    System.out.println("Memory delta after resize: " + memoryAfter.minus(memoryBefore));
                }
                if (settleMillis > 0) {
                    Thread.sleep(settleMillis);
                    MemorySample memoryAfterSettle = memorySample();
                    System.out.println("Memory after " + settleMillis + " ms idle: " + memoryAfterSettle);
                    if (memoryBefore != null) {
                        System.out.println("Memory delta after idle: " + memoryAfterSettle.minus(memoryBefore));
                    }
                }
            } finally {
                SwingUtilities.invokeAndWait(() -> {
                    GL.setCapabilities(null);
                    frameRef.get().dispose();
                });
            }
            if (disposeSettleMillis > 0) {
                Thread.sleep(disposeSettleMillis);
                MemorySample memoryAfterDispose = memorySample();
                System.out.println("Memory after dispose plus " + disposeSettleMillis + " ms: " + memoryAfterDispose);
                if (memoryBefore != null) {
                    System.out.println("Memory delta after dispose: " + memoryAfterDispose.minus(memoryBefore));
                }
            }
        }
    }

    private static MemorySample memorySample() {
        return new MemorySample(committedVirtualMemory(), windowsProcessMemory());
    }

    private static long committedVirtualMemory() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean) {
            return ((com.sun.management.OperatingSystemMXBean) bean).getCommittedVirtualMemorySize();
        }
        return -1L;
    }

    private static long[] windowsProcessMemory() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        String pid = runtimeName.substring(0, runtimeName.indexOf('@'));
        String command = "$p = Get-Process -Id " + pid + "; Write-Output "
                + "($p.PrivateMemorySize64.ToString() + ' ' + $p.WorkingSet64.ToString() + ' ' + $p.Handles.ToString())";
        try {
            Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command)
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.readLine();
            }
            if (process.waitFor() != 0 || output == null) {
                return new long[] { -1L, -1L, -1L };
            }
            String[] values = output.trim().split("\\s+");
            return new long[] { Long.parseLong(values[0]), Long.parseLong(values[1]), Long.parseLong(values[2]) };
        } catch (Exception e) {
            System.err.println("Could not read Windows process memory: " + e);
            return new long[] { -1L, -1L, -1L };
        }
    }

    private static final class MemorySample {
        final long committedVirtual;
        final long privateBytes;
        final long workingSet;
        final long handles;

        MemorySample(long committedVirtual, long[] processMemory) {
            this(committedVirtual, processMemory[0], processMemory[1], processMemory[2]);
        }

        MemorySample(long committedVirtual, long privateBytes, long workingSet, long handles) {
            this.committedVirtual = committedVirtual;
            this.privateBytes = privateBytes;
            this.workingSet = workingSet;
            this.handles = handles;
        }

        MemorySample minus(MemorySample other) {
            return new MemorySample(committedVirtual - other.committedVirtual, privateBytes - other.privateBytes,
                    workingSet - other.workingSet, handles - other.handles);
        }

        @Override
        public String toString() {
            return "committedVirtual=" + committedVirtual + ", privateBytes=" + privateBytes
                    + ", workingSet=" + workingSet + ", handles=" + handles;
        }
    }

}
