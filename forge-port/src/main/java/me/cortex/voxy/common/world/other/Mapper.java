package me.cortex.voxy.common.world.other;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.cortex.voxy.common.storage.StorageBackend;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.nbt.Tag;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Persistent block-state and biome ID mappings. The packed voxel ID layout is unchanged. */
public final class Mapper {
    private static final int BLOCK_STATE_TYPE = 1, BIOME_TYPE = 2;
    public static final long UNKNOWN_MAPPING = -1, AIR = 0;
    private final StorageBackend storage;
    private final Map<BlockState, StateEntry> block2stateEntry = new ConcurrentHashMap<>(2000);
    private final ObjectArrayList<StateEntry> blockId2stateEntry = new ObjectArrayList<>();
    private final Map<String, BiomeEntry> biome2biomeEntry = new ConcurrentHashMap<>(2000);
    private final ObjectArrayList<BiomeEntry> biomeId2biomeEntry = new ObjectArrayList<>();
    private volatile Consumer<StateEntry> newStateCallback;
    private volatile Consumer<BiomeEntry> newBiomeCallback;

    public Mapper(StorageBackend storage) {
        this.storage = Objects.requireNonNull(storage);
        StateEntry air = new StateEntry(0, Blocks.AIR.defaultBlockState());
        block2stateEntry.put(air.state, air);
        blockId2stateEntry.add(air);
        loadFromStorage();
    }

    public static boolean isAir(long id) { return ((id >> 27) & ((1 << 20) - 1)) == 0; }
    public static int getBlockId(long id) { return (int)((id >> 27) & ((1 << 20) - 1)); }
    public static int getBiomeId(long id) { return (int)((id >> 47) & 0x1FF); }
    public static int getLightId(long id) { return (int)((id >> 56) & 0xFF); }
    public static long withLight(long id, int light) { return (id & ~(0xFFL << 56)) | (Integer.toUnsignedLong(light) << 56); }

    public void setCallbacks(Consumer<StateEntry> stateCallback, Consumer<BiomeEntry> biomeCallback) {
        newStateCallback = stateCallback; newBiomeCallback = biomeCallback;
    }

    private void loadFromStorage() {
        Int2ObjectOpenHashMap<byte[]> mappings = storage.getIdMappingsData();
        List<StateEntry> states = new ArrayList<>();
        List<BiomeEntry> biomes = new ArrayList<>();
        for (var entry : mappings.int2ObjectEntrySet()) {
            int type = entry.getIntKey() >>> 30;
            int id = entry.getIntKey() & ((1 << 30) - 1);
            if (type == BLOCK_STATE_TYPE) {
                StateEntry decoded = StateEntry.deserialize(id, entry.getValue());
                if (decoded.state.isAir()) continue;
                if (block2stateEntry.put(decoded.state, decoded) != null)
                    throw new IllegalStateException("Duplicate block-state mapping");
                states.add(decoded);
            } else if (type == BIOME_TYPE) {
                BiomeEntry decoded = BiomeEntry.deserialize(id, entry.getValue());
                if (biome2biomeEntry.put(decoded.biome, decoded) != null)
                    throw new IllegalStateException("Duplicate biome mapping");
                biomes.add(decoded);
            } else {
                throw new IllegalStateException("Unknown mapping type " + type);
            }
        }
        states.sort(Comparator.comparingInt(a -> a.id));
        for (StateEntry entry : states) {
            if (blockId2stateEntry.size() != entry.id) throw new IllegalStateException("Non-contiguous block mapping IDs");
            blockId2stateEntry.add(entry);
        }
        biomes.sort(Comparator.comparingInt(a -> a.id));
        for (BiomeEntry entry : biomes) {
            if (biomeId2biomeEntry.size() != entry.id) throw new IllegalStateException("Non-contiguous biome mapping IDs");
            biomeId2biomeEntry.add(entry);
        }
    }

    public long getBaseId(byte light, BlockState state, Holder<Biome> biome) {
        if (state.isAir()) return Byte.toUnsignedLong(light) << 56;
        return composeMappingId(light, getIdForBlockState(state), getIdForBiome(biome));
    }

    public BlockState getBlockStateFromBlockId(int id) { return blockId2stateEntry.get(id).state; }

