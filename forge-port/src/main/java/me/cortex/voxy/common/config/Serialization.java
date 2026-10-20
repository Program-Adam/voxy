package me.cortex.voxy.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import me.cortex.voxy.common.storage.config.CompressorConfig;
import me.cortex.voxy.common.storage.config.StorageConfig;
import me.cortex.voxy.common.storage.compressors.ZSTDCompressor;
import me.cortex.voxy.common.storage.inmemory.MemoryStorageBackend;
import me.cortex.voxy.common.storage.lmdb.LMDBStorageBackend;
import me.cortex.voxy.common.storage.other.BasicPathInsertionConfig;
import me.cortex.voxy.common.storage.other.CompressionStorageAdaptor;
import me.cortex.voxy.common.storage.other.ConditionalStorageBackendConfig;
import me.cortex.voxy.common.storage.other.FragmentedStorageBackendAdaptor;
import me.cortex.voxy.common.storage.other.ReadonlyCachingLayer;
import me.cortex.voxy.common.storage.other.TranslocatingStorageAdaptor;
import me.cortex.voxy.common.storage.redis.RedisStorageBackend;
import me.cortex.voxy.common.storage.rocksdb.RocksDBStorageBackend;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Forge-compatible replacement for Fabric's config class discovery.
 *
 * Config implementations are registered explicitly rather than discovered
 * through FabricLoader, preserving the existing TYPE discriminator format
 * while avoiding loader-specific classpath scanning in production Forge.
 */
public final class Serialization {
    public static final Set<Class<?>> CONFIG_TYPES = new HashSet<>();
    public static Gson GSON;
    private static boolean initialized;

    private Serialization() {
    }

    private static final class GsonConfigSerialization<T> implements TypeAdapterFactory {
        private static final String TYPE_FIELD = "TYPE";
        private final Class<T> baseType;
        private final Map<String, Class<? extends T>> nameToType = new HashMap<>();
        private final Map<Class<? extends T>, String> typeToName = new HashMap<>();

        private GsonConfigSerialization(Class<T> baseType) {
            this.baseType = baseType;
        }

        private void register(String name, Class<? extends T> type) {
            if (nameToType.put(name, type) != null || typeToName.put(type, name) != null) {
                throw new IllegalStateException("Duplicate storage config registration: " + name);
            }
        }

        private T deserialize(Gson gson, JsonElement json) {
            JsonObject object = json.getAsJsonObject();
            JsonElement typeName = object.remove(TYPE_FIELD);
            if (typeName == null) {
                throw new IllegalStateException("Storage config is missing its TYPE field");
            }
            Class<? extends T> implementation = nameToType.get(typeName.getAsString());
            if (implementation == null) {
                throw new IllegalStateException("Unknown storage config type: " + typeName.getAsString());
            }
            return gson.getDelegateAdapter(this, TypeToken.get(implementation)).fromJsonTree(object);
        }

        private JsonElement serialize(Gson gson, T value) {
            String name = typeToName.get(value.getClass());
            if (name == null) {
                throw new IllegalStateException("Unregistered storage config type: " + value.getClass().getName());
            }
            JsonObject result = new JsonObject();
            result.addProperty(TYPE_FIELD, name);
            JsonObject fields = gson.getDelegateAdapter(this, TypeToken.get((Class<T>) value.getClass()))
                    .toJsonTree(value).getAsJsonObject();
            fields.asMap().forEach(result::add);
            return result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <X> TypeAdapter<X> create(Gson gson, TypeToken<X> type) {
            if (!baseType.isAssignableFrom(type.getRawType())) {
                return null;
            }
            TypeAdapter<JsonElement> jsonAdapter = gson.getAdapter(JsonElement.class);
            return (TypeAdapter<X>) new TypeAdapter<T>() {
                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    jsonAdapter.write(out, GsonConfigSerialization.this.serialize(gson, value));
                }

                @Override
                public T read(JsonReader in) throws IOException {
                    return GsonConfigSerialization.this.deserialize(gson, jsonAdapter.read(in));
                }
            };
        }
    }

    private static <T> void register(GsonConfigSerialization<T> serializer, Class<? extends T> implementation) {
        try {
            Method method = implementation.getMethod("getConfigTypeName");
            serializer.register((String) method.invoke(null), implementation);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Invalid config implementation: " + implementation.getName(), e);
        }
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }

        GsonConfigSerialization<StorageConfig> storage = new GsonConfigSerialization<>(StorageConfig.class);
        register(storage, RocksDBStorageBackend.Config.class);
        register(storage, LMDBStorageBackend.Config.class);
        register(storage, RedisStorageBackend.Config.class);
        register(storage, MemoryStorageBackend.Config.class);
        register(storage, BasicPathInsertionConfig.class);
        register(storage, CompressionStorageAdaptor.Config.class);
        register(storage, ConditionalStorageBackendConfig.class);
        register(storage, FragmentedStorageBackendAdaptor.Config.class);
        register(storage, ReadonlyCachingLayer.Config.class);
        register(storage, TranslocatingStorageAdaptor.Config.class);

        GsonConfigSerialization<CompressorConfig> compressors = new GsonConfigSerialization<>(CompressorConfig.class);
        register(compressors, ZSTDCompressor.Config.class);

        GSON = new GsonBuilder()
                .registerTypeAdapterFactory(storage)
                .registerTypeAdapterFactory(compressors)
                .create();
        initialized = true;
    }
}
