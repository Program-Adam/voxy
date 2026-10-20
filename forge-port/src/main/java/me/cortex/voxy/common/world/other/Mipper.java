package me.cortex.voxy.common.world.other;

/** Selects a representative voxel for an eight-voxel mip group. */
public final class Mipper {
    private Mipper() {}

    public static long mip(long i000, long i100, long i001, long i101,
                           long i010, long i110, long i011, long i111, Mapper mapper) {
        if (!Mapper.isAir(i111)) return i111;
        if (!Mapper.isAir(i110)) return i110;
        if (!Mapper.isAir(i011)) return i011;
        if (!Mapper.isAir(i010)) return i010;
        if (!Mapper.isAir(i101)) return i101;
        if (!Mapper.isAir(i100)) return i100;
        if (!Mapper.isAir(i001)) return i001;
        if (!Mapper.isAir(i000)) return i000;

        int blockLight = ((Mapper.getLightId(i000) & 0xF0) + (Mapper.getLightId(i001) & 0xF0)
                + (Mapper.getLightId(i010) & 0xF0) + (Mapper.getLightId(i011) & 0xF0)
                + (Mapper.getLightId(i100) & 0xF0) + (Mapper.getLightId(i101) & 0xF0)
                + (Mapper.getLightId(i110) & 0xF0) + (Mapper.getLightId(i111) & 0xF0)) / 8;
        int skyLight = (int) Math.ceil(((Mapper.getLightId(i000) & 0x0F) + (Mapper.getLightId(i001) & 0x0F)
                + (Mapper.getLightId(i010) & 0x0F) + (Mapper.getLightId(i011) & 0x0F)
                + (Mapper.getLightId(i100) & 0x0F) + (Mapper.getLightId(i101) & 0x0F)
                + (Mapper.getLightId(i110) & 0x0F) + (Mapper.getLightId(i111) & 0x0F)) / 8.0);
        return Mapper.withLight(i111, (blockLight & 0xF0) | (skyLight & 0x0F));
    }
}
