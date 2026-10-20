# Forge 1.20.1 port implementation status

**Target:** Minecraft 1.20.1, Forge 47.4.15, Java 17  
**Work branch:** `port/forge-1.20.1-47.4.15`

This is an incremental port, not yet a playable Voxy build. The root Fabric project is kept as the source/reference baseline; Forge-specific work lives in `forge-port/`.

## Source baseline

Pinned source: [`16e37d9b776db4f84b7df7852c080328dfaa9998`](https://github.com/Program-Adam/voxy/commit/16e37d9b776db4f84b7df7852c080328dfaa9998), Minecraft 1.20.4 Fabric. The source uses Yarn names and Fabric APIs; the ForgeGradle subproject uses Mojang mappings for 1.20.1, so rendering and mixin code cannot be copied verbatim.

## Progress

| Phase | Status | Evidence / notes |
|---|---|---|
| Pin source baseline | Complete | Commit above |
| Isolated ForgeGradle project | Complete | Java 17, Forge 47.4.15, Minecraft 1.20.1 |
| Forge metadata and mod entry point | Initial implementation | Common entry point defers client-only initialization |
| Client diagnostic command | Initial implementation | `/voxy status` reports scaffold/config state |
| Config persistence | Initial implementation | `FMLPaths.CONFIGDIR`, `voxy-config.json`, atomic replace where supported |
| Shared storage/util source adaptation | Initial implementation | Generated copies with narrow mapping/API substitutions |
| Storage config serialization test | Implemented | Round-trip test for default compression/RocksDB/ZSTD config |
| Dependency/native packaging | In progress | LWJGL bindings plus native resources, RocksDB/Jedis runtime dependencies |
| Forge compile and test | In progress | CI caught and fixed a wrong `Dist` import; latest run is validating the fix |
| Client world lifecycle / ingestion | Not started | Need Forge client lifecycle hooks and chunk-change ingestion |
| LOD world engine | Not started | Port world section, section cache, mapper/mipper and save/load service |
| Render hooks / mixins | Not started | Re-map 1.20.4 Yarn targets to 1.20.1 Mojang mappings; validate injection points |
| Access widener conversion | Not started | Review `voxy.accesswidener`; use Forge access transformers or public alternatives |
| Optional integrations | Not started | Sodium, Iris, Nvidium and similar integrations require separate compatibility work |
| Runtime smoke tests | Not started | Must run an actual Forge client; compile success alone is insufficient |

## Next actions

1. Get a clean `gradle -p forge-port build` on CI, including tests and JAR assembly.
2. Inspect the built JAR for Forge metadata and packaged dependencies/native resources; launch the client and verify the diagnostic command.
3. Port the loader-independent world engine in small compilable slices, keeping Minecraft-specific adapters isolated.
4. Port chunk ingestion and save/load lifecycle, preserving the existing on-disk storage schema.
5. Convert access-widener needs and port the core `WorldRenderer` / client chunk mixins against Forge 1.20.1 mappings.
6. Bring up a basic LOD renderer before attempting optional Sodium/Iris/Nvidium support.
7. Test world entry/exit, chunk updates, LOD visibility, save/reload, shutdown, and native/GPU resource cleanup.

## Acceptance criteria

- [ ] Clean build and tests on JDK 17.
- [ ] Forge client launches with no required Fabric classes/APIs.
- [ ] A world can be entered and exited repeatedly without leaking worker threads or GPU/database resources.
- [ ] Chunk changes feed the LOD pipeline and far terrain is visible.
- [ ] Data survives restart and existing storage formats remain readable.
- [ ] Core rendering works before optional integrations are enabled.
- [ ] Tested loader/mod compatibility is documented accurately.

**Important:** the current scaffold is not yet a playable port. Do not distribute it as a functional LOD renderer until the runtime criteria above pass.
