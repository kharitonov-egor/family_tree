# CLAUDE.md

## What this is

Family Tree tracks tamed pets, the animals players breed, name, or add, and their lineage. Fabric supports Minecraft 1.20.1, 1.21.1, 1.21.4 through 1.21.11, 26.1.1, 26.1.2, 26.2, and 26.3. Its 1.21.8 jar also covers 1.21.6 and 1.21.7, and its 1.21.10 jar covers 1.21.9. NeoForge supports 1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.2, and 26.3. Forge supports 1.20.1. The NeoForge 26.3 loader and mod build are beta. Fabric also builds the 26.4-snapshot-6 target for release-day readiness; release manifests leave it out unless `--snapshots` asks for it, and `docs/releases/26.4-runbook.md` moves it to 26.4. Install JDK 17, 21, and 25 for all builds. The 1.20.1 jars use Java 17 bytecode, the 1.21.x jars Java 21, and 26.x jars Java 25.

Mod ID `familytree`, package `com.egakh.familytree`. `mod_version` lives in `gradle.properties`. Players search pets, pan and zoom through ancestry, locate pets, add known parents, and export a family as a PNG.

## Build and source versions

`./gradlew buildAll --no-parallel` builds and tests all twenty-one pairs. Fabric jars land in `versions/<minecraft>/build/libs/`; NeoForge jars land in `neoforge/build/<minecraft>/libs/`; Forge jars land in `forge/build/<minecraft>/libs/`. Build one Fabric target with `./gradlew :26.2:build`, NeoForge with `./gradlew -p neoforge build -Pminecraft=26.2`, or Forge with `./gradlew -p forge build -Pminecraft=1.20.1`.

Stonecutter 0.9.6 and loom-back-compat 0.3 generate target-specific source. Loom is pinned to 1.16.3. ModDevGradle is pinned to 2.0.147, and Forge uses its `legacyforge` plugin. Fabric Loader 0.19.3 is the tested minimum, except 26.4, whose Fabric API needs Loader 0.20; `build.gradle` sets the loader per target and stamps it into `fabric.mod.json`. Forge builds against 47.4.28 and declares 47.1.0, the oldest build tested with the release jar.

- `settings.gradle` lists the twelve Fabric versions. Source names remain authored against 26.1.1.
- `stonecutter.gradle` defines `buildAll`, target API replacements, and the ordered NeoForge and Forge build tasks.
- `build.gradle` selects Fabric API and stamps a Minecraft predicate into metadata. It is exact except for the 1.21.8 and 1.21.10 ranges, and it writes snapshots the way Fabric Loader reads them, so `26.4-snapshot-6` becomes `=26.4-alpha.6`. Declare an earlier patch only after running the jar on it, and list it in `ALSO_RUNS_ON` in `scripts/release_manifest.py`, which the publishers use for game versions. `loomx.applyMojangMappings()` handles modern Mojang names.
- `neoforge/build.gradle` compiles generated shared source for `-Pminecraft`. 1.21.1, 1.21.4, and 1.21.5 use the legacy loader adapter in `neoforge/src/legacy/java`, because NeoForge 21.5 and older lack `RegisterClientPayloadHandlersEvent` and `ClientPacketDistributor`. 1.21.8 and newer use the modern adapters in `src/main/java` there.
- `forge/build.gradle` compiles the generated 1.20.1 main, client, and test source with JDK 17. Forge runs on SRG names, so the Mixin annotation processor writes `familytree.refmap.json`, the jar manifest lists the mixin config, and `reobfJar` produces the release jar. Forge 1.20.1 also requires `pack.mcmeta` in every mod, including the smoke and demo mods.
- `versions/` is generated and ignored. Never edit or commit it.

