package org.lwjgl.opengl.awt;

import org.lwjgl.opengl.GL;

import static org.lwjgl.opengl.GL11.GL_BACK;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_FRONT;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.glDrawBuffer;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT16;
import static org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24;
import static org.lwjgl.opengl.GL21.GL_SRGB8_ALPHA8;
import static org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL30.GL_DEPTH24_STENCIL8;
import static org.lwjgl.opengl.GL30.GL_DEPTH_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_DEPTH_COMPONENT32F;
import static org.lwjgl.opengl.GL30.GL_DEPTH_STENCIL_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL30.GL_MAX_SAMPLES;
import static org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30.GL_RENDERBUFFER;
import static org.lwjgl.opengl.GL30.GL_RENDERBUFFER_BINDING;
import static org.lwjgl.opengl.GL30.GL_RGBA16F;
import static org.lwjgl.opengl.GL30.GL_STENCIL_ATTACHMENT;
import static org.lwjgl.opengl.GL30.GL_STENCIL_INDEX8;
import static org.lwjgl.opengl.GL30.glBindFramebuffer;
import static org.lwjgl.opengl.GL30.glBindRenderbuffer;
import static org.lwjgl.opengl.GL30.glBlitFramebuffer;
import static org.lwjgl.opengl.GL30.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL30.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL30.glDeleteRenderbuffers;
import static org.lwjgl.opengl.GL30.glFramebufferRenderbuffer;
import static org.lwjgl.opengl.GL30.glGenFramebuffers;
import static org.lwjgl.opengl.GL30.glGenRenderbuffers;
import static org.lwjgl.opengl.GL30.glRenderbufferStorageMultisample;

/**
 * A grow-only multisampled render target that resolves into the window framebuffer.
 */
final class ManagedMultisampleFramebuffer {
    private final int samples;
    private final boolean doubleBuffered;
    private final int colorFormat;
    private final int depthStencilFormat;
    private final int depthStencilAttachment;
    private int framebuffer;
    private int colorBuffer;
    private int depthStencilBuffer;
    private int width;
    private int height;

    ManagedMultisampleFramebuffer(GLData data) {
        this.samples = data.managedSamples;
        this.doubleBuffered = data.doubleBuffer;
        this.colorFormat = data.pixelFormatFloat ? GL_RGBA16F : data.sRGB ? GL_SRGB8_ALPHA8 : GL_RGBA8;
        if (data.depthSize > 0 && data.stencilSize > 0) {
            this.depthStencilFormat = GL_DEPTH24_STENCIL8;
            this.depthStencilAttachment = GL_DEPTH_STENCIL_ATTACHMENT;
        } else if (data.depthSize > 0) {
            this.depthStencilFormat = data.depthSize <= 16 ? GL_DEPTH_COMPONENT16
                    : data.depthSize <= 24 ? GL_DEPTH_COMPONENT24 : GL_DEPTH_COMPONENT32F;
            this.depthStencilAttachment = GL_DEPTH_ATTACHMENT;
        } else if (data.stencilSize > 0) {
            this.depthStencilFormat = GL_STENCIL_INDEX8;
            this.depthStencilAttachment = GL_STENCIL_ATTACHMENT;
        } else {
            this.depthStencilFormat = 0;
            this.depthStencilAttachment = 0;
        }
    }

    int framebuffer() {
        return framebuffer;
    }

    void bind(int requiredWidth, int requiredHeight) {
        if (requiredWidth <= 0 || requiredHeight <= 0) {
            return;
        }
        ensureCapacity(requiredWidth, requiredHeight);
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
    }

    void resolve(int resolveWidth, int resolveHeight) {
        if (framebuffer == 0 || resolveWidth <= 0 || resolveHeight <= 0) {
            return;
        }
        glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
        glDrawBuffer(doubleBuffered ? GL_BACK : GL_FRONT);
        glBlitFramebuffer(0, 0, resolveWidth, resolveHeight, 0, 0, resolveWidth, resolveHeight,
                GL_COLOR_BUFFER_BIT, GL_NEAREST);
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
    }

    void contextDeleted() {
        framebuffer = 0;
        colorBuffer = 0;
        depthStencilBuffer = 0;
        width = 0;
        height = 0;
    }

    private void ensureCapacity(int requiredWidth, int requiredHeight) {
        if (requiredWidth <= width && requiredHeight <= height) {
            return;
        }
        if (!GL.getCapabilities().OpenGL30) {
            throw new IllegalStateException("Managed multisampling requires OpenGL 3.0 or newer");
        }
        int maximumSamples = glGetInteger(GL_MAX_SAMPLES);
        if (samples > maximumSamples) {
            throw new IllegalStateException("Requested " + samples + " managed samples, but GL_MAX_SAMPLES is "
                    + maximumSamples);
        }

        int allocationWidth = growCapacity(width, requiredWidth);
        int allocationHeight = growCapacity(height, requiredHeight);
        int previousRenderbuffer = glGetInteger(GL_RENDERBUFFER_BINDING);
        deleteBuffers();
        try {
            framebuffer = glGenFramebuffers();
            colorBuffer = glGenRenderbuffers();
            depthStencilBuffer = depthStencilFormat != 0 ? glGenRenderbuffers() : 0;
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            glBindRenderbuffer(GL_RENDERBUFFER, colorBuffer);
            glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, colorFormat,
                    allocationWidth, allocationHeight);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, colorBuffer);
            if (depthStencilFormat != 0) {
                glBindRenderbuffer(GL_RENDERBUFFER, depthStencilBuffer);
                glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, depthStencilFormat,
                        allocationWidth, allocationHeight);
                glFramebufferRenderbuffer(GL_FRAMEBUFFER, depthStencilAttachment, GL_RENDERBUFFER,
                        depthStencilBuffer);
            }
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                deleteBuffers();
                throw new IllegalStateException("Managed multisample framebuffer is incomplete");
            }
            width = allocationWidth;
            height = allocationHeight;
        } finally {
            glBindRenderbuffer(GL_RENDERBUFFER, previousRenderbuffer);
        }
    }

    private void deleteBuffers() {
        if (framebuffer != 0) {
            if (depthStencilBuffer != 0) {
                glDeleteRenderbuffers(depthStencilBuffer);
            }
            glDeleteRenderbuffers(colorBuffer);
            glDeleteFramebuffers(framebuffer);
            framebuffer = 0;
            colorBuffer = 0;
            depthStencilBuffer = 0;
            width = 0;
            height = 0;
        }
    }

    static int growCapacity(int current, int required) {
        long grown = current == 0 ? required : (long) current + Math.max(1, current / 2);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(required, grown));
    }
}
