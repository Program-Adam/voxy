package me.cortex.voxy.common.world;

/**
 * Stable packed section-coordinate helpers shared by Voxy's storage adaptors.
 *
 * Kept separate from WorldEngine so the persistence layer can compile without
 * pulling Minecraft client/world classes into the Forge port.
 */
public final class WorldSectionId {
    private WorldSectionId() {
    }

    public static long getWorldSectionId(int level, int x, int y, int z) {
        return ((long) level << 60)
                | ((long) (y & 0xFF) << 52)
                | ((long) (z & ((1 << 24) - 1)) << 28)
                | ((long) (x & ((1 << 24) - 1)) << 4);
    }

    public static int getLevel(long id) {
        return (int) ((id >> 60) & 0xF);
    }

    public static int getX(long id) {
        return (int) ((id << 36) >> 40);
    }

    public static int getY(long id) {
        return (int) ((id << 4) >> 56);
    }

    public static int getZ(long id) {
        return (int) ((id << 12) >> 40);
    }
}
