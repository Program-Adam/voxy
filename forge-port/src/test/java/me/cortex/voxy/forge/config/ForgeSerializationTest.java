package me.cortex.voxy.forge.config;

import com.google.gson.JsonParser;
import me.cortex.voxy.common.config.Serialization;
import me.cortex.voxy.common.storage.config.StorageConfig;
import me.cortex.voxy.common.storage.other.CompressionStorageAdaptor;
import me.cortex.voxy.common.storage.rocksdb.RocksDBStorageBackend;
import me.cortex.voxy.common.storage.compressors.ZSTDCompressor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ForgeSerializationTest {
    @Test
    void existingDefaultStorageJsonRoundTrips() {
        Serialization.init();

        String json = """
                {
                  "TYPE": "CompressionAdaptor",
                  "delegate": {"TYPE": "RocksDB"},
                  "compressor": {"TYPE": "ZSTD", "compressionLevel": 7}
                }
                """;

        StorageConfig config = Serialization.GSON.fromJson(json, StorageConfig.class);
        CompressionStorageAdaptor.Config compression =
                assertInstanceOf(CompressionStorageAdaptor.Config.class, config);
        assertInstanceOf(RocksDBStorageBackend.Config.class, compression.delegate);
        ZSTDCompressor.Config compressor =
                assertInstanceOf(ZSTDCompressor.Config.class, compression.compressor);
        assertEquals(7, compressor.compressionLevel);

        String encoded = Serialization.GSON.toJson(config, StorageConfig.class);
        var encodedJson = JsonParser.parseString(encoded).getAsJsonObject();
        assertEquals("CompressionAdaptor", encodedJson.get("TYPE").getAsString());
        assertEquals("RocksDB", encodedJson.getAsJsonObject("delegate").get("TYPE").getAsString());
        assertEquals("ZSTD", encodedJson.getAsJsonObject("compressor").get("TYPE").getAsString());
    }
}
