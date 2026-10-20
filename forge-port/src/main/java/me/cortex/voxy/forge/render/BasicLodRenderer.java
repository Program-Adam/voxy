package me.cortex.voxy.forge.render;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.forge.VoxyForgeClient;
import me.cortex.voxy.forge.config.ForgeVoxyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Tesselator;
import net.minecraft.client.renderer.BufferUploader;
import net.minecraft.client.renderer.VertexConsumer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Conservative fallback renderer for the Forge port.
 *
 * It draws the highest occupied cell of the coarsest persisted voxel mip.
 * This is intentionally independent of Voxy's advanced compute/meshlet renderer,
 * which still needs a full Forge port.
 */
@Mod.EventBusSubscriber(modid = "voxy", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class BasicLodRenderer {
    private static final int LOD_LEVEL = 3;
    private static final int SECTION_BLOCK_SIZE = 32 << LOD_LEVEL;
    private static final int MAX_SECTION_RADIUS = 6;
    private static final Map<Integer, Integer> COLORS = new HashMap<>();

    private BasicLodRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !ForgeVoxyConfig.CONFIG.enabled) return;
        WorldEngine engine = VoxyForgeClient.getWorldEngine();
        if (engine == null) return;

        var camera = event.getCamera().getPosition();
        int centerX = floorDiv((int)Math.floor(camera.x), SECTION_BLOCK_SIZE);
        int centerY = floorDiv((int)Math.floor(camera.y), SECTION_BLOCK_SIZE);
        int centerZ = floorDiv((int)Math.floor(camera.z), SECTION_BLOCK_SIZE);
        int radius = Math.min(MAX_SECTION_RADIUS, Math.max(1, ForgeVoxyConfig.CONFIG.renderDistance / 8));
        Matrix4f pose = event.getPoseStack().last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        var builder = tesselator.getBuilder();
        boolean began = false;
        COLORS.clear();

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        try {
            for (int sx = centerX - radius; sx <= centerX + radius; sx++) {
                for (int sz = centerZ - radius; sz <= centerZ + radius; sz++) {
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
                                        Integer cachedColor = COLORS.get(blockId);
                                        int color;
                                        if (cachedColor != null) {
                                            color = cachedColor;
                                        } else {
                                            try {
                                                BlockState state = engine.getMapper().getBlockStateFromBlockId(blockId);
                                                color = state.getMapColor(mc.level,
                                                        new BlockPos(baseX + x * (1 << LOD_LEVEL),
                                                                baseY + y * (1 << LOD_LEVEL),
                                                                baseZ + z * (1 << LOD_LEVEL))).col;
                                            } catch (RuntimeException e) {
                                                color = 0x777777;
                                            }
                                            COLORS.put(blockId, color);
                                        }
                                        float red = ((color >> 16) & 255) / 255.0f;
                                        float green = ((color >> 8) & 255) / 255.0f;
                                        float blue = (color & 255) / 255.0f;
                                        float wx = baseX + x * (1 << LOD_LEVEL) - (float) camera.x;
                                        float wy = baseY + (y + 1) * (1 << LOD_LEVEL) - (float) camera.y;
                                        float wz = baseZ + z * (1 << LOD_LEVEL) - (float) camera.z;
                                        float size = 1 << LOD_LEVEL;
                                        vertex(builder, pose, wx, wy, wz, red, green, blue);
                                        vertex(builder, pose, wx, wy, wz + size, red, green, blue);
                                        vertex(builder, pose, wx + size, wy, wz + size, red, green, blue);
                                        vertex(builder, pose, wx + size, wy, wz, red, green, blue);
                                        break;
                                    }
                                }
                            }
                            began = true;
                        } finally {
                            section.release();
                        }
                    }
                }
            }
            if (began) BufferUploader.drawWithShader(builder.end());
            else builder.end().close();
        } catch (RuntimeException | Error error) {
            // Do not let a rendering fallback crash the whole client.
            try { builder.end().close(); } catch (Throwable ignored) {}
            throw error;
        } finally {
            RenderSystem.disableCull();
        }
    }

    private static int floorDiv(int value, int divisor) {
        return Math.floorDiv(value, divisor);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float z,
                               float r, float g, float b) {
        consumer.vertex(pose, x, y, z).color(r, g, b, 1.0f).endVertex();
    }
}
