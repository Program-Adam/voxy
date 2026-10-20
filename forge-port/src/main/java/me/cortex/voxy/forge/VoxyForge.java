package me.cortex.voxy.forge;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Common-safe Forge entry point. Client-only classes are loaded only on the
 * physical client, so dedicated servers never resolve client event types or
 * initialize client-side storage/rendering services.
 */
@Mod(VoxyForge.MOD_ID)
public final class VoxyForge {
    public static final String MOD_ID = "voxy";
    private static final Logger LOGGER = LogUtils.getLogger();

    public VoxyForge() {
        LOGGER.info("Voxy Forge mod container initialized.");
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> VoxyForgeClient::init);
    }
}
