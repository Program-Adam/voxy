package me.cortex.voxy.forge;

import com.mojang.logging.LogUtils;
import me.cortex.voxy.common.config.Serialization;
import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.compressors.ZSTDCompressor;
import me.cortex.voxy.common.storage.other.CompressionStorageAdaptor;
import me.cortex.voxy.common.storage.rocksdb.RocksDBStorageBackend;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.forge.config.ForgeVoxyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Client lifecycle, persistent world engine, and chunk-ingestion hooks. */
public final class VoxyForgeClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static ClientLevel observedLevel;
    private static long clientTicks;
    private static WorldEngine worldEngine;
    private static final ConcurrentLinkedQueue<LevelChunk> pendingChunks = new ConcurrentLinkedQueue<>();

    private static final IGuiOverlay STATUS_OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        ForgeVoxyConfig config = ForgeVoxyConfig.CONFIG;
        if (!config.enabled || Minecraft.getInstance().options.hideGui) return;
        Minecraft minecraft = Minecraft.getInstance();
        String world = minecraft.level == null ? "No world" : minecraft.level.dimension().location().toString();
        String status = "Voxy Forge | " + world + " | ingest queue: "
                + (worldEngine == null ? 0 : worldEngine.ingestService.getTaskCount());
        graphics.drawString(minecraft.font, Component.literal(status), 8, 8, 0xFFFFFF, true);
    };

    private VoxyForgeClient() {}

    public static void init() {
        Serialization.init();
        ForgeVoxyConfig config = ForgeVoxyConfig.CONFIG;
        if (!java.nio.file.Files.exists(ForgeVoxyConfig.getConfigPath())) config.save();
        LOGGER.info("Voxy Forge initialized (enabled={}, renderDistance={}, ingestEnabled={}).",
                config.enabled, config.renderDistance, config.ingestEnabled);
        MinecraftForge.EVENT_BUS.addListener(VoxyForgeClient::registerClientCommands);
        MinecraftForge.EVENT_BUS.addListener(VoxyForgeClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(VoxyForgeClient::onChunkLoad);
        MinecraftForge.EVENT_BUS.addListener(VoxyForgeClient::onChunkUnload);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(VoxyForgeClient::registerOverlays);
    }

    private static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("voxy_status", STATUS_OVERLAY);
    }

    /** Called by the LevelChunk mixin after a successful block-state mutation on the client. */
    public static void onChunkBlockChanged(LevelChunk chunk) {
        if (chunk == null || !(chunk.getLevel() instanceof ClientLevel level)
                || level != Minecraft.getInstance().level) return;
        ForgeVoxyConfig config = ForgeVoxyConfig.CONFIG;
        WorldEngine engine = worldEngine;
        if (config.enabled && config.ingestEnabled && engine != null && observedLevel == level) {
            engine.ingestService.enqueue(chunk);
        }
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ClientLevel level) || level != Minecraft.getInstance().level) return;
        ForgeVoxyConfig config = ForgeVoxyConfig.CONFIG;
        if (!config.enabled || !config.ingestEnabled) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        WorldEngine engine = worldEngine;
        if (engine != null && observedLevel == level) engine.ingestService.enqueue(chunk);
        else pendingChunks.offer(chunk);
    }

    /**
     * Preserve the last state of a client chunk before it leaves the client cache.
     * This mirrors Voxy's Fabric chunk-cache drop hook without relying on a Fabric API.
     */
    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ClientLevel level) || level != Minecraft.getInstance().level) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        ForgeVoxyConfig config = ForgeVoxyConfig.CONFIG;
        WorldEngine engine = worldEngine;
        if (config.enabled && config.ingestEnabled && engine != null && observedLevel == level) {
            engine.ingestService.enqueue(chunk);
        }
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        clientTicks++;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel currentLevel = minecraft.level;
        boolean shouldRun = currentLevel != null && ForgeVoxyConfig.CONFIG.enabled;
        if (currentLevel == observedLevel && ((shouldRun && worldEngine != null) || (!shouldRun && worldEngine == null))) return;

        if (worldEngine != null) {
            LOGGER.info("Closing Voxy world engine after {} client ticks.", clientTicks);
            try { worldEngine.close(); }
            catch (Throwable error) { LOGGER.error("Failed to close Voxy world engine cleanly", error); }
            worldEngine = null;
        }
        observedLevel = currentLevel;
        if (currentLevel == null || !ForgeVoxyConfig.CONFIG.enabled) {
            LOGGER.info(currentLevel == null ? "Voxy observed client world unload." : "Voxy is disabled; world ingestion is stopped.");
            return;
        }

        try {
            StorageBackend storage = createStorage(minecraft, currentLevel);
            ForgeVoxyConfig config = ForgeVoxyConfig.CONFIG;
            worldEngine = new WorldEngine(storage,
                    Math.max(1, config.ingestThreads), Math.max(1, config.savingThreads), 4);
            LOGGER.info("Voxy world engine started for dimension {}.", currentLevel.dimension().location());
            LevelChunk chunk;
            while ((chunk = pendingChunks.poll()) != null) {
                if (chunk.getLevel() == currentLevel) worldEngine.ingestService.enqueue(chunk);
            }
        } catch (Throwable error) {
            LOGGER.error("Could not start Voxy world engine; chunk ingestion disabled for this world", error);
            worldEngine = null;
        }
    }

    public static WorldEngine getWorldEngine() { return worldEngine; }

    private static StorageBackend createStorage(Minecraft minecraft, ClientLevel level) {
        Path worldRoot;
        var server = minecraft.getSingleplayerServer();
        if (server != null) {
            worldRoot = server.getWorldPath(LevelResource.ROOT);
        } else {
            String serverId = minecraft.getCurrentServer() == null ? "unknown-server"
                    : minecraft.getCurrentServer().ip.replaceAll("[^a-zA-Z0-9._-]", "_");
            worldRoot = minecraft.gameDirectory.toPath().resolve("voxy-forge").resolve(serverId);
        }
        String dimension = level.dimension().location().toString().replace(':', '_').replace('/', '_');
        Path storagePath = worldRoot.resolve("voxy").resolve(dimension).resolve("storage");
        StorageBackend raw = new RocksDBStorageBackend(storagePath.toAbsolutePath().toString());
        return new CompressionStorageAdaptor(new ZSTDCompressor(7), raw);
    }

    private static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("voxy")
                .then(Commands.literal("status").executes(context -> {
                    ForgeVoxyConfig config = ForgeVoxyConfig.CONFIG;
                    Minecraft minecraft = Minecraft.getInstance();
                    String world = minecraft.level == null ? "none" : minecraft.level.dimension().location().toString();
                    WorldEngine engine = worldEngine;
                    context.getSource().sendSuccess(() -> Component.literal(
                            "Voxy Forge: enabled=" + config.enabled + ", world=" + world
                                    + ", engine=" + (engine == null ? "stopped" : "running")
                                    + ", queuedChunks=" + (engine == null ? 0 : engine.ingestService.getTaskCount())
                                    + ". Experimental coarse LOD renderer active; full GPU renderer remains unported."),
                            false);
                    return 1;
                })));
    }
}
