package me.cortex.voxy.forge.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.forge.VoxyForgeClient;
import me.cortex.voxy.forge.config.ForgeVoxyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Conservative Forge render-stage hook. Draws stored coarse voxel top faces;
 * this is a fallback and not the original meshlet/compute Voxy renderer.
 */
@Mod.EventBusSubscriber(modid = "voxy", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class BasicLodRenderer {
    private static final int LOD_LEVEL = 3;
    private static final int SECTION_BLOCK_SIZE = 32 << LOD_LEVEL;
    private static final int MAX_SECTION_RADIUS = 2;
    private static final Map<Integer, Integer> COLORS = new HashMap<>();
    private static WorldEngine lastEngine;

    private BasicLodRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !ForgeVoxyConfig.CONFIG.enabled) return;
        WorldEngine engine = VoxyForgeClient.getWorldEngine();
        if (engine == null) return;

        var camera = event.getCamera().getPosition();
        int centerX = Math.floorDiv((int) Math.floor(camera.x), SECTION_BLOCK_SIZE);
        int centerY = Math.floorDiv((int) Math.floor(camera.y), SECTION_BLOCK_SIZE);
        int centerZ = Math.floorDiv((int) Math.floor(camera.z), SECTION_BLOCK_SIZE);
        Matrix4f pose = event.getPoseStack().last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        boolean hasVertices = false;
        if (lastEngine != engine) {
            COLORS.clear();
            lastEngine = engine;
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        try {
            for (int sx = centerX - MAX_SECTION_RADIUS; sx <= centerX + MAX_SECTION_RADIUS; sx++) {
                for (int sz = centerZ - MAX_SECTION_RADIUS; sz <= centerZ + MAX_SECTION_RADIUS; sz++) {
                    for (int sy = Math.max(-4, centerY - 2); sy <= Math.min(4, centerY + 1); sy++) {
                        WorldSection section;
                        try {
                            section = engine.acquireIfExists(LOD_LEVEL, sx, sy, sz);
                        } catch (RuntimeException ignored) {
                            continue;
                        }
                        if (section == null) continue;
                        try {
                            long[] voxels = section.copyData();
                            int baseX = sx * SECTION_BLOCK_SIZE;
                            int baseY = sy * SECTION_BLOCK_SIZE;
                            int baseZ = sz * SECTION_BLOCK_SIZE;
                            for (int z = 0; z < 32; z++) {
                                for (int x = 0; x < 32; x++) {
                                    for (int y = 31; y >= 0; y--) {
                                        long voxel = voxels[WorldSection.getIndex(x, y, z)];
                                        if (Mapper.isAir(voxel)) continue;
                                        int blockId = Mapper.getBlockId(voxel);
                                        int color = COLORS.computeIfAbsent(blockId, id -> {
                                            try {
                                                BlockState state = engine.getMapper().getBlockStateFromBlockId(id);
                                                return state.getMapColor(mc.level, new BlockPos(baseX, baseY, baseZ)).col;
                                            } catch (RuntimeException ex) {
                                                return 0x777777;
                                            }
                                        });
                                        float red = ((color >> 16) & 255) / 255.0f;
                                        float green = ((color >> 8) & 255) / 255.0f;
                                        float blue = (color & 255) / 255.0f;
                                        float size = 1 << LOD_LEVEL;
                                        float wx = baseX + x * size - (float) camera.x;
                                        float wy = baseY + (y + 1) * size - (float) camera.y;
                                        float wz = baseZ + z * size - (float) camera.z;
                                        vertex(builder, pose, wx, wy, wz, red, green, blue);
                                        vertex(builder, pose, wx, wy, wz + size, red, green, blue);
                                        vertex(builder, pose, wx + size, wy, wz + size, red, green, blue);
                                        vertex(builder, pose, wx + size, wy, wz, red, green, blue);
                                        hasVertices = true;
                                        break;
                                    }
                                }
                            }
                        } finally {
                            section.release();
                        }
                    }
                }
            }
            var rendered = builder.end();
            if (hasVertices) BufferUploader.drawWithShader(rendered);
            else rendered.close();
        } catch (RuntimeException | Error error) {
            try { builder.end().close(); } catch (Throwable ignored) {}
            throw error;
        } finally {
            RenderSystem.disableCull();
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float z,
                               float r, float g, float b) {
        consumer.vertex(pose, x, y, z).color(r, g, b, 1.0f).endVertex();
    }
}
