# Voxy → Minecraft 1.20.1 Forge 47.4.15 porting overview

**Target:** Minecraft 1.20.1, Forge 47.4.15, Java 17.

**Pinned source baseline:** [`16e37d9b776db4f84b7df7852c080328dfaa9998`](https://github.com/Program-Adam/voxy/commit/16e37d9b776db4f84b7df7852c080328dfaa9998), the June 26, 2024 commit whose Gradle properties specify Minecraft 1.20.4, Fabric Loader 0.15.1 and Fabric API 0.91.1+1.20.4. The following historical commit `67edb4a44b2f446816c2d39f1e440c6f66c9026f` is the update to Minecraft 1.21.

The port is being developed under `forge-port/` so the pinned Fabric project remains intact. The Forge project currently contains a build scaffold and minimal mod entry point only; Voxy functionality is not yet ported.

## Migration work

| Priority | Area | Work |
|---|---|---|
| P0 | Build | ForgeGradle 6.x, Forge 47.4.15, Java 17, dependency pinning |
| P0 | Metadata/bootstrap | Replace Fabric metadata and entry points with `mods.toml` and Forge lifecycle |
| P0 | Fabric APIs | Replace loader, lifecycle, commands, resource and config APIs |
| P0 | Mappings/API | Port Yarn-named 1.20.4 source to Forge's 1.20.1 mapping namespace and API surface |
| P0 | Mixins/access | Revalidate mixin targets and injection points; convert access widener rules to Forge access transformers where appropriate |
| P0 | Renderer | Port chunk/cache, level renderer, fog, GPU resources, shader and render-order hooks |
| P0 | Compatibility | Pick exact Forge-compatible renderer/shader integration versions and isolate optional hooks |
| P1 | Storage/native libs | Verify packaging, native classifiers, persistence compatibility and shutdown |
| P1 | UI/config | Replace Mod Menu / Fabric config integration with a Forge-compatible path |
| P0 | Validation | CI build, client launch, world load, chunk updates, LOD visibility, save/reload and shutdown |

## Implementation sequence

1. Keep the root Fabric baseline unchanged and make the Forge scaffold build cleanly.
2. Add a loader-neutral core/platform boundary; move initialization behind Forge lifecycle registration.
3. Port config and commands; avoid pulling Fabric runtime classes into the Forge artifact.
4. Port chunk ingestion and LOD persistence before the renderer, preserving existing data formats where possible.
5. Port each Minecraft mixin/access rule against 1.20.1 and verify at runtime.
6. Get the basic LOD path rendering without optional mods.
7. Add renderer/shader integrations one at a time, pinning tested versions.
8. Run the validation matrix and document known incompatibilities.

## Definition of done

- Clean build with JDK 17 and Forge 47.4.15.
- Forge client starts without mandatory Fabric classes or APIs.
- Worlds load and unload safely; LOD geometry updates as chunks change.
- LODs render correctly with persistence working across restart.
- No unreleased GPU resources, stuck worker threads or database/native-library failures.
- Optional-mod support is documented and limited to tested combinations.

This document is a migration plan, not a claim that the port is already playable.
