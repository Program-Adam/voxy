package me.cortex.voxy.common.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldSectionIdTest {
    @Test
    void packedCoordinatesRoundTripForPositiveAndNegativeValues() {
        int[][] coordinates = {
                {0, 0, 0, 0},
                {1, 1, 1, 1},
                {4, -1, -64, 1024},
                {15, 8_388_607, -128, -8_388_608},
                {2, -8_388_608, 127, 8_388_607}
        };

        for (int[] coordinate : coordinates) {
            int level = coordinate[0];
            int x = coordinate[1];
            int y = coordinate[2];
            int z = coordinate[3];
            long packed = WorldSectionId.getWorldSectionId(level, x, y, z);

            assertEquals(level, WorldSectionId.getLevel(packed), "level");
            assertEquals(x, WorldSectionId.getX(packed), "x");
            assertEquals(y, WorldSectionId.getY(packed), "y");
            assertEquals(z, WorldSectionId.getZ(packed), "z");
        }
    }
}
