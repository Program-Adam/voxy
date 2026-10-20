package me.cortex.voxy.forge;

import com.mojang.logging.LogUtils;
import me.cortex.voxy.forge.config.ForgeVoxyConfig;
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
    }
}