    public int getIdForBlockState(BlockState state) {
        StateEntry existing = block2stateEntry.get(state);
        if (existing != null) return existing.id;
        synchronized (this) {
            existing = block2stateEntry.get(state);
            if (existing != null) return existing.id;
            StateEntry entry = new StateEntry(blockId2stateEntry.size(), state);
            persist(entry.id | (BLOCK_STATE_TYPE << 30), entry.serialize());
            blockId2stateEntry.add(entry);
            block2stateEntry.put(state, entry);
            Consumer<StateEntry> callback = newStateCallback;
            if (callback != null) callback.accept(entry);
            return entry.id;
        }
    }

    public int getIdForBiome(Holder<Biome> biome) {
        ResourceLocation key = biome.unwrapKey().orElseThrow(() ->
                new IllegalArgumentException("Cannot persist a biome without a registry key")).location();
        String name = key.toString();
        BiomeEntry existing = biome2biomeEntry.get(name);
        if (existing != null) return existing.id;
        synchronized (this) {
            existing = biome2biomeEntry.get(name);
            if (existing != null) return existing.id;
            BiomeEntry entry = new BiomeEntry(biomeId2biomeEntry.size(), name);
            persist(entry.id | (BIOME_TYPE << 30), entry.serialize());
            biomeId2biomeEntry.add(entry);
            biome2biomeEntry.put(name, entry);
            Consumer<BiomeEntry> callback = newBiomeCallback;
            if (callback != null) callback.accept(entry);
            return entry.id;
        }
    }

    private void persist(int key, byte[] bytes) {
        ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
        try { buffer.put(bytes).flip(); storage.putIdMapping(key, buffer); }
        finally { MemoryUtil.memFree(buffer); }
    }

    public static long composeMappingId(byte light, int blockId, int biomeId) {
        if (blockId == 0) return Byte.toUnsignedLong(light) << 56;
        return (Byte.toUnsignedLong(light) << 56) | (Integer.toUnsignedLong(biomeId) << 47)
                | (Integer.toUnsignedLong(blockId) << 27);
    }

    public StateEntry[] getStateEntries() { synchronized (this) { return blockId2stateEntry.toArray(StateEntry[]::new); } }
    public BiomeEntry[] getBiomeEntries() { synchronized (this) { return biomeId2biomeEntry.toArray(BiomeEntry[]::new); } }

    public void forceResaveStates() {
        synchronized (this) {
            for (StateEntry entry : blockId2stateEntry) if (entry.id != 0)
                persist(entry.id | (BLOCK_STATE_TYPE << 30), entry.serialize());
            for (BiomeEntry entry : biomeId2biomeEntry)
                persist(entry.id | (BIOME_TYPE << 30), entry.serialize());
        }
        storage.flush();
    }

    public static final class StateEntry {
        public final int id; public final BlockState state;
        public StateEntry(int id, BlockState state) { this.id=id; this.state=state; }
        public byte[] serialize() {
            CompoundTag root = new CompoundTag(); root.putInt("id", id);
            Tag stateTag = BlockState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow(false, message -> {});
            root.put("block_state", stateTag);
            return compressed(root);
        }
        public static StateEntry deserialize(int id, byte[] data) {
            CompoundTag root = read(data);
            if (root.getInt("id") != id) throw new IllegalStateException("Encoded block ID mismatch");
            BlockState state = BlockState.CODEC.parse(NbtOps.INSTANCE, root.get("block_state"))
                    .getOrThrow(false, message -> {});
            return new StateEntry(id, state);
        }
    }

    public static final class BiomeEntry {
        public final int id; public final String biome;
        public BiomeEntry(int id, String biome) { this.id=id; this.biome=biome; }
        public byte[] serialize() { CompoundTag root = new CompoundTag(); root.putInt("id", id); root.putString("biome_id", biome); return compressed(root); }
        public static BiomeEntry deserialize(int id, byte[] data) {
            CompoundTag root = read(data);
            if (root.getInt("id") != id) throw new IllegalStateException("Encoded biome ID mismatch");
            ResourceLocation.tryParse(root.getString("biome_id"));
            return new BiomeEntry(id, root.getString("biome_id"));
        }
    }

    private static byte[] compressed(CompoundTag tag) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) { NbtIo.writeCompressed(tag, out); return out.toByteArray(); }
        catch (Exception e) { throw new IllegalStateException("Could not serialize mapping", e); }
    }
    private static CompoundTag read(byte[] bytes) {
        try { return NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.UNLIMITED); }
        catch (Exception e) { throw new IllegalStateException("Could not deserialize mapping", e); }
    }
}
