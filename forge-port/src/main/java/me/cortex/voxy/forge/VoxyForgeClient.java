package me.cortex.voxy.forge;

import me.cortex.voxy.forge.config.ForgeVoxyConfig;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * Client-only Forge lifecycle hooks. This class must only be loaded on the
 * physical client.
 */
public final class VoxyForgeClient {
    private VoxyForgeClient() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(VoxyForgeClient::registerClientCommands);
    }

    private static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("voxy")
                .then(Commands.literal("status")
                        .executes(context -> {
                            ForgeVoxyConfig config = ForgeVoxyConfig.CONFIG;
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "Voxy Forge port scaffold: config "
                                            + (config.enabled ? "enabled" : "disabled")
                                            + ", render distance " + config.renderDistance
                                            + ". LOD rendering is not wired yet."),
                                    false);
                            return 1;
                        })));
    }
}
