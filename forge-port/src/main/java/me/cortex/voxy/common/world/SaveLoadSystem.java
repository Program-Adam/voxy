package me.cortex.voxy.common.world;

import it.unimi.dsi.fastutil.longs.Long2ShortOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.util.zstd.Zstd.*;

/** Stable palette-based section serialization used by Voxy's existing storage format. */
public final class SaveLoadSystem {
    private SaveLoadSystem() {}

    public static ByteBuffer serialize(WorldSection section) {
        long[] data = section.copyData();
        short[] compressed = new short[data.length];
        Long2ShortOpenHashMap palette = new Long2ShortOpenHashMap(data.length);
        LongArrayList paletteValues = new LongArrayList();
        for (int i = 0; i < data.length; i++) {
            long value = data[i];
            compressed[i] = palette.computeIfAbsent(value, id -> {
                paletteValues.add(id);
                return (short) (paletteValues.size() - 1);
            });
        }

        long[] lut = paletteValues.toLongArray();
        ByteBuffer raw = MemoryUtil.memAlloc(compressed.length * 2 + lut.length * 8 + 512);
        long hash = section.key ^ (lut.length * 1293481298141L);
        raw.putLong(section.key).putInt(lut.length);
        for (long value : lut) {
            raw.putLong(value);
            hash = (hash * 1230987149811L + 12831) ^ value;
        }
        for (int i = 0; i < compressed.length; i++) {
            short value = compressed[i];
            raw.putShort(value);
            hash = (hash * 1230987149811L + 12831) ^ ((value * 1827631L) ^ data[i]);
        }
        raw.putLong(hash).flip();
        return raw;
    }

    public static boolean deserialize(WorldSection section, ByteBuffer data, boolean ignoreMismatchPosition) {
        try {
            long key = data.getLong();
            int lutLength = data.getInt();
            if (lutLength < 1 || lutLength > section.data.length || data.remaining() < lutLength * 8L + section.data.length * 2L + 8L)
                return false;

            long[] lut = new long[lutLength];
            long hash = key ^ (lut.length * 1293481298141L);
            for (int i = 0; i < lutLength; i++) {
                lut[i] = data.getLong();
                hash = (hash * 1230987149811L + 12831) ^ lut[i];
            }
            if (!ignoreMismatchPosition && section.key != key) return false;

            for (int i = 0; i < section.data.length; i++) {
                short lutId = data.getShort();
                if (lutId < 0 || lutId >= lut.length) return false;
                section.data[i] = lut[lutId];
                hash = (hash * 1230987149811L + 12831) ^ ((lutId * 1827631L) ^ section.data[i]);
            }
            return data.getLong() == hash && !data.hasRemaining();
        } catch (RuntimeException malformed) {
            return false;
        }
    }
}
