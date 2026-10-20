package me.cortex.voxy.forge;

import com.mojang.logging.LogUtils;
import me.cortex.voxy.forge.config.ForgeVoxyConfig;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Initial Forge entry point for the 1.20.1 port.
 *
 * Loader-specific initialization lives here. Rendering hooks and world
 * lifecycle integration are being migrated separately so Fabric classes are
 * not loaded by the Forge artifact.
 */
@Mod(VoxyForge.MOD_ID)
public final class VoxyForge {
    public static final String MOD_ID = "voxy";
    private static final Logger LOGGER = LogUtils.getLogger();

    public VoxyForge() {
        ForgeVoxyConfig config = ForgeVoxyConfig.CONFIG;
        LOGGER.info("Voxy Forge port bootstrap loaded (enabled={}, renderDistance={}). "
                + "LOD rendering is not wired yet.",
                config.enabled, config.renderDistance);

        MinecraftForge.EVENT_BUS.addListener(this::registerClientCommands);
    }

    private void registerClientCommands(RegisterClientCommandsEvent event) {
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
