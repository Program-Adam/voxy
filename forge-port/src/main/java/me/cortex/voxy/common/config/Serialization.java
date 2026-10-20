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

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * Forge-compatible replacement for Fabric's config class discovery.
 *
 * Storage config implementations are discovered from this mod's classpath,
 * then registered with Gson using the same TYPE discriminator format as the
 * Fabric build so existing per-world storage JSON remains readable.
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

    public static synchronized void init() {
        if (initialized) {
            return;
        }

        Map<Class<?>, GsonConfigSerialization<?>> serializers = new HashMap<>();
        Set<String> classNames = collectAllClasses("me.cortex.voxy");
        int registered = 0;

        for (String className : classNames) {
            String lowerName = className.toLowerCase();
            if (!lowerName.contains("config") || lowerName.contains("mixin")
                    || className.endsWith("VoxyConfig") || className.endsWith("Serialization")) {
                continue;
            }

            try {
                Class<?> implementation = Class.forName(className, true, Serialization.class.getClassLoader());
                if (Modifier.isAbstract(implementation.getModifiers())) {
                    continue;
                }

                Class<?> parent = implementation;
                while ((parent = parent.getSuperclass()) != null) {
                    if (!CONFIG_TYPES.contains(parent)) {
                        continue;
                    }

                    Method typeNameMethod = implementation.getMethod("getConfigTypeName");
                    String typeName = (String) typeNameMethod.invoke(null);
                    GsonConfigSerialization serializer = serializers.computeIfAbsent(
                            parent, key -> new GsonConfigSerialization(key));
                    serializer.register(typeName, implementation);
                    registered++;
                    break;
                }
            } catch (ClassNotFoundException ignored) {
                // Optional classes may be absent in development environments.
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new IllegalStateException("Failed to register storage config " + className, e);
            }
        }

        GsonBuilder builder = new GsonBuilder();
        serializers.values().forEach(builder::registerTypeAdapterFactory);
        GSON = builder.create();
        initialized = true;
        System.out.println("Voxy Forge registered " + registered + " storage config types");
    }

    private static Set<String> collectAllClasses(String packageName) {
        Set<String> classNames = new LinkedHashSet<>();
        String packagePath = packageName.replace('.', '/');
        ClassLoader loader = Serialization.class.getClassLoader();

        try {
            Enumeration<URL> roots = loader.getResources(packagePath);
            while (roots.hasMoreElements()) {
                URL root = roots.nextElement();
                if ("file".equals(root.getProtocol())) {
                    Path directory = Path.of(root.toURI());
                    try (var files = Files.walk(directory)) {
                        files.filter(path -> path.toString().endsWith(".class"))
                                .forEach(path -> {
                                    String relative = directory.relativize(path).toString()
                                            .replace(path.getFileSystem().getSeparator(), ".");
                                    classNames.add(packageName + "." + relative.substring(0, relative.length() - 6));
                                });
                    }
                } else if ("jar".equals(root.getProtocol())) {
                    JarURLConnection connection = (JarURLConnection) root.openConnection();
                    try (JarFile jar = connection.getJarFile()) {
                        var entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            String name = entries.nextElement().getName();
                            if (name.startsWith(packagePath + "/") && name.endsWith(".class")) {
                                classNames.add(name.substring(0, name.length() - 6).replace('/', '.'));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not scan Voxy config classes under " + packageName, e);
        }

        return classNames;
    }
}
