package me.cortex.voxy.forge.mixin;

import me.cortex.voxy.forge.VoxyForgeClient;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forge equivalent of Voxy's Fabric block-update hook. The callback is deliberately
 * lightweight: the actual chunk conversion is queued on Voxy's ingest workers.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Inject(
            method = "setBlockState",
            at = @At("RETURN")
    )
    private void voxy$onBlockStateChanged(BlockPos pos, BlockState state, boolean moving,
                                          CallbackInfoReturnable<BlockState> callback) {
        if (callback.getReturnValue() != null) {
            VoxyForgeClient.onChunkBlockChanged((LevelChunk) (Object) this);
        }
    }
}
