# Forge 1.20.1 port implementation status

**Target:** Minecraft 1.20.1, Forge 47.4.15, Java 17  
**Work branch:** `port/forge-1.20.1-47.4.15`

This is an incremental port, not yet a playable Voxy build. The root Fabric project is kept as the source/reference baseline; Forge-specific work lives in `forge-port/`.

## Source baseline

Pinned source: [`16e37d9b776db4f84b7df7852c080328dfaa9998`](https://github.com/Program-Adam/voxy/commit/16e37d9b776db4f84b7df7852c080328dfaa9998), Minecraft 1.20.4 Fabric. The Forge subproject uses Mojang mappings for 1.20.1, so Minecraft-facing adapters require explicit mapping and lifecycle changes.

## Progress

| Phase | Status | Evidence / notes |
|---|---|---|
| Pin source baseline | Complete | Historical 1.20.4 source remains untouched |
| Isolated ForgeGradle project | Complete | Java 17, Forge 47.4.15, Minecraft 1.20.1 |
| Forge metadata and mod entry point | Initial implementation | Common entry point defers client-only initialization |
| Client diagnostic command and overlay | Initial implementation | `/voxy status`, client world lifecycle logging |
| Config persistence | Initial implementation | Forge config directory and original storage JSON format |
| Shared storage/util adaptation | Initial implementation | Generated copies with narrow mapping/API substitutions |
| World section model and active-section cache | Ported | Ref-counted 32³ sections, striped cache, cache counts |
| World-section serialization | Ported | Palette/hash persistence retains original on-disk layout; malformed payloads rejected |
| Core world engine | Initial port | Section acquire/load, mip updates from pre-voxelized sections, async dirty-section persistence and shutdown |
| World-engine tests | Added | Packed coordinates, section serialization round-trip, malformed payload handling |
| Block/biome mapper and mip generation | Not ported | Needs Mojang-mapping conversion and registry/NBT adaptation |
| Forge chunk ingestion | Not wired | Needs client chunk lifecycle hooks, block/biome conversion and lighting capture |
| Runtime dependency/native packaging | In progress | Verify assembled jar and actual client startup |
| Renderer and mixins | Not ported | Remap client renderer and mixin targets to Forge 1.20.1 |
| Access widener conversion | Not ported | Review access widener; use Forge access transformers/public alternatives |
| Runtime smoke tests | Not completed | Must run actual Forge client; compile success alone is insufficient |

## Next actions

1. Verify this world-engine slice compiles and its tests pass on CI.
2. Port the mapper, registry/NBT conversion, and mip-generation path.
3. Wire Forge chunk load/change/unload events into ingestion, including copied lighting data.
4. Convert access-widener needs and port renderer/cache/mixins.
5. Validate dependency packaging, then run world entry/exit, chunk updates, save/reload, LOD visibility and shutdown tests.

## Acceptance criteria

- [ ] Clean build and tests on JDK 17.
- [ ] Forge client launches without required Fabric APIs.
- [ ] Chunk changes feed the voxel-ingestion pipeline.
- [ ] Distant terrain is visibly rendered in a real Forge client.
- [ ] Data survives restart and existing storage formats remain readable.
- [ ] World leave and shutdown release worker threads and native/GPU resources.
- [ ] Core rendering works before optional integrations are enabled.

**Important:** the port now has an initial loader-independent world-engine slice, but it is still not playable: mapper/ingestion and the renderer are not connected, so distant terrain is not rendered yet.
