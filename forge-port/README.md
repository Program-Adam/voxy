# Voxy Forge port — Minecraft 1.20.1

This is a separate ForgeGradle project targeting **Minecraft 1.20.1 / Forge 47.4.15 / Java 17**. The root Fabric project is retained as the source/reference baseline.

## Current status: boot scaffold, not yet the full Voxy renderer

The Forge bootstrap, config persistence, storage serialization, `/voxy status`, client-world lifecycle detection, and a small in-game status overlay are implemented. The overlay is deliberately honest: it says the LOD renderer is still being migrated. **This is not yet a playable far-distance/LOD rendering port.**

## Build and run locally

Install a 64-bit JDK 17 and Gradle 8.8. From the repository root:

```sh
gradle -p forge-port build
gradle -p forge-port runClient
```

The built mod jar is in `forge-port/build/libs/`. For a normal launcher installation, install Minecraft 1.20.1, Forge 47.4.15, and copy the built mod jar into that instance's `mods` directory. Do not put source/test jars in the mods folder.

On first launch, the mod creates `config/voxy-config.json`. Join a world and use `/voxy status`; the top-left overlay and log should report client/world state. These are bootstrap smoke checks only, not evidence that LOD rendering works.

## CI build artifact

The [Forge port GitHub Actions workflow](https://github.com/Program-Adam/voxy/actions/workflows/forge-port.yml) builds and tests the project and uploads the mod jar as a short-lived artifact. Open a successful run and download the artifact named `voxy-forge-1.20.1-<commit>`.

## Source baseline

The port branch is based on commit [`16e37d9b776db4f84b7df7852c080328dfaa9998`](https://github.com/Program-Adam/voxy/commit/16e37d9b776db4f84b7df7852c080328dfaa9998), the historical Minecraft 1.20.4 Fabric source baseline. It uses Yarn names and Fabric APIs; Forge 1.20.1 uses a different Minecraft version and Mojang mappings, so client rendering and mixin code need explicit adaptation rather than direct copying.

## Migration plan

1. Keep the Forge bootstrap buildable and publish artifacts from CI.
2. Port loader-independent section storage and world-engine pieces while preserving the existing storage schema.
3. Adapt block-state/world conversion and chunk ingestion to Forge client-world lifecycle.
4. Convert required access wideners and remap the renderer mixins to 1.20.1.
5. Bring up core LOD rendering, then optional Sodium/Iris/Nvidium integrations.
6. Run an actual client smoke-test matrix: world join/leave, chunk updates, save/reload, shutdown, GPU/native cleanup.

A successful compile is necessary but not sufficient; the port should not be called playable until distant terrain is rendered in a real Forge client.