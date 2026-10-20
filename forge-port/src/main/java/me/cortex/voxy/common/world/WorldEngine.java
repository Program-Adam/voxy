package me.cortex.voxy.common.world;

import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Loader-independent section storage engine plus asynchronous chunk ingestion. */
public final class WorldEngine implements AutoCloseable {
    public final StorageBackend storage;
    public final VoxelIngestService ingestService;
    private final Mapper mapper;
    private final ActiveSectionTracker sectionTracker;
    private final ExecutorService saveExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final int maxMipLevels;
    private volatile Consumer<WorldSection> dirtyCallback;

    public WorldEngine(StorageBackend storage, int ingestWorkers, int savingWorkers, int maxMipLevels) {
        this.storage = Objects.requireNonNull(storage, "storage");
        if (savingWorkers < 1 || ingestWorkers < 1) throw new IllegalArgumentException("Worker counts must be positive");
        if (maxMipLevels < 1 || maxMipLevels > 4) throw new IllegalArgumentException("maxMipLevels must be between 1 and 4");
        this.maxMipLevels = maxMipLevels;
        this.mapper = new Mapper(storage);
        this.sectionTracker = new ActiveSectionTracker(3, this::loadSection);
        this.saveExecutor = Executors.newFixedThreadPool(savingWorkers, runnable -> {
            Thread thread = new Thread(runnable, "Voxy Forge section saver");
            thread.setDaemon(false);
            return thread;
        });
        this.ingestService = new VoxelIngestService(this, ingestWorkers);
    }

    public Mapper getMapper() { return mapper; }

    private int loadSection(WorldSection section) {
        ByteBuffer data = storage.getSectionData(section.key);
        if (data == null) return 1;
        try {
            if (SaveLoadSystem.deserialize(section, data, true)) return 0;
            storage.deleteSectionData(section.key);
            Arrays.fill(section.data, Mapper.AIR);
            System.err.println("Voxy: invalid saved section " + section.key + "; discarded");
            return -1;
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    public void setDirtyCallback(Consumer<WorldSection> callback) { dirtyCallback = callback; }

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
                        ByteBuffer encoded = SaveLoadSystem.serialize(section);
                        try { storage.setSectionData(section.key, encoded); }
                        finally { MemoryUtil.memFree(encoded); }
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

    /** Insert a voxelized 16³ source section and update its parent mip levels. */
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

    public int[] getLoadedSectionCacheSizes() { return sectionTracker.getCacheCounts(); }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("World engine is closed");
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { ingestService.close(); } catch (Exception e) { System.err.println(e); }
        saveExecutor.shutdown();
        try {
            if (!saveExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                saveExecutor.shutdownNow();
                saveExecutor.awaitTermination(10, TimeUnit.SECONDS);
            }
        } catch (InterruptedException interrupted) {
            saveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        try { storage.flush(); } catch (Exception error) { System.err.println(error); }
        try { storage.close(); } catch (Exception error) { System.err.println(error); }
    }
}
