# Voxy Forge port — Minecraft 1.20.1

This directory is a deliberately separate ForgeGradle project targeting **Minecraft 1.20.1 / Forge 47.4.15 / Java 17**. It leaves the root Fabric project untouched while the port is developed.

## Current status

- [x] Standalone ForgeGradle project scaffold
- [x] Forge mod metadata and minimal `@Mod("voxy")` entry point
- [x] Pin a historical Minecraft 1.20.4 Fabric source revision
- [ ] Compile and launch the Forge skeleton in CI
- [ ] Port common/client initialization and lifecycle
- [ ] Port configuration, commands and resource reload
- [ ] Port mixins/access rules to 1.20.1 mappings and Forge runtime
- [ ] Port the LOD renderer and chunk integration
- [ ] Reintroduce optional renderer/shader integrations one by one
- [ ] Build and run client smoke tests on Java 17

## Build

Use JDK 17 and a Gradle 8.x installation compatible with ForgeGradle 6. Run from this directory:

```sh
gradle genIntellijRuns
gradle runClient
gradle build
```

A Gradle wrapper should be generated from a trusted Gradle 8.x distribution when a local build environment is available.

## Source baseline

The port branch is based on commit [`16e37d9b776db4f84b7df7852c080328dfaa9998`](https://github.com/Program-Adam/voxy/commit/16e37d9b776db4f84b7df7852c080328dfaa9998), whose `gradle.properties` pins Minecraft **1.20.4**, Fabric Loader **0.15.1**, and Fabric API **0.91.1+1.20.4**. The next commit on the historical line, `67edb4a`, changes the project to Minecraft 1.21, so this is the last commit before that version migration.

The entry point only proves that the Forge project is wired; it does not implement Voxy rendering or claim runtime success.
