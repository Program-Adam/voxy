package me.cortex.voxy.forge;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Initial Forge entry point for the 1.20.1 port.
 *
 * The Fabric bootstrap is intentionally not invoked here. Platform lifecycle,
 * rendering hooks, persistence and optional integrations will be migrated in
 * separate, testable steps.
 */
@Mod(VoxyForge.MOD_ID)
public final class VoxyForge {
    public static final String MOD_ID = "voxy";
    private static final Logger LOGGER = LogUtils.getLogger();

    public VoxyForge() {
        LOGGER.info("Voxy Forge port bootstrap loaded. Rendering migration is not implemented yet.");
    }
}