Add simple API renames to version-gated replacements in `stonecutter.gradle`. Keep source names compatible with 26.1.1. Each game rename sits behind the release that introduced it: `< 1.21.2` for `EntitySpawnReason`, `SUCCESS_SERVER`, `ByteBufCodecs.LONG`, and registry lookups; `< 1.21.5` for `ClickEvent` records, `SavedDataType` storage, NBT getters, the wolf package, and variant asset info; `< 1.21.9` for the private `isClientSide` field; and `< 1.21.11` for `Identifier`, the equine and feline packages, and `Util`. 1.21.11 keeps the older GUI and Fabric API names, so those renames sit behind `< 26.1`. Conditionals follow the same releases: owner references and `SavedDataType` start at 1.21.5, `Screen.render` stops blurring at 1.21.6, input events and key categories start at 1.21.9, and permission sets start at 1.21.11. For a removed API or a changed signature, use a Stonecutter conditional. Its replacement rules reverse when inactive, so avoid overlapping replacement strings or imports that also occur independently in source. Stonecutter matches every rule against the authored text in one pass and the longest match wins, even for an inactive rule. A rule cannot build on another rule's output. A 1.20.1 rule whose left side contained `Identifier` blocked the `Identifier` rename on 1.21.1, which is why 1.20.1 maps `.fromNamespaceAndPath(` to `.tryBuild(`. After editing rules, diff `versions/<v>/build/generated` for every target. Rules also rewrite shared code that happens to match them: a method named `render(` becomes `extractRenderState(` on 26.x, so give non-GUI methods other names.

Write 26.4 conditionals as `>26.3`. Stonecutter sorts `26.4-snapshot-6`, pre-releases, and release candidates before `26.4`, so `>=26.4` skips them. 26.4 drops the mouse and partial-tick parameters from `Screen.extractRenderState`, which `FamilyTreeScreen` adapts back to the older signature; makes `AbstractWidget.visible` protected, so use `FamilyTreeScreen.setVisible` and `isVisible`; and moves entity previews to `EntityPortraitWidget`, which follows the real mouse, so `PetFaceRenderer` builds the portrait itself to keep a fixed angle.

Since 26.3, Minecraft numbers mouse buttons from 1 as SDL does, so `InputConstants.MOUSE_BUTTON_LEFT` is 1 there and 0 on older targets. Compare buttons with `FamilyTreeScreen.isLeftButton` or the `InputConstants` constants, never a literal. The demo runners press buttons directly and never send mouse events, so check clicks on the canvas and browser rows in a real client.

Minecraft 1.20.1 predates `CustomPacketPayload`, `StreamCodec`, `ByteBufCodecs`, and `RegistryFriendlyByteBuf`. Shared code keeps using those names. On 1.20.1 the `<1.21` rules point the imports at `network/backport`, which writes the same bytes. `WireFormatTest` asserts identical payload bytes on every target. Use only the codec methods the backport defines, or add the method there. Conditionals for 1.20.1 use `>=1.21`/`<1.21`. Shared source compiled for 1.20.1 must avoid APIs newer than Java 17, such as `List.getFirst()`.

When adding a target, update the version list, dependencies, `buildAll`, release manifest, CI matrix, installation guide, and page matrix. Compile and run Minecraft on that target before claiming compatibility. Exact names can be checked with `javap` in Gradle's deobfuscated Minecraft jars.

## Save compatibility comes first

Players keep pet history for years. Never discard that history for a code cleanup.

- Never rename or remove existing saved AnimalRecord fields, including `birth_world_day`, `death_epoch`, `variant_id`, and `name`.
- `variant_id` holds every species' variant: an enum's serialized name (`blue`, `pink`), a registry key (`minecraft:warm`), `coat/markings` for horses, or `main/hidden` panda genes. Keep these strings identical across Minecraft versions so an upgraded world keeps its variants. `variant_origin` saves how a bred animal got its variant, judged at birth, because dye and lightning change sheep and mooshroom colors later.
- `horse_stats` stores base speed, jump strength, max health, and llama strength in Minecraft's attribute units. Every value inside it is optional; null means unknown. Convert to player units only for display, in `util/HorseStatUnits.java`.
- New saved fields must use `optionalFieldOf`. Old saves must parse without the field. Handle missing values when reading it.
- RecordCodecBuilder groups cap at 16 fields. `BASE_CODEC` preserves the original 16 flat fields. The outer codec combines it with optional additions such as `tree_name`.
- FamilyTreeState keeps readable records when another record fails. It preserves unreadable and duplicate entries for resave and logs their count. Do not replace this with a strict `listOf()` or silently discard rejected entries.
- Future or unsupported format versions protect the original data and disable writes. Add a migration before changing the meaning of a data version. Current data version is 1.
- Legacy import supports both raw records and the SavedData `data` wrapper. It runs only for an uninitialized store and copies the legacy file to a migration backup. An empty modern store must not reimport deleted history.
- Every 1.21.x target shares the overworld `data/familytree.dat` file, so newer 1.21 builds load 1.21.1 history in place. 1.21.1 and 1.21.4 use `SavedData.Factory`; 1.21.5 and newer use `SavedDataType` with the same file name. 26.x reads it through legacy import.
- Pruning requires a successful JSON backup before deletion. Backups live under the world's `familytree-backups/` directory.
- Pets and named animals are never removed automatically. The farm limit only stops new unnamed animals other than pets. Automatic cleanup is opt-in (`autoPruneFarmRecords`), backs up first, and removes only deceased unnamed animals other than pets. Unclassified modded species count as pets until an entity shows otherwise.
- Document downgrade limits. Older mod versions can drop unknown fields on their next save. Never downgrade a Minecraft world for a test.

