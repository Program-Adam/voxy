package me.cortex.voxy.forge;

import com.mojang.logging.LogUtils;
import me.cortex.voxy.common.config.Serialization;
import me.cortex.voxy.forge.config.ForgeVoxyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraft.client.multiplayer.ClientLevel;
import org.slf4j.Logger;

/**
 * Client-only Forge lifecycle hooks. This class must only be loaded on the
 * physical client. This stage establishes a tested client lifecycle boundary
 * before the renderer is migrated.
 */
public final class VoxyForgeClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static ClientLevel observedLevel;
    private static long clientTicks;

    private static final IGuiOverlay STATUS_OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        ForgeVoxyConfig config = ForgeVoxyConfig.CONFIG;
        if (!config.enabled || Minecraft.getInstance().options.hideGui) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        String world = minecraft.level == null ? "No world" : minecraft.level.dimension().location().toString();
        String status = "Voxy Forge port | " + world + " | LOD renderer migration in progress";
        graphics.drawString(minecraft.font, Component.literal(status), 8, 8, 0xFFFFFF, true);
    };

    private VoxyForgeClient() {
    }

    public static void init() {
        Serialization.init();
        ForgeVoxyConfig config = ForgeVoxyConfig.CONFIG;
        if (!java.nio.file.Files.exists(ForgeVoxyConfig.getConfigPath())) {
            config.save();
        }

        LOGGER.info("Voxy Forge port initialized (enabled={}, renderDistance={}, ingestEnabled={}).",
                config.enabled, config.renderDistance, config.ingestEnabled);

        MinecraftForge.EVENT_BUS.addListener(VoxyForgeClient::registerClientCommands);
        MinecraftForge.EVENT_BUS.addListener(VoxyForgeClient::onClientTick);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(VoxyForgeClient::registerOverlays);
    }

    private static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("voxy_status", STATUS_OVERLAY);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        clientTicks++;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel currentLevel = minecraft.level;
        if (currentLevel != observedLevel) {
            if (observedLevel != null) {
                LOGGER.info("Voxy observed client world unload after {} client ticks.", clientTicks);
            }
            observedLevel = currentLevel;
            if (currentLevel != null) {
                LOGGER.info("Voxy observed client world load: {}.",
                        currentLevel.dimension().location());
            }
        }
    }

    private static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("voxy")
                .then(Commands.literal("status")
                        .executes(context -> {
                            ForgeVoxyConfig config = ForgeVoxyConfig.CONFIG;
                            Minecraft minecraft = Minecraft.getInstance();
                            String world = minecraft.level == null ? "none"
                                    : minecraft.level.dimension().location().toString();
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "Voxy Forge port: initialized; enabled=" + config.enabled
                                            + ", world=" + world
                                            + ", renderDistance=" + config.renderDistance
                                            + ". The original LOD engine is not connected yet."),
                                    false);
                            return 1;
                        })));
    }
}
