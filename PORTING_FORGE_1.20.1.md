# Voxy → Minecraft 1.20.1 Forge 47.4.15 porting overview

**Target:** Minecraft 1.20.1, Forge 47.4.15, Java 17.

**Pinned source baseline:** [`16e37d9b776db4f84b7df7852c080328dfaa9998`](https://github.com/Program-Adam/voxy/commit/16e37d9b776db4f84b7df7852c080328dfaa9998), the June 26, 2024 commit whose Gradle properties specify Minecraft 1.20.4, Fabric Loader 0.15.1 and Fabric API 0.91.1+1.20.4. The following historical commit `67edb4a44b2f446816c2d39f1e440c6f66c9026f` updates the project to Minecraft 1.21.

The port is being developed under `forge-port/` so the pinned Fabric project remains intact.

## Implemented so far

- ForgeGradle project targeting Forge 47.4.15, Minecraft 1.20.1 and Java 17.
- Forge metadata (`META-INF/mods.toml`) and a minimal `@Mod("voxy")` entry point.
- Forge client `/voxy status` command for verifying bootstrap and config state.
- Forge config persistence using `FMLPaths.CONFIGDIR`, with the existing Voxy option defaults and the original per-world storage JSON format.
- Shared storage backends and utility classes pulled from the pinned source baseline, with small mapping/API substitutions for Forge's Mojang mappings.
- Explicit storage-config serialization registration to preserve the existing `TYPE` JSON format.
- A JUnit round-trip test for the default `CompressionAdaptor → RocksDB + ZSTD` storage config.

**Verified:** GitHub Actions build and the storage serialization test passed at commit [`4f7644acc275f7c12423342fc1124f2b2ab0f3ba`](https://github.com/Program-Adam/voxy/commit/4f7644acc275f7c12423342fc1124f2b2ab0f3ba). The subsequent change to bundle native/runtime dependencies is awaiting its CI result. This is not yet a playable Voxy port: the world engine, chunk ingestion, renderer, and mixins are not wired into Forge.

## Remaining migration work

| Priority | Area | Work |
|---|---|---|
| P0 | Runtime packaging | Verify JarJar dependencies and native classifiers; test the distributable JAR |
| P0 | Fabric APIs | Replace loader, lifecycle, commands, resource and config APIs |
| P0 | Mappings/API | Port Yarn-named Minecraft 1.20.4 code to Forge's Mojang-mapped 1.20.1 API surface |
| P0 | World engine | Port world sections, ingestion, save/load and chunk conversion |
| P0 | Mixins/access | Revalidate mixin targets/injections; convert access widener rules to Forge access transformers where appropriate |
| P0 | Renderer | Port chunk/cache, level renderer, fog, GPU resources, shader and render-order hooks |
| P0 | Compatibility | Isolate optional renderer/shader integrations and select exact Forge-compatible versions |
| P1 | UI/config | Replace Mod Menu / Fabric config integration with a Forge-compatible path |
| P0 | Validation | Client launch, world load, chunk updates, LOD visibility, save/reload and shutdown |

## Implementation sequence

1. Keep the root Fabric baseline unchanged and make the Forge scaffold/storage layer build cleanly.
2. Finish dependency/native packaging and test the Forge artifact launch.
3. Port world engine and chunk ingestion while preserving storage formats.
4. Port each Minecraft mixin/access rule against 1.20.1 and verify at runtime.
5. Get the basic LOD path rendering without optional mods.
6. Add renderer/shader integrations one at a time, pinning tested versions.
7. Run the validation matrix and document known incompatibilities.

## Definition of done

- Clean build with JDK 17 and Forge 47.4.15.
- Forge client starts without mandatory Fabric classes or APIs.
- Worlds load and unload safely; LOD geometry updates as chunks change.
- LODs render correctly with persistence working across restart.
- No unreleased GPU resources, stuck worker threads or database/native-library failures.
- Optional-mod support is documented and limited to tested combinations.

This document is a migration plan with partial implementation progress, not a claim that the port is already playable.
