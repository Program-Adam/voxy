# Forge 1.20.1 port implementation status

**Target:** Minecraft 1.20.1, Forge 47.4.15, Java 17  
**Work branch:** `port/forge-1.20.1-47.4.15`

This is an incremental port. The root Fabric project is kept as the source/reference baseline; Forge-specific work lives in `forge-port/`.

## Progress

| Phase | Status | Evidence / notes |
|---|---|---|
| Isolated ForgeGradle project and mod bootstrap | Initial implementation | Java 17, Forge 47.4.15, client-safe entry point |
| Config persistence and status command | Initial implementation | Forge config directory, `/voxy status`, in-game diagnostics |
| Shared storage/util adaptation | Initial implementation | Generated source copies with mapping substitutions |
| World section model and active-section cache | Ported | Ref-counted 32³ sections and striped cache |
| Section persistence | Ported | Palette/hash format, compressed mapping records, malformed payload checks |
| Block-state/biome mapper | Initial Mojmap port | Registry-keyed biome IDs and vanilla block-state codec serialization |
| Voxel conversion and mip generation | Initial Mojmap port | 16³ chunk section to Voxy voxel format, four generated mip levels |
| World engine and save queue | Initial port | Loads/stores sections, applies voxel updates, saves asynchronously |
| Forge chunk-load ingestion | Initial wiring | Client chunk-load events enqueue sections for background conversion |
| Chunk-change tracking | Not complete | Chunk-load ingestion only; changes after initial load need a reliable client hook/mixin |
| Renderer and mixins | Not ported | No far-distance geometry is currently drawn |
| Native packaging and runtime verification | Unverified for latest changes | Need clean CI build and an actual Forge client smoke test |

## Next actions

1. Run the Forge build/tests and fix any Mojmap/API compile issues from the mapper and ingestion migration.
2. Capture chunk block states, biome data and lighting safely when chunks change, not only when they load.
3. Port renderer buffers, render hooks and required mixins/access transformers.
4. Verify storage compatibility and run world join/leave, save/reload and GPU/native cleanup tests.

## Acceptance criteria

- [ ] Clean build and tests on JDK 17.
- [ ] Forge client launches without Fabric APIs.
- [ ] Chunk load and subsequent block/chunk changes feed ingestion.
- [ ] Distant terrain is visibly rendered in a real Forge client.
- [ ] Data survives restart and existing storage formats remain readable.
- [ ] World leave and shutdown release worker threads and native/GPU resources.

**Important:** this branch now includes the first Forge mapper/conversion/ingestion path, but it is not yet a playable LOD renderer. The renderer is not migrated, and the new code has not been verified by a fresh build or runtime test yet.


<!-- build check -->
