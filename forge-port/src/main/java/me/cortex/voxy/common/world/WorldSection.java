package me.cortex.voxy.common.world;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** A ref-counted 32³ region of LOD voxel data. */
public final class WorldSection {
    private static final int ARRAY_REUSE_CACHE_SIZE = 256;
    private static final Deque<long[]> ARRAY_REUSE_CACHE = new ArrayDeque<>(1024);

    public final int lvl, x, y, z;
    public final long key;
    long[] data;
    private final ActiveSectionTracker tracker;
    public final AtomicBoolean inSaveQueue = new AtomicBoolean();
    private final AtomicInteger atomicState = new AtomicInteger(1);

    WorldSection(int lvl, int x, int y, int z, ActiveSectionTracker tracker) {
        this.lvl = lvl;
        this.x = x;
        this.y = y;
        this.z = z;
        this.key = WorldSectionId.getWorldSectionId(lvl, x, y, z);
        this.tracker = tracker;
        synchronized (ARRAY_REUSE_CACHE) {
            this.data = ARRAY_REUSE_CACHE.poll();
        }
        if (this.data == null) this.data = new long[32 * 32 * 32];
    }

    @Override public int hashCode() {
        return ((x * 1235641 + y) * 8127451 + z) * 918267913 + lvl;
    }

    public int acquire() {
        int state = atomicState.addAndGet(2);
        if ((state & 1) == 0) throw new IllegalStateException("Tried to acquire unloaded section");
        return state >> 1;
    }

    public int getRefCount() { return atomicState.get() >> 1; }

    public int release() {
        int state = atomicState.addAndGet(-2);
        if (state < 1) throw new IllegalStateException("Section got into an invalid state");
        if ((state & 1) == 0) throw new IllegalStateException("Tried releasing a freed section");
        if ((state >> 1) == 0) tracker.tryUnload(this);
        return state >> 1;
    }

    boolean trySetFreed() {
        int witness = atomicState.compareAndExchange(1, 0);
        if ((witness & 1) == 0 && witness != 0)
            throw new IllegalStateException("Section marked as free but has refs");
        if (witness == 1) {
            synchronized (ARRAY_REUSE_CACHE) {
                if (ARRAY_REUSE_CACHE.size() < ARRAY_REUSE_CACHE_SIZE) ARRAY_REUSE_CACHE.add(this.data);
            }
            this.data = null;
            return true;
        }
        return false;
    }

    public void assertNotFree() {
        if ((atomicState.get() & 1) == 0) throw new IllegalStateException("Section has been freed");
    }

    public static int getIndex(int x, int y, int z) {
        int mask = 31;
        if ((x | y | z) < 0 || x > mask || y > mask || z > mask)
            throw new IllegalArgumentException("Out of bounds: " + x + ", " + y + ", " + z);
        return (y << 10) | (z << 5) | x;
    }

    public long set(int x, int y, int z, long id) {
        int index = getIndex(x, y, z);
        long old = data[index];
        data[index] = id;
        return old;
    }

    public long[] copyData() {
        assertNotFree();
        return Arrays.copyOf(data, data.length);
    }

    public void copyDataTo(long[] target) {
        assertNotFree();
        if (target.length != data.length) throw new IllegalArgumentException("Expected 32768 longs");
        System.arraycopy(data, 0, target, 0, data.length);
    }

    public boolean tryAcquire() {
        int state = atomicState.updateAndGet(value -> (value & 1) != 0 ? value + 2 : value);
        return (state & 1) != 0;
    }
}
