# CLAUDE.md

## What this is

Family Tree tracks tamed pets and their lineage. Fabric supports Minecraft 1.21.1, 26.1.1, 26.1.2, 26.2, and 26.3. NeoForge supports 1.21.1, 26.2, and 26.3. The NeoForge 26.3 loader and mod build are beta. Install JDK 21 and 25 for all builds. The 1.21.1 jars use Java 21 bytecode; 26.x jars use Java 25.

Mod ID `familytree`, package `com.egakh.familytree`. `mod_version` lives in `gradle.properties`. Players search pets, pan and zoom through ancestry, locate pets, add known parents, and export a family as a PNG.

## Build and source versions

`./gradlew buildAll --no-parallel` builds and tests all eight pairs. Fabric jars land in `versions/<minecraft>/build/libs/`; NeoForge jars land in `neoforge/build/<minecraft>/libs/`. Build one Fabric target with `./gradlew :26.2:build`, or NeoForge with `./gradlew -p neoforge build -Pminecraft=26.2`.

Stonecutter 0.9.6 and loom-back-compat 0.3 generate target-specific source. Loom is pinned to 1.16.3. ModDevGradle is pinned to 2.0.147. Fabric Loader 0.19.3 is the tested minimum.

- `settings.gradle` lists the five Fabric versions. Source names remain authored against 26.1.1.
- `stonecutter.gradle` defines `buildAll`, target API replacements, and the ordered NeoForge build tasks.
- `build.gradle` selects Fabric API and stamps an exact Minecraft predicate into metadata. `loomx.applyMojangMappings()` handles modern Mojang names.
- `neoforge/build.gradle` compiles generated shared source for `-Pminecraft`. The 1.21.1 loader adapter lives in `neoforge/src/legacy/java`; modern adapters live in `src/main/java` there.
- `versions/` is generated and ignored. Never edit or commit it.

Add simple API renames to version-gated replacements in `stonecutter.gradle`. Keep source names compatible with 26.1.1. For a removed API or a changed signature, use a Stonecutter conditional. Its replacement rules reverse when inactive, so avoid overlapping replacement strings or imports that also occur independently in source.

When adding a target, update the version list, dependencies, `buildAll`, release manifest, CI matrix, installation guide, and page matrix. Compile and run Minecraft on that target before claiming compatibility. Exact names can be checked with `javap` in Gradle's deobfuscated Minecraft jars.

## Save compatibility comes first

Players keep pet history for years. Never discard that history for a code cleanup.

- Never rename or remove existing saved AnimalRecord fields, including `birth_world_day`, `death_epoch`, `variant_id`, and `name`.
- New saved fields must use `optionalFieldOf`. Old saves must parse without the field. Handle missing values when reading it.
- RecordCodecBuilder groups cap at 16 fields. `BASE_CODEC` preserves the original 16 flat fields. The outer codec combines it with optional additions such as `tree_name`.
- FamilyTreeState keeps readable records when another record fails. It preserves unreadable and duplicate entries for resave and logs their count. Do not replace this with a strict `listOf()` or silently discard rejected entries.
- Future or unsupported format versions protect the original data and disable writes. Add a migration before changing the meaning of a data version. Current data version is 1.
- Legacy import supports both raw records and the SavedData `data` wrapper. It runs only for an uninitialized store and copies the legacy file to a migration backup. An empty modern store must not reimport deleted history.
- Pruning requires a successful JSON backup before deletion. Backups live under the world's `familytree-backups/` directory.
- Document downgrade limits. Older mod versions can drop unknown fields on their next save. Never downgrade a Minecraft world for a test.

When touching persistence, load previous-release data on the same game version and check commands and the GUI. See `docs/testing.md` for disposable-world runners. Retained history in an existing smoke world is useful evidence, but is not an immutable migration fixture.

## Network and permissions

Server code owns the saved history. Both loaders dispatch shared packet handlers on the server thread. PetAccess defines view and manage rules; public snapshots remove foreign pet coordinates. Snapshot copies do not retain mutable server records.

Version 1.1.0 uses `open_request_v3`, `snapshot_pages_v3`, and `discover_v2`. NeoForge registers protocol `2`. Update client and server together. Incompatible packet changes need new channels or a negotiated version. Keep fields in codec encode and decode order.

Snapshots use a request UUID, page number, total count, and byte-bounded records. The client exposes the result only after validating every page. Current page budget is 256 KiB and aggregate limit is 100,000 records. Never silently truncate a family.

Commands and discovery share per-player and per-server scan cooldowns. Rate-limited requests return before scanning or serializing. Rename and parent edits return an explicit result. Validate current ownership, species, distinct IDs, and ancestry loops on the server before editing.

## Code map

- `data/AnimalRecord.java` contains the persisted and network record schema. `name()` uses the custom tree name; `originalName()` retains the entity name. Tree names never change an entity's name tag.
- `data/FamilyTreeState.java` contains the overworld save, tolerant recovery, legacy import, and prune backup.
- `event/PetLifecycleListeners.java` records breeding, tame, name, unload, and death events. Existing loaded pets enter through discovery.
- `permissions/` contains ownership and parent-link rules. Horse and donkey parents are allowed for a mule.
- `network/` contains cooldowns, page splitting, and shared handlers. Loader adapters register channels and events.
- `command/FamilyTreeCommand.java` handles list, scan, locate, info, pairing, age corrections, and pruning. Name edits reject ambiguity and accept exact UUIDs.
- `interaction/LinkingTool.java` retains the named-stick workflow and rechecks permissions at confirmation. Sessions clear on logout and stop.
- `util/Genealogy.java` calculates generations with an explicit stack and checks cycles. Derived generations are never persisted.
- `client/SnapshotAssembler.java` validates paged replies and ignores old requests.
- `client/screen/` contains browser, tree, settings, help, rename, parent editor, and pet picker. Screens use FamilyTreeScreen to adapt input and rendering APIs.
- `client/export/` copies a tree and renders a PNG on a background task. Exports omit explicit player names and coordinates. User-entered pet names may still contain personal information.
- `platform/` and `client/platform/` hold loader-independent paths and transports. Shared code has no Fabric dependency.

All new player-facing text uses translation keys in `assets/familytree/lang/en_us.json`. Add keys for new errors rather than raw English messages. Clear client entity previews when disconnecting.

## Testing and publication

Run `buildAll` after changing Minecraft APIs. Development-only source sets `src/smoke` and `src/demo` stay outside release jars. `docs/testing.md` describes server, client, and real gameplay checks.

Commit the tested source, then run `python scripts/release_manifest.py`. It checks exact metadata, Java bytecode, icon, license, and development-class exclusions. It copies jars and source jars into `build/releases/<version>/` and records SHA-512 hashes and the source commit. Publish those copies without rebuilding.

Modrinth project ID is `CVQKDAe7`. Fabric API dependency ID is `P7dR8mSH`. Publish one version per game and loader. Follow `docs/MODRINTH_API.md` or `scripts/publish_modrinth.py`; the PAT lives in ignored `.env`. Never print or commit it. Daily public download counts require no token and no player telemetry.
