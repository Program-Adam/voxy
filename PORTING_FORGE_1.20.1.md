# Voxy → Minecraft 1.20.1 Forge 47.4.15 Porting Overview

**Goal:** port Voxy from a Minecraft 1.20.4 Fabric build to **Minecraft 1.20.1 + Minecraft Forge 47.4.15**.

**Status:** planning document only. This file records the work areas and recommended order; it does not claim that a Forge port has been implemented or tested.

## Important baseline check

The repository's current `dev` branch does **not** appear to be the stated Minecraft 1.20.4 baseline: its `gradle.properties` currently declares Minecraft **26.3**, Fabric Loader **0.19.5**, Fabric API **0.160.5+26.3**, Loom **1.16-SNAPSHOT**, and Java release **25**. Before porting, identify and pin the exact 1.20.4 commit/tag/branch, or deliberately port from the current tree and account for the much larger version downgrade. Do not treat the current `dev` tree as 1.20.4-compatible.

Forge 47.4.15 targets **Minecraft 1.20.1**, so this is both a loader migration (Fabric → Forge) and a Minecraft version change (1.20.4 → 1.20.1). Expect API and mapping changes in addition to loader changes.

## Scope at a glance

| Area | Required work | Priority |
|---|---|---|
| Build and dependencies | Replace Fabric Loom setup with a ForgeGradle 6.x-based 1.20.1 project; Java 17; pin compatible libraries | P0 |
| Mod entry points / metadata | Replace `fabric.mod.json`, Fabric entrypoints and dependency declarations with Forge mod metadata and lifecycle wiring | P0 |
| Fabric API usage | Replace Fabric events, commands, client hooks, resource loading and other APIs with Forge equivalents or explicit compatibility shims | P0 |
| Mixins and access | Audit all mixin configs/injections against 1.20.1 mappings and runtime; convert access widener rules to Forge Access Transformers where appropriate | P0 |
| Minecraft internals | Port render, chunk-cache, level, fog, render-system and client-session hooks to 1.20.1 code | P0 |
| Sodium / Iris integration | Select exact 1.20.1-compatible Forge-side APIs/mods; isolate optional integrations and fail safely when absent | P0 |
| Rendering and GPU | Validate OpenGL/LWJGL calls, shader compilation, buffers, framebuffers, render ordering and resource lifetime on the 1.20.1 pipeline | P0 |
| Storage and native libraries | Re-check bundled/native dependencies, classifiers, packaging and supported operating systems | P1 |
| Config, commands and UI | Port config screen, Mod Menu integration, commands, debug entries and translations | P1 |
| Compatibility and regression testing | Add launch, world-load, render, save/reload and optional-mod test matrix | P0 |

## 1. Establish a reproducible 1.20.4 source baseline

- [ ] Find the exact commit/tag/branch that builds for Minecraft 1.20.4 Fabric.
- [ ] Record its commit SHA in this document or in the port branch notes.
- [ ] Create a dedicated branch for the Forge port; keep the Fabric baseline intact.
- [ ] Save the original Gradle properties, dependency versions, mixin configs, access widener and generated resource metadata for reference.
- [ ] Build the baseline successfully before changing code, and record the output JAR and build command.

**Do not start by copying the current `dev` build files unchanged.** They currently target Minecraft 26.3 / Java 25, not 1.20.4.

## 2. Replace the build system and mod packaging

### Build setup

- Replace `net.fabricmc.fabric-loom` with a ForgeGradle version compatible with Minecraft 1.20.1 / Forge 47.4.15 (normally ForgeGradle 6.x for this target).
- Use the Forge MDK for 1.20.1 as the starting point for `settings.gradle`, `build.gradle`, run configurations and mappings.
- Target **Java 17** (`options.release = 17`); Minecraft 1.20.1 is not a Java 25 target.
- Pin Forge to `1.20.1-47.4.15`; pin all mod/library versions to versions that actually support Minecraft 1.20.1 and Forge.
- Remove Fabric Loader, Fabric API modules, Fabric Loom tasks and Fabric-specific resource processing.
- Rebuild the dependency model deliberately: distinguish compile-only optional integrations from bundled libraries and runtime dependencies.
- Review `include` / Jar-in-Jar handling for RocksDB, SQLite, LWJGL-related libraries, compression libraries, Redis/Jedis and other shaded or native dependencies. Avoid packaging libraries that Forge/Minecraft already supplies, and test the final JAR on Windows and Linux at minimum.
- Recreate build metadata (version, commit SHA and build time) using Forge-compatible resource expansion or manifest metadata.

### Mod metadata and entry points

