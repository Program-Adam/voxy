package me.cortex.voxy.forge;

import com.mojang.logging.LogUtils;
import me.cortex.voxy.common.config.Serialization;
import me.cortex.voxy.forge.config.ForgeVoxyConfig;
import net.minecraftforge.fml.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Common-safe Forge entry point. Client-only classes are loaded only on the
 * physical client, so dedicated servers never resolve client event types.
 */
@Mod(VoxyForge.MOD_ID)
public final class VoxyForge {
    public static final String MOD_ID = "voxy";
    private static final Logger LOGGER = LogUtils.getLogger();

    public VoxyForge() {
        Serialization.init();
        ForgeVoxyConfig config = ForgeVoxyConfig.CONFIG;
        LOGGER.info("Voxy Forge port bootstrap loaded (enabled={}, renderDistance={}). "
                + "LOD rendering is not wired yet.",
                config.enabled, config.renderDistance);

        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> VoxyForgeClient::init);
    }
}
