package me.cortex.voxy.common.world.service;

import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldConversionFactory;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background conversion of loaded Forge chunks into Voxy voxel sections.
 * Chunk callbacks only enqueue; conversion and mip generation run on workers.
 */
public final class VoxelIngestService implements AutoCloseable {
    private final WorldEngine world;
    private final LinkedBlockingQueue<LevelChunk> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread[] workers;

    public VoxelIngestService(WorldEngine world, int workerCount) {
        this.world = world;
        if (workerCount < 1) throw new IllegalArgumentException("workerCount must be positive");
        workers = new Thread[workerCount];
        for (int i=0;i<workerCount;i++) {
            final int index = i;
            workers[i] = new Thread(this::work, "Voxy Forge ingest #" + index);
            workers[i].setDaemon(true);
            workers[i].start();
        }
    }

    public void enqueue(LevelChunk chunk) {
        if (running.get() && chunk != null) queue.offer(chunk);
    }

    public int getTaskCount() { return queue.size(); }

    private void work() {
        while (running.get() || !queue.isEmpty()) {
            try {
                LevelChunk chunk = queue.poll(250, TimeUnit.MILLISECONDS);
                if (chunk != null) ingest(chunk);
            } catch (InterruptedException interrupted) {
                if (!running.get()) break;
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable error) {
                System.err.println("Voxy Forge chunk ingestion failed: " + error);
                error.printStackTrace();
            }
        }
    }

    private void ingest(LevelChunk chunk) {
        Level level = chunk.getLevel();
        if (level == null || level.isClientSide == false) return;
        LevelChunkSection[] sections = chunk.getSections();
        int minSection = level.getMinSection();
        for (int index=0; index<sections.length; index++) {
            LevelChunkSection section = sections[index];
            int sectionY = minSection + index;
            int chunkX = chunk.getPos().x;
            int chunkZ = chunk.getPos().z;
            if (section == null || section.hasOnlyAir()) {
                world.insertUpdate(VoxelizedSection.createEmpty(chunkX, sectionY, chunkZ));
                continue;
            }
            int baseY = sectionY << 4;
            VoxelizedSection voxels = WorldConversionFactory.convert(world.getMapper(),
                    section.getStates(), section.getBiomes(),
                    (x,y,z,state) -> {
                        BlockPos pos = new BlockPos((chunkX << 4) + x, baseY + y, (chunkZ << 4) + z);
                        int block = Math.max(level.getBrightness(LightLayer.BLOCK, pos), state.getLightEmission());
                        int sky = level.getBrightness(LightLayer.SKY, pos);
                        // The persisted voxel format stores inverted sky light.
                        return (byte) ((Math.min(15, block) << 4) | (15 - Math.min(15, sky)));
                    }, chunkX, sectionY, chunkZ);
            WorldConversionFactory.mipSection(voxels, world.getMapper());
            world.insertUpdate(voxels);
        }
    }

    @Override public void close() {
        if (!running.compareAndSet(true, false)) return;
        for (Thread worker : workers) worker.interrupt();
        for (Thread worker : workers) {
            try { worker.join(5000); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        queue.clear();
    }
}