Current Fabric metadata is in `src/main/resources/fabric.mod.json`. Replace it with Forge metadata, typically `META-INF/mods.toml`, and add a Forge mod entry point using `@Mod("voxy")`.

Map existing Fabric entry points to Forge lifecycle registration:
- `me.cortex.voxy.commonImpl.VoxyCommon` — common initialization, if it truly needs to run on both sides.
- `me.cortex.voxy.client.VoxyClient` — client-only initialization and client event registration.
- `me.cortex.voxy.client.config.ModMenuIntegration` — replace Mod Menu integration with a Forge-compatible config-screen integration or remove it for the first working port.
- `me.cortex.voxy.client.config.VoxyConfigMenu` — replace Sodium Config API integration with the API supported by the chosen 1.20.1 renderer/mod stack.

Declare Forge and Minecraft version ranges in `mods.toml`. Do not carry Fabric entrypoint names or Fabric dependency keys into the Forge artifact.

## 3. Audit Fabric API calls and lifecycle assumptions

Search all Java sources for `net.fabricmc`, `FabricLoader`, `ClientModInitializer`, `ModInitializer`, `ClientTickEvents`, `ServerLifecycleEvents`, Fabric command APIs, Fabric resource-loader APIs and Fabric rendering APIs.

For every use, record the call site and choose one of:
1. replace it with a Forge event / registry / lifecycle API;
2. replace it with a small loader-independent Voxy abstraction with a Forge implementation; or
3. remove it if it is only development or optional-mod support.

Likely migration buckets include:
- client and common initialization;
- client/world/session start and stop events;
- command registration;
- resource reload and resource discovery;
- debug overlay / debug screen entries;
- config registration and screen opening;
- mod-presence checks and version checks;
- environment/client-only class separation.

Avoid adding Forge calls directly throughout core logic where a narrow platform adapter would keep the rendering/storage code reusable.

## 4. Port mixins and access rules

The current tree contains `client.voxy.mixins.json`, `common.voxy.mixins.json`, `src/main/java/me/cortex/voxy/client/mixin/**` and `src/main/resources/voxy.accesswidener`. These need an item-by-item compatibility audit, not just a metadata rename.

- Re-resolve every target class, field, method, descriptor, injection point and local capture against **Minecraft 1.20.1** and the chosen Forge mappings.
- Re-test mixins against Forge's runtime namespace and remapping/refmap behavior. Confirm the selected Mixin configuration is loaded by Forge and that required MixinExtras support is present if any code uses it.
- Convert access-widener entries to Forge Access Transformer rules in `src/main/resources/META-INF/accesstransformer.cfg` when applicable. Use the exact names/format expected by the chosen ForgeGradle mappings; do not mechanically copy Fabric's named-namespace entries.
- Prefer Forge events or public APIs over mixins when they provide the needed hook. Keep mixins for renderer internals that cannot be reached cleanly through Forge APIs.
- Verify client-only mixins cannot load on a dedicated server.
- Add a mixin audit checklist with columns: target, purpose, 1.20.1 target verified, injection succeeds, tested with optional mods.

High-risk mixin families in this codebase include Minecraft client chunk cache/level/rendering, RenderSystem and fog, session/packet listener hooks, Sodium renderer internals, Iris shader/render pipeline internals, Nvidium and Flashback compatibility.

## 5. Port the renderer and mod integrations

Voxy is a far-distance / level-of-detail renderer, so rendering compatibility is the main technical risk. A successful compile is not evidence that the port works.

### Minecraft rendering internals

Audit and adapt code around:
- `VoxyRenderSystem`, `RenderPipelineFactory`, `NormalRenderPipeline` and `IrisVoxyRenderPipeline`;
- `MixinLevelRenderer`, `MixinRenderSystem`, `MixinFogRenderer`, `MixinLevelExtractor` and related mixins;
- viewport selection, depth/framebuffer handling, Hi-Z occlusion, render ordering, resource reuse and GPU synchronization;
- block/entity model baking and texture extraction;
- chunk section updates, geometry generation, visibility/occlusion and chunk cache interception.

For each hook, confirm the 1.20.1 equivalent still exists and has the same timing/semantics. Minecraft 1.20.1's renderer is not interchangeable with a newer-version renderer simply because the class names look similar.

### Sodium, Iris and optional mods

The current build declares or conditionally integrates Sodium, Iris, Mod Menu, Lithium, Chunky, Vivecraft, Flashback and Nvidium. These are not automatically usable on Forge.

