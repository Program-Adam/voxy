package me.cortex.voxy.common.world;

import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Loader-independent Voxy world-section engine.
 *
 * It owns the active section cache, applies source-section updates to all
 * configured mip levels, and asynchronously persists dirty sections. Minecraft
 * chunk capture and block/biome mapping are supplied by the Forge client layer.
 */
public final class WorldEngine implements AutoCloseable {
    public final StorageBackend storage;
    private final ActiveSectionTracker sectionTracker;
    private final ExecutorService saveExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final int maxMipLevels;
    private volatile Consumer<WorldSection> dirtyCallback;

    public WorldEngine(StorageBackend storage, int savingWorkers, int maxMipLevels) {
        this.storage = Objects.requireNonNull(storage, "storage");
        if (savingWorkers < 1) throw new IllegalArgumentException("savingWorkers must be positive");
        if (maxMipLevels < 1 || maxMipLevels > 4)
            throw new IllegalArgumentException("maxMipLevels must be between 1 and 4");
        this.maxMipLevels = maxMipLevels;
        this.sectionTracker = new ActiveSectionTracker(3, this::loadSection);
        this.saveExecutor = Executors.newFixedThreadPool(savingWorkers, runnable -> {
            Thread thread = new Thread(runnable, "Voxy Forge section saver");
            thread.setDaemon(false);
            return thread;
        });
    }

    private int loadSection(WorldSection section) {
        ByteBuffer data = storage.getSectionData(section.key);
        if (data == null) return 1;
        try {
            if (SaveLoadSystem.deserialize(section, data, true)) return 0;
            storage.deleteSectionData(section.key);
            Arrays.fill(section.data, 0L);
            System.err.println("Voxy: invalid saved section " + section.key + "; discarded");
            return -1;
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    public void setDirtyCallback(Consumer<WorldSection> callback) {
        this.dirtyCallback = callback;
    }

    public WorldSection acquire(int level, int x, int y, int z) {
        ensureOpen();
        return sectionTracker.acquire(level, x, y, z, false);
    }

    public WorldSection acquireIfExists(int level, int x, int y, int z) {
        ensureOpen();
        return sectionTracker.acquire(level, x, y, z, true);
    }

    public void markDirty(WorldSection section) {
        ensureOpen();
        Consumer<WorldSection> callback = dirtyCallback;
        if (callback != null) callback.accept(section);
        if (section.inSaveQueue.compareAndSet(false, true)) {
            section.acquire();
            try {
                saveExecutor.execute(() -> {
                    try {
                        section.assertNotFree();
                        ByteBuffer data = SaveLoadSystem.serialize(section);
                        try {
                            storage.setSectionData(section.key, data);
                        } finally {
                            MemoryUtil.memFree(data);
                        }
                    } catch (Throwable error) {
                        System.err.println("Voxy: failed to save section " + section.key + ": " + error);
                    } finally {
                        section.inSaveQueue.set(false);
                        section.release();
                    }
                });
            } catch (RuntimeException rejected) {
                section.inSaveQueue.set(false);
                section.release();
                throw rejected;
            }
        }
    }

    /** Insert an already voxelized source chunk section and update parent mips. */
    public void insertUpdate(VoxelizedSection source) {
        ensureOpen();
        for (int level = 0; level < maxMipLevels; level++) {
            WorldSection target = acquire(level, source.x >> (level + 1),
                    source.y >> (level + 1), source.z >> (level + 1));
            boolean changed = false;
            try {
                int mask = (1 << (level + 1)) - 1;
                int bx = (source.x & mask) << (4 - level);
                int by = (source.y & mask) << (4 - level);
                int bz = (source.z & mask) << (4 - level);
                int size = 16 >> level;
                for (int y = by; y < by + size; y++) {
                    for (int z = bz; z < bz + size; z++) {
                        for (int x = bx; x < bx + size; x++) {
                            long value = source.get(level, x - bx, y - by, z - bz);
                            changed |= target.set(x, y, z, value) != value;
                        }
                    }
                }
                if (changed) markDirty(target);
            } finally {
                target.release();
            }
            if (!changed) break;
        }
    }

    public int[] getLoadedSectionCacheSizes() {
        return sectionTracker.getCacheCounts();
    }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("World engine is closed");
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        saveExecutor.shutdown();
        try {
            if (!saveExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                saveExecutor.shutdownNow();
                if (!saveExecutor.awaitTermination(10, TimeUnit.SECONDS))
                    System.err.println("Voxy: saver threads did not stop cleanly");
            }
        } catch (InterruptedException interrupted) {
            saveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        try { storage.flush(); } catch (Exception error) { System.err.println(error); }
        try { storage.close(); } catch (Exception error) { System.err.println(error); }
    }
}
