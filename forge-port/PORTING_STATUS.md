# Forge port implementation status

Target: Minecraft 1.20.1, Forge 47.4.15, Java 17.

## Current state

This is an incremental port, not yet a playable Voxy build. The isolated Forge project can be developed without destabilizing the root Fabric project.

Source baseline: `16e37d9b776db4f84b7df7852c080328dfaa9998` (Minecraft 1.20.4 Fabric, June 26, 2024). The following historical commit, `67edb4a44b2f446816c2d39f1e440c6f66c9026f`, switches to Minecraft 1.21.

| Phase | Status | Notes |
|---|---|---|
| Identify source baseline | Complete | Pinned 1.20.4 Fabric commit |
| Create isolated ForgeGradle project | Complete (scaffold only) | Build configuration, metadata, Java 17 target |
| Add minimal Forge entry point | Complete (scaffold only) | Logs during mod construction; no Voxy renderer is wired |
| Port config path and persistence | Initial implementation | Uses `FMLPaths.CONFIGDIR`, preserves key options/defaults, writes config atomically where supported |
| Verify Forge scaffold build | In progress | GitHub Actions workflow builds the isolated project |
| Port common initialization and commands | Not started | Replace Fabric lifecycle/command APIs |
| Port chunk ingestion and LOD persistence | Not started | Preserve storage compatibility where possible |
| Port rendering and Minecraft mixins | Not started | Highest-risk work; source uses Yarn names while ForgeGradle uses Mojang mappings |
| Port optional integrations | Not started | Keep optional until the core renderer works |
| Runtime smoke tests | Not started | Requires a Forge 1.20.1 client and compatible GPU/runtime |

## Rules for the port

1. Keep the root Fabric project untouched while the Forge implementation is being brought up.
2. Port one subsystem at a time and require a successful compile before moving on.
3. Treat successful compilation as necessary but insufficient: validate world load, chunk updates, LOD visibility, save/reload, shutdown and GPU resource lifetime.
4. Do not declare support for Sodium, Iris, or other optional mods until exact Forge-compatible versions have been tested.

## Immediate next step

Verify the scaffold build, then port the client bootstrap and loader-specific API boundaries from the pinned 1.20.4 source. The renderer cannot be wired safely until its Minecraft references and mixins have been migrated from Yarn names to the Forge mapping namespace.