- Pick a known, compatible 1.20.1 Forge-side renderer stack before porting renderer mixins. Confirm exact mod versions and APIs; do not assume a Fabric Sodium or Iris artifact works on Forge.
- Keep a vanilla/Forge-compatible fallback path if Voxy's architecture permits it.
- Move optional integrations behind isolated adapters and mod-presence checks.
- Port or temporarily disable each integration separately. First get Voxy running without optional renderer mods; then add one integration at a time.
- Revisit the Sodium config UI and Iris shader patching/uniform/sampler code independently; both are tightly coupled to specific mod internals.
- Treat Nvidium as a separate compatibility investigation rather than a required dependency.
- Document supported combinations and explicitly reject known-incompatible versions rather than crashing during startup.

## 6. Keep core algorithms and data separate from loader glue

Most of the LOD, storage, geometry, shader and GPU utility code may be reusable in principle, but must still be compiled and tested against the target Java/Minecraft APIs.

Prioritize keeping these areas platform-neutral where practical:
- LOD hierarchy and traversal;
- geometry / section data generation;
- persistence and database code;
- shader source assets and GLSL algorithms;
- low-level storage and compression utilities.

Loader-specific initialization, event registration, config UI, Minecraft internals and third-party mod hooks should be kept at the edges. Do not rewrite algorithms merely because their surrounding Fabric bootstrap code changes.

## 7. Resources, shaders and configuration

- Preserve `src/main/resources/assets/voxy/**` and language/icon resources unless a target API or format change requires edits.
- Check every shader's GLSL version and required OpenGL features against the 1.20.1 runtime and the selected graphics backend.
- Verify shader include/define processing and resource lookup paths under Forge's resource system.
- Verify config defaults, migration from existing Voxy config files, and safe fallback when config files are absent or malformed.
- Check translations and key names after replacing Mod Menu / Sodium configuration screens.
- Verify bundled resources are included in the final Forge JAR at the expected paths.

## 8. Validation plan and definition of done

### Build and startup
- [ ] Clean Gradle build with JDK 17 and Forge 1.20.1-47.4.15.
- [ ] Forge client launches with only required dependencies.
- [ ] Mod metadata and dependency errors are clean; no Fabric Loader/API classes are required at runtime.
- [ ] Dedicated server startup does not load client-only classes, if the mod is intended to support a dedicated server.
- [ ] Final distributable JAR contains metadata, mixin configs, access transformer and all required assets/libraries.

### Functional smoke tests
- [ ] Create a new world and load an existing world.
- [ ] Explore, teleport and change render distance; verify LOD creation, updates and culling.
- [ ] Verify chunk updates after block changes, world reload and dimension changes.
- [ ] Save, quit, relaunch and confirm persisted data can be read safely.
- [ ] Test resource reload, config changes and clean shutdown.
- [ ] Exercise shader compile failures, unsupported GPU capabilities and missing optional mods without unrecoverable crashes.
- [ ] Check memory use, GPU resource leaks, thread shutdown and database/native-library loading.

### Compatibility matrix
Test the baseline Forge client first, then add compatible optional mods one at a time: Sodium-equivalent renderer (if supported), Iris-equivalent shader support, and each remaining integration independently. Record exact mod versions and results in the repository.

**Done means:** clean build, reproducible launch, stable world load/unload, visible correct LOD rendering, working persistence, no mandatory Fabric runtime dependency, and documented supported optional-mod combinations.

## Suggested implementation sequence

1. **Baseline:** locate/pin the actual 1.20.4 Fabric commit and produce a clean baseline build.
2. **Forge skeleton:** create a separate 1.20.1 Forge 47.4.15 branch/project using JDK 17; make a minimal `@Mod` entry point compile and launch.
3. **Metadata and common initialization:** port mod setup, config defaults and platform abstractions.
4. **Dependency and storage layer:** package/retest required libraries and native artifacts.
5. **Core client bootstrap:** wire Voxy initialization and shutdown without optional mods.
6. **Rendering hooks:** port mixins and Minecraft internals one subsystem at a time.
7. **First LOD render:** get a minimal end-to-end LOD path working before optional integrations.
8. **Integrations:** add renderer/shader/config compatibility separately, with version guards.
9. **Regression and packaging:** run the validation checklist and publish only tested combinations.

## Reference links

- [Forge 1.20.1 downloads / build 47.4.15](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html)
- [Forge 1.20.x Access Transformers](https://docs.minecraftforge.net/en/1.20.x/advanced/accesstransformers/)
- [ForgeGradle documentation](https://docs.minecraftforge.net/en/fg-6.x/)
- [Fabric Access Wideners / Class Tweakers](https://docs.fabricmc.net/develop/class-tweakers/access-widening)

---

*This is a migration checklist, not a completed port. Re-validate every API, dependency and mixin against the pinned 1.20.4 source and Minecraft 1.20.1 Forge runtime.*
