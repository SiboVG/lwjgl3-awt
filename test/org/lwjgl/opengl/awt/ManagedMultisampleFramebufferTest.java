package org.lwjgl.opengl.awt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