When touching persistence, load previous-release data on the same game version and check commands and the GUI. See `docs/testing.md` for disposable-world runners. Retained history in an existing smoke world is useful evidence, but is not an immutable migration fixture.

## Network and permissions

Server code owns the saved history. Both loaders dispatch shared packet handlers on the server thread. PetAccess defines view and manage rules; public snapshots remove foreign pet coordinates. Snapshot copies do not retain mutable server records.

The current protocol uses `open_request_v4`, `snapshot_pages_v5`, `discover_v3`, `rename_request_v2`, `rename_result_v3`, `waypoint_request_v1`, `waypoint_result_v1`, `relatives_request_v1`, and `relatives_result_v1`. NeoForge and Forge register protocol `4`. The waypoint and relatives channels are optional: without the waypoint channels the client takes the waypoint from its snapshot's last known position, and without the relatives channels the relatives key shows a message. 1.4.0 added the relatives channels under protocol `4`, so 1.4.0 clients still join 1.3.0 servers. Version 1.2.0 used `snapshot_pages_v4`, `rename_result_v2`, and protocol `3`; version 1.1.0 used `open_request_v3`, `snapshot_pages_v3`, `discover_v2`, and protocol `2`. Update client and server together. Incompatible packet changes need new channels or a negotiated version. Keep fields in codec encode and decode order.

Forge 1.20.1 has one event channel per payload, named like the payload, and accepts a missing remote mod. Snapshots use a request UUID, page number, total count, and byte-bounded records. The client exposes the result only after validating every page. Current page budget is 256 KiB and aggregate limit is 100,000 records. Minecraft 1.20.1 limits clientbound custom payloads to 1 MiB and serverbound ones to 32,767 bytes. Never silently truncate a family.

Commands and discovery share per-player and per-server scan cooldowns. Rate-limited requests return before scanning or serializing. Rename and parent edits return an explicit result. Validate current ownership, species, distinct IDs, and ancestry loops on the server before editing.

Every command and player action checks a `familytree.*` permission node through `ServerPetAccess.allowed`. `permissions/FamilyTreePermission.java` lists the nodes and their defaults, which come from the server settings and must reproduce 1.3.0's operator-level behavior when no permission mod is installed. Each loader installs a `platform/PermissionBackend`: fabric-permissions-api on Fabric, only when a mod provides it, and PermissionAPI on NeoForge and Forge, which register the nodes and answer with the defaults until a player's login event. A new command or action needs a node, a `.requires` or explicit check, and a row in `docs/servers.md`; the Paper plugin follows the same node names and setting keys.

## Code map

