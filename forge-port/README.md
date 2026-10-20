# Voxy Forge port — Minecraft 1.20.1

This separate ForgeGradle project targets **Minecraft 1.20.1 / Forge 47.4.15 / Java 17**. The original Fabric source at the repository root is kept as the reference baseline.

## Current status: world-engine core ported, rendering not yet playable

The Forge bootstrap, config persistence, shared storage serialization, `/voxy status`, and client lifecycle diagnostics are in place. The Forge source set now includes an initial loader-independent world engine: ref-counted world sections, a striped active-section cache, the existing palette/hash save format, mip updates from pre-voxelized sections, and asynchronous section persistence.

**This is not yet a playable far-distance/LOD rendering port.** Block/biome mapping, mip generation from Minecraft chunks, Forge chunk ingestion, and the renderer/mixins are not wired in, so distant terrain will not appear yet.

## Build and run locally

Install a 64-bit JDK 17 and Gradle 8.8. From the repository root:

```sh
gradle -p forge-port build
gradle -p forge-port runClient
```

The built mod jar is in `forge-port/build/libs/`. For a launcher installation, use Minecraft 1.20.1 with Forge 47.4.15 and copy the built mod jar into that instance's `mods` directory.

## Tests and CI artifact

The Forge workflow runs `build` and the world-section/storage tests, then uploads a jar artifact. See [Forge port Actions](https://github.com/Program-Adam/voxy/actions/workflows/forge-port.yml). Use a successful run's artifact named `voxy-forge-1.20.1-<commit>`; do not treat a successful compile as proof that rendering works.

## Source baseline and next work

The branch is based on historical Minecraft 1.20.4 Fabric source commit [`16e37d9b776db4f84b7df7852c080328dfaa9998`](https://github.com/Program-Adam/voxy/commit/16e37d9b776db4f84b7df7852c080328dfaa9998). Forge 1.20.1 uses Mojang mappings, so Minecraft-facing code requires explicit adaptation.

Next: port the mapper and registry/NBT conversions, hook chunk load/change and lighting capture into the ingestion service, then migrate renderer/cache/mixins and run an actual Forge client smoke test.
