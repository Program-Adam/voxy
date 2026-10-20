package me.cortex.voxy.common.voxelization;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.common.world.other.Mipper;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.util.function.ToIntFunction;

/** Converts a Forge/Mojmap chunk section to Voxy's packed voxel format. */
public final class WorldConversionFactory {
    @FunctionalInterface
    public interface LightSupplier {
        byte get(int x, int y, int z, BlockState state);
    }

    private static final ThreadLocal<Reference2IntOpenHashMap<BlockState>> BLOCK_CACHE =
            ThreadLocal.withInitial(Reference2IntOpenHashMap::new);

    private WorldConversionFactory() {}

    public static VoxelizedSection convert(Mapper mapper, PalettedContainer<BlockState> blocks,
                                           PalettedContainer<Holder<Biome>> biomes,
                                           LightSupplier lightSupplier, int sx, int sy, int sz) {
        var blockCache = BLOCK_CACHE.get();
        blockCache.clear();
        VoxelizedSection section = VoxelizedSection.createEmpty(sx, sy, sz);
        long[] data = section.data();
        for (int oy = 0; oy < 4; oy++) {
            for (int oz = 0; oz < 4; oz++) {
                for (int ox = 0; ox < 4; ox++) {
                    int biomeId = mapper.getIdForBiome(biomes.get(ox, oy, oz));
                    for (int iy = 0; iy < 4; iy++) {
                        for (int iz = 0; iz < 4; iz++) {
                            for (int ix = 0; ix < 4; ix++) {
                                int x = (ox << 2) | ix, y = (oy << 2) | iy, z = (oz << 2) | iz;
                                BlockState state = blocks.get(x, y, z);
                                byte light = lightSupplier.get(x, y, z, state);
                                if (state.isAir() && light == 0) {
                                    data[index(x,y,z)] = Mapper.AIR;
                                } else {
                                    int blockId = state.isAir() ? 0 :
                                            blockCache.computeIfAbsent(state, mapper::getIdForBlockState);
                                    data[index(x,y,z)] = Mapper.composeMappingId(light, blockId, biomeId);
                                }
                            }
                        }
                    }
                }
            }
        }
        return section;
    }

    private static int index(int x, int y, int z) { return (y << 8) | (z << 4) | x; }

    private static int h(int x, int y, int z) { return (y << 6) | (z << 3) | x; }
    private static int i(int x, int y, int z) { return (y << 4) | (z << 2) | x; }
    private static int j(int x, int y, int z) { return (y << 2) | (z << 1) | x; }

    public static void mipSection(VoxelizedSection section, Mapper mapper) {
        long[] data = section.data();
        int offset1 = 4096, offset2 = offset1 + 512, offset3 = offset2 + 64, offset4 = offset3 + 8;
        int out = 0;
        for (int y=0;y<16;y+=2) for(int z=0;z<16;z+=2) for(int x=0;x<16;x+=2)
            data[offset1 + out++] = Mipper.mip(data[index(x,y,z)],data[index(x+1,y,z)],data[index(x,y,z+1)],data[index(x+1,y,z+1)],
                    data[index(x,y+1,z)],data[index(x+1,y+1,z)],data[index(x,y+1,z+1)],data[index(x+1,y+1,z+1)],mapper);
        out=0;
        for(int y=0;y<8;y+=2) for(int z=0;z<8;z+=2) for(int x=0;x<8;x+=2)
            data[offset2 + out++] = mip(data,offset1,x,y,z,mapper);
        out=0;
        for(int y=0;y<4;y+=2) for(int z=0;z<4;z+=2) for(int x=0;x<4;x+=2)
            data[offset3 + out++] = mip(data,offset2,x,y,z,mapper);
        data[offset4] = mip(data,offset3,0,0,0,mapper);
    }

    private static long mip(long[] data, int offset, int x, int y, int z, Mapper mapper) {
        int size = offset == 4096 ? 8 : offset == 4608 ? 4 : 2;
        return Mipper.mip(data[offset + ((y) * size * size + z * size + x)],
                data[offset + (y * size * size + z * size + x+1)],
                data[offset + (y * size * size + (z+1) * size + x)],
                data[offset + (y * size * size + (z+1) * size + x+1)],
                data[offset + ((y+1) * size * size + z * size + x)],
                data[offset + ((y+1) * size * size + z * size + x+1)],
                data[offset + ((y+1) * size * size + (z+1) * size + x)],
                data[offset + ((y+1) * size * size + (z+1) * size + x+1)], mapper);
    }
}