- `data/AnimalRecord.java` contains the persisted and network record schema. `name()` uses the custom tree name; `originalName()` retains the entity name. Tree names never change an entity's name tag.
- `data/FamilyTreeState.java` contains the overworld save, tolerant recovery, legacy import, and prune backup.
- `event/PetLifecycleListeners.java` records breeding, tame, name, unload, and death events. Existing loaded pets enter through discovery. Each last-seen update also refreshes horse stats.
- `util/PetFilter.java` decides what is tracked. Horse-type mounts need a Minecraft owner. Camels always report as tamed and trap skeleton horses have no owner, so they join only when a player rides or breeds them. Records that already exist stay current even when the animal is not tamed, such as foals. Other animals join only through `PetLifecycleListeners.recordBirth` (a player's breeding, or a recorded parent), the name tag mixin, and the linking stick, subject to `settings/TrackingRules`.
- `util/AnimalCategory.java` sorts species into pets, farm, wildlife, and modded. `util/EntityVariants.java` reads and applies variants through `VariantHolder` before 1.21.5 and entity data components after. `util/VariantRules.java` holds rarity, inheritance outcomes, and label keys without Minecraft classes; `util/VariantText.java` turns them into components.
- `data/FarmRecords.java` and `data/FarmCleanup.java` count unnamed animals other than pets against `farmRecordLimit` and run the opt-in cleanup.
- `permissions/` contains ownership and parent-link rules and the permission nodes. Horse and donkey parents are allowed for a mule.
- `settings/ServerSettings.java` defines every server setting: its default, validation, and the commented file Family Tree writes. `FamilyTreeServerSettings` loads it at server start and on `/familytree reload`, keeps the text of values it can't use, and only rewrites the file when its text changes. Keep defaults identical to the previous release and keep every existing key working. `util/SpeciesTags.java` reads the `familytree:tracked` and `familytree:ignored` tags; the settings file wins over them. 1.20.1 keeps its tag files in `tags/entity_types/`, and every build file excludes the other folder.
- `network/` contains cooldowns, page splitting, and shared handlers. Loader adapters register channels and events. `network/backport/` exists only in 1.20.1 builds; `PayloadCodecs` there encodes payloads for the 1.20.1 Fabric and Forge adapters.
- `command/FamilyTreeCommand.java` handles list, scan, locate, info, pairing, age corrections, and pruning. Name edits reject ambiguity and accept exact UUIDs.
- `interaction/LinkingTool.java` retains the named-stick workflow and rechecks permissions at confirmation. Sessions clear on logout and stop.
- `util/Genealogy.java` calculates generations with an explicit stack and checks cycles. Derived generations are never persisted.
- `util/Kinship.java` names how two animals relate and computes coancestry for the breeding planner. `util/CloseRelation.java` maps it to the six relations the world highlight shows, with their colors and labels. Pass Kinship only records the viewer may see.
- `client/SnapshotAssembler.java` validates paged replies and ignores old requests.
- `client/screen/` contains browser, tree, settings, help, rename, parent editor, and pet picker. Screens use FamilyTreeScreen to adapt input and rendering APIs.
- `client/export/` copies a tree and renders a PNG on a background task. `PortraitCapture` reads model portraits for species without texture portraits from the screen, drawn on black and white to recover transparency. `PetPortraits` cuts horse side profiles from the equine texture layout, which 1.20.1, 1.21.1, 1.21.11, and 26.x share. 1.20.1 wolves have one coat, so their records have no variant. Exports omit explicit player names and coordinates. User-entered pet names may still contain personal information.
- `platform/` and `client/platform/` hold loader-independent paths and transports. `ClientEnvironment` holds versions and installed mod IDs from each loader's client entry point. Shared code has no Fabric dependency.
- `menu/` holds the chest, anvil, and lectern menus that `/familytree` opens for clients without the mod. Their `clicked` overrides refuse every click before Minecraft moves an item, resend the player's inventory, and run the button's action after the click packet finishes. Never call the vanilla click handling there.
- `text/` reads the mod's language files on the server. `ServerText` gives every `familytree.` key a fallback in the receiving player's language, so vanilla clients read translated text. Send chat and command output through `ServerText.send`, `success`, or `failure`. 1.20.1 records the client's language with `ClientLanguageMixin`, which only the 1.20.1 Fabric and Forge builds list.
- `integration/PetInsight.java` builds the tooltip data for one viewer with PetAccess rules and stores it in a CompoundTag. `integration/jade/` and `integration/wthit/` hold the server data providers; `client/integration/` holds the tooltip lines, the client halves of those plugins, and map waypoints. Without server data, tooltips use the client's last snapshot.
- `client/relatives/WorldRelatives.java` runs the relatives key: it picks the animal the player looks at, asks the server over `relatives_request_v1`, caches replies while the key is held, and spawns the particle lines. `network/NearbyRelatives.java` builds the reply with PetAccess, the 64-block range, and the 32-relative cap. The client mixins in `mixin/client/` outline relatives through Minecraft's glowing path (`shouldEntityAppearGlowing` and `getTeamColor`) and swap their name tags for labels. Before 1.21.2 `EntityRenderer.render` returns early, so the label hooks every return.
- `client/integration/MapWaypoints.java` asks the server for a pet's position over `waypoint_request_v1`, then adds the waypoint through JourneyMap's API or a Xaero's Minimap chat share. The waypoint follows Locate's ownership rule.
- `client/feedback/FeedbackSender.java` and `client/screen/FeedbackScreen.java` send player feedback to `tools/feedback-endpoint/`, a Vercel function that emails it through Resend. The screen must list every value in the request body. Nothing may send feedback without the player pressing Send.

## Optional integrations

Jade, WTHIT, Mod Menu, JourneyMap, and Xaero's Minimap are optional. Never bundle them or reference their classes outside their integration packages and entry points. Jade, WTHIT, and Mod Menu are compile-only dependencies; JourneyMap compiles against `src/stubs`, which must match the JourneyMap 2.0.0 API signatures and never enter a jar. Xaero's Minimap has no API dependency. Fabric loads them through `fabric.mod.json` entrypoints, NeoForge and Forge through `@WailaPlugin` and `@JourneyMapPlugin`, and WTHIT through `waila_plugins.json` on every loader. Each build file maps the integration versions per target. A target without a WTHIT build excludes `**/integration/wthit/**` and `waila_plugins.json`. When adding a target, add its integration versions or exclusions, then run the integration scene from `docs/testing.md`.

Tooltips and waypoints show nothing a player couldn't see in Family Tree's own screens. Keep tooltip data free of coordinates and filter parents with PetAccess.

All new player-facing text uses translation keys in `assets/familytree/lang/en_us.json`. Add keys for new errors rather than raw English messages. Add each new key to all 15 language files; `LangParityTest` requires the same keys and format arguments everywhere. Use Minecraft's own translations, such as `entity.minecraft.wolf` or `death.attack.*`, for game nouns and death messages. Clear client entity previews when disconnecting.

## Paper and Spigot plugin

`paper/` is a separate Gradle build for one plugin jar that runs on Spigot, Paper, and Paper forks from 1.20.1 to 26.3. `docs/plugin.md` records why it uses only the Bukkit API. Build it with `./gradlew -p paper build`; `buildAll` runs that too.

- It compiles the Minecraft-free classes listed in `sharedJava` in `paper/build.gradle` straight from `src/main/java`, against its own `AnimalRecord` and `HorseStats` with the same methods. Keep those files free of Minecraft imports. When a shared class needs a new record method, add it to `paper/src/main/java/com/egakh/familytree/data/` too.
- `storage/RecordNbt.java` mirrors `AnimalRecord.CODEC` field for field. A new saved field goes into its `KNOWN` set, `encode`, and `decode`. `StoreCompatibilityTest` reads files the mod wrote and must write back the same NBT.
- `network/Payloads.java` mirrors the mod's payload codecs. `WireParityTest` compares its bytes with `WireFormatTest` and fails when those change. A new channel or field needs the same change in both.
- Compile against Spigot 1.20.1 and reach APIs that changed shape later, such as `Cat.Type`, `Attribute`, and `InventoryView`, through `track/Compat.java`. Paper-only code lives in `paper/src/paper`.
- Plugin-only strings are in `paper/src/main/resources/familytree-plugin/lang/`, in all 15 languages.
- `scripts/plugin_server.py` and `scripts/plugin_client_check.py` run real servers and an unmodded client. List a server version in `PLUGIN_GAME_VERSIONS` in `scripts/release_manifest.py` only after both pass on it.

## Testing and publication

Run `buildAll` after changing Minecraft APIs. Development-only source sets `src/smoke` and `src/demo` stay outside release jars. `docs/testing.md` describes server, client, and real gameplay checks.

Commit the tested source, then run `python scripts/release_manifest.py`. It checks exact metadata, Java bytecode, icon, license, and development-class exclusions. It copies jars and source jars into `build/releases/<version>/` and records SHA-512 hashes and the source commit. Publish those copies without rebuilding.

Modrinth project ID is `CVQKDAe7`. The plugin jar is published to the same project with the paper, spigot, and purpur loaders; `publish_curseforge.py` skips it because CurseForge lists Bukkit plugins in a separate project. Fabric API dependency ID is `P7dR8mSH`. Publish one version per game and loader. Follow `docs/MODRINTH_API.md` or `scripts/publish_modrinth.py`; the PAT lives in ignored `.env`. Never print or commit it. Daily public download counts require no token and no player telemetry.
