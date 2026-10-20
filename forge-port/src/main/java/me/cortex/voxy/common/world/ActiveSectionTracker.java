package me.cortex.voxy.common.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.cortex.voxy.common.util.VolatileHolder;

import java.util.Arrays;

/** Thread-safe striped cache of currently referenced world sections. */
public final class ActiveSectionTracker {
    @FunctionalInterface
    public interface SectionLoader { int load(WorldSection section); }

    private final Long2ObjectOpenHashMap<VolatileHolder<WorldSection>>[] loadedSectionCache;
    private final SectionLoader loader;

    @SuppressWarnings("unchecked")
    public ActiveSectionTracker(int sliceBits, SectionLoader loader) {
        if (sliceBits < 0 || sliceBits > 10) throw new IllegalArgumentException("sliceBits out of range");
        this.loader = loader;
        this.loadedSectionCache = new Long2ObjectOpenHashMap[1 << sliceBits];
        for (int i = 0; i < loadedSectionCache.length; i++)
            loadedSectionCache[i] = new Long2ObjectOpenHashMap<>(1024);
    }

    public WorldSection acquire(int lvl, int x, int y, int z, boolean nullOnEmpty) {
        long key = WorldSectionId.getWorldSectionId(lvl, x, y, z);
        var cache = loadedSectionCache[getCacheArrayIndex(key)];
        VolatileHolder<WorldSection> holder;
        boolean isLoader = false;
        synchronized (cache) {
            holder = cache.get(key);
            if (holder == null) {
                holder = new VolatileHolder<>();
                cache.put(key, holder);
                isLoader = true;
            }
            WorldSection existing = holder.obj;
            if (existing != null) {
                existing.acquire();
                return existing;
            }
        }

        if (isLoader) {
            WorldSection section = new WorldSection(lvl, x, y, z, this);
            int status = loader.load(section);
            if (status < 0) {
                System.err.println("Unable to load section " + key + "; initializing as air");
                status = 1;
            }
            if (status == 1) Arrays.fill(section.data, 0x0F00000000000000L);
            section.acquire();
            holder.obj = section;
            if (nullOnEmpty && status == 1) {
                section.release();
                return null;
            }
            return section;
        }

        WorldSection section;
        while ((section = holder.obj) == null) Thread.onSpinWait();
        synchronized (cache) {
            if (section.tryAcquire()) return section;
        }
        return acquire(lvl, x, y, z, nullOnEmpty);
    }

    void tryUnload(WorldSection section) {
        var cache = loadedSectionCache[getCacheArrayIndex(section.key)];
        synchronized (cache) {
            if (section.trySetFreed()) {
                var removed = cache.remove(section.key);
                if (removed == null || removed.obj != section)
                    throw new IllegalStateException("Section cache ownership mismatch");
            }
        }
    }

    private int getCacheArrayIndex(long key) {
        return (int) (mixStafford13(key) & (loadedSectionCache.length - 1));
    }

    public static long mixStafford13(long seed) {
        seed = (seed ^ seed >>> 30) * -4658895280553007687L;
        seed = (seed ^ seed >>> 27) * -7723592293110705685L;
        return seed ^ seed >>> 31;
    }

    public int[] getCacheCounts() {
        int[] counts = new int[loadedSectionCache.length];
        for (int i = 0; i < counts.length; i++) {
            synchronized (loadedSectionCache[i]) { counts[i] = loadedSectionCache[i].size(); }
        }
        return counts;
    }
}
