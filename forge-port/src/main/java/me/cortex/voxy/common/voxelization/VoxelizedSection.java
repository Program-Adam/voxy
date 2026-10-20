package me.cortex.voxy.common.voxelization;

/** A 16³ source section containing voxel data at each precomputed mip level. */
public final class VoxelizedSection {
    public final int x, y, z;
    private final long[] section;

    public VoxelizedSection(long[] section, int x, int y, int z) {
        if (section.length < 16 * 16 * 16 + 8 * 8 * 8 + 4 * 4 * 4 + 2 * 2 * 2 + 1)
            throw new IllegalArgumentException("Voxel section does not contain all mip levels");
        this.section = section;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    private static int getIdx(int x, int y, int z, int shiftBy, int size) {
        int mask = (1 << size) - 1;
        x = (x >> shiftBy) & mask;
        y = (y >> shiftBy) & mask;
        z = (z >> shiftBy) & mask;
        return (y << (size << 1)) | (z << size) | x;
    }

    public long get(int level, int x, int y, int z) {
        if (level < 0 || level > 4) throw new IllegalArgumentException("Invalid mip level " + level);
        int offset = switch (level) {
            case 0 -> 0;
            case 1 -> 1 << 12;
            case 2 -> (1 << 12) | (1 << 9);
            case 3 -> (1 << 12) | (1 << 9) | (1 << 6);
            case 4 -> (1 << 12) | (1 << 9) | (1 << 6) | (1 << 3);
            default -> throw new IllegalStateException();
        };
        return section[getIdx(x, y, z, 0, 4 - level) + offset];
    }

    public static VoxelizedSection createEmpty(int x, int y, int z) {
        return new VoxelizedSection(new long[16*16*16 + 8*8*8 + 4*4*4 + 2*2*2 + 1], x, y, z);
    }
}
