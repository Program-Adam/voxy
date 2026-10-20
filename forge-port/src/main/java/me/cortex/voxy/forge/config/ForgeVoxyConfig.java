package me.cortex.voxy.forge.config;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Forge-side configuration storage for the port.
 *
 * This deliberately avoids FabricLoader so the Forge artifact has no Fabric
 * bootstrap dependency. The existing Voxy option names/defaults are preserved
 * where they are loader-independent.
 */
public final class ForgeVoxyConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("Voxy/Config");
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();

    public static final ForgeVoxyConfig CONFIG = loadOrCreate();

    public boolean enabled = true;
    public boolean ingestEnabled = true;
    public int qualityScale = 12;
    public int maxSections = 200_000;
    public int renderDistance = 128;
    public int geometryBufferSize = (1 << 30) / 8;
    public int ingestThreads = 2;
    public int savingThreads = 4;
    public int renderThreads = 5;
    public boolean useMeshShaderIfPossible = true;
    public String defaultSaveConfig = "default";

    private ForgeVoxyConfig() {
    }

    public static ForgeVoxyConfig loadOrCreate() {
        Path path = getConfigPath();
        if (Files.exists(path)) {
            try (var reader = Files.newBufferedReader(path)) {
                ForgeVoxyConfig config = GSON.fromJson(reader, ForgeVoxyConfig.class);
                if (config != null) {
                    if (config.defaultSaveConfig == null) {
                        config.defaultSaveConfig = "default";
                    }
                    return config;
                }
                LOGGER.warn("Voxy config was empty; using defaults");
            } catch (Exception e) {
                LOGGER.warn("Could not read Voxy config at {}; using defaults", path, e);
            }
        }
        return new ForgeVoxyConfig();
    }

    public void save() {
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(this));
            try {
                Files.move(temporary, path,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveUnsupported) {
                Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save Voxy config to {}", path, e);
        }
    }

    public static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get().resolve("voxy-config.json");
    }
}
