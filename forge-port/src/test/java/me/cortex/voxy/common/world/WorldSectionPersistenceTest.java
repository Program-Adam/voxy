package me.cortex.voxy.common.world;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class WorldSectionPersistenceTest {
    @Test
    void sectionPaletteRoundTripsAndReferenceCountReturnsToZero() {
        ActiveSectionTracker sourceTracker = new ActiveSectionTracker(1, ignored -> 1);
        WorldSection source = sourceTracker.acquire(0, -3, 7, 12, false);
        source.set(1, 2, 3, 0x123456789ABCDEFL);
        source.set(31, 31, 31, 0x7FEDCBA987654321L);

        ByteBuffer encoded = SaveLoadSystem.serialize(source);
        try {
            ActiveSectionTracker targetTracker = new ActiveSectionTracker(1, ignored -> 1);
            WorldSection target = targetTracker.acquire(0, -3, 7, 12, false);
            try {
                assertTrue(SaveLoadSystem.deserialize(target, encoded, false));
                assertArrayEquals(source.copyData(), target.copyData());
            } finally {
                target.release();
            }
        } finally {
            MemoryUtil.memFree(encoded);
            source.release();
        }
        assertEquals(0, source.getRefCount());
    }

    @Test
    void malformedSectionPayloadIsRejected() {
        ActiveSectionTracker tracker = new ActiveSectionTracker(1, ignored -> 1);
        WorldSection section = tracker.acquire(0, 0, 0, 0, false);
        try {
            assertFalse(SaveLoadSystem.deserialize(section, ByteBuffer.allocate(3), true));
        } finally {
            section.release();
        }
    }
}
