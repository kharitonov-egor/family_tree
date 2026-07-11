# CLAUDE.md

## What this is

Family Tree is a Fabric mod (Minecraft 26.1.1 + 26.2, Java 25, Mojang names) that tracks tamed pets and their lineage. Tame or breed a cat, wolf, parrot, or horse-type animal and the mod records it: parents, birth day, name, owner, coat variant, last known location, and eventually its death (day and cause). Players browse it all in a GUI: a searchable pet browser and a pan/zoom family tree with 3D pet portraits.

Mod id `familytree`, package `com.egakh.familytree`. Mod version lives in `gradle.properties` (`mod_version`); the list of targeted Minecraft versions lives in `settings.gradle`. One source tree builds a jar per Minecraft version via **Stonecutter** — see "Multi-version build" below.

## Publishing releases

The mod is published on Modrinth. When the user wants to publish a new version, use the Modrinth API and follow `docs/MODRINTH_API.md` (auth, headers, version-upload + add-file endpoints, curl examples). There is now one jar **per Minecraft version** (`familytree-<modver>+<mcver>.jar`); decide with the user how to lay them out on Modrinth (separate versions per MC vs. multiple files on one version).

## Multi-version build (Stonecutter)

The mod targets several Minecraft versions from a single source tree using [Stonecutter](https://stonecutter.kikugie.dev/) `0.9.6` + `loom-back-compat` `0.3`.

- **Build one jar per target:** `./gradlew buildAll` → jars land in `versions/<mcver>/build/libs/familytree-<modver>+<mcver>.jar`. Build a single target with `./gradlew :26.2:build`. Per-version compile checks: `./gradlew :26.2:compileJava :26.2:compileClientJava`.
- **`versions/` is fully generated** (and gitignored). Don't edit or commit it.
- **The three build files:**
  - `settings.gradle` — the version list (`versions '26.1.1', '26.2'`) and `vcsVersion` (the version the source is authored against, `26.1.1`).
  - `stonecutter.gradle` — the controller: active version, the `buildAll` task, and the version-gated API **replacements** (see below).
  - `build.gradle` — applied to each version node; picks the Fabric API version by the active MC version (`fabricApiVersions` map) and computes the `~26.x` range stamped into `fabric.mod.json`. `loader_version`/`loom_version` are shared in `gradle.properties`.
- **Mappings:** `loom-back-compat`'s `loomx.applyMojangMappings()` resolves 26.x Mojang mappings; plain Loom auto-mappings does not fire inside a Stonecutter node.
- **Source is authored in 26.1.1 names.** APIs that were renamed in a newer version are rewritten at build time by `replace` lines in `stonecutter.gradle`, gated by `string(current.parsed >= '<mcver>')`. Do **not** scatter `//?` conditionals through the source for simple renames — add a `replace` instead. Current replacements (26.1.1 → 26.2):
  | 26.1.1 | 26.2 |
  |---|---|
  | `Minecraft.setScreen(...)` | `setScreenAndShow(...)` |
  | `EntityType.CAT` / `.WOLF` | `EntityTypes.CAT` / `.WOLF` (fully-qualified, no import needed) |
  | `gui.setOverlayMessage(c, false)` | `player.sendOverlayMessage(c)` |
- **Adding a new Minecraft version:** (1) add it to `versions` in `settings.gradle`; (2) add its `<fabric_api>+<mcver>` to `fabricApiVersions` in `build.gradle`; (3) add `:<mcver>:build` to the `buildAll` task's `dependsOn`; (4) build it, and for each `cannot find symbol` add a `replace` under a `string(current.parsed >= '<mcver>')` gate in `stonecutter.gradle` (find the new name with `javap`, see below). Never change the 26.1.1-authored source to a newer name — that breaks the older target.

## Backward compatibility is priority #1

Players keep long-running worlds with years of pet history. A version upgrade must NEVER lose or corrupt their data. Every change is checked against this first, before features, before cleanliness. Concretely:

- **Never rename or remove existing NBT fields** in `AnimalRecord.CODEC` (`birth_world_day`, `death_epoch`, `variant_id`, ...). Old saves are parsed by field name; a rename silently drops that data for every existing pet.
- **New persisted fields must be `optionalFieldOf`** with no required default semantics. An old save that lacks the field must parse cleanly (as `Optional.empty()` / `null`), and code reading the field must handle `null` (e.g. a pet that died before death causes existed still renders as plain "deceased").
- **`RecordCodecBuilder` groups cap at 16 fields** and `AnimalRecord.CODEC` is currently at exactly 16. To add data, nest it: create a small record with its own `CODEC` (like `LastSeen` and `DeathCause`) and add it as one optional field, instead of adding scalars.
- **`FamilyTreeState` uses a tolerant list codec** (`tolerantList` in `FamilyTreeState.java`): a single corrupt/unparseable record is skipped instead of failing the whole file. Keep this behavior; never replace it with a strict `listOf()`.
- **Legacy migration stays**: `FamilyTreeState.tryLoadLegacy` imports the pre-SavedDataType `data/familytree.dat` file when the modern store is empty. There is also a `data_version` field (`CURRENT_DATA_VERSION`) reserved for future format migrations; bump it and write migration code rather than changing field meanings in place.
- **Network `STREAM_CODEC` is append-only**: new fields go at the END of both `encode` and `decode`, in the same order. Client and server ship together, so appending is safe; reordering is not.
- **Downgrade caveat** (document in release notes when relevant): old mod versions load new saves fine but drop unknown fields on their next save.

When touching persistence, verify by loading a world saved with the previous release and checking `/familytree list` and the GUI still show everything.

## Code map

```
src/main/java/com/egakh/familytree/
  FamilyTreeMod.java        entrypoint: wires packets, listeners, tool, commands
  data/AnimalRecord.java    the per-pet record: fields, mutators, CODEC (NBT) + STREAM_CODEC (network)
  data/FamilyTreeState.java SavedData store on the overworld (id familytree:familytree),
                            ConcurrentHashMap<UUID, AnimalRecord>, tolerant list codec, legacy import
  event/PetLifecycleListeners.java
                            central lifecycle logic: onBred, onDeath (records DeathCause),
                            tame hooks, ensureRecord (create-or-refresh), stampPosition,
                            ENTITY_UNLOAD position stamping, scanLoadedPets
  interaction/LinkingTool.java
                            "familytree"-renamed stick: select parent1/parent2/child via
                            UseEntityCallback, glow feedback, clickable chat confirm
  command/FamilyTreeCommand.java
                            /familytree list|scan|locate|info|pair|unpair|setage|setbirth|prune|confirmlink|cancellink
  network/                  OpenFamilyTreeRequest (C2S) + FamilyTreeSnapshotPayload (S2C snapshot)
  settings/                 server-side view permissions
  naming/NameGenerator.java auto-names for unnamed pets
  util/PetFilter.java       what counts as trackable (tamed TamableAnimal or AbstractHorse)
  util/TimeUtil.java        world-day and epoch helpers
  util/Genealogy.java       derived generation numbers (founder = Gen 1); computed from
                            the parent graph, never persisted; used by command + client
  mixin/                    breed/tame/rename hooks + Cat/Wolf variant invokers

src/client/java/com/egakh/familytree/client/
  screen/FamilyTreeBrowserScreen.java  pet list: search, filters, species tabs
  screen/FamilyTreeViewScreen.java     pan/zoom tree (computes generations for the snapshot)
  screen/TreeRenderer.java             node cards (name, generation, owner, age, death line)
  screen/TreeLayout.java               tree layout
  screen/PetFaceRenderer.java          3D pet head portraits
  keybind/, settings/                  open-tree keybind (H by default), display toggles
                                       (showGeneration/showAge/showBirthDay)
```

## How data flows

1. Mixins/events (breed, tame, rename, death, unload) call into `PetLifecycleListeners`, which mutates `FamilyTreeState` via `state.update(...)` / `state.put(...)` (both call `setDirty()` so Minecraft persists).
2. Everything is server-authoritative. The client GUI opens by sending `OpenFamilyTreeRequest`; the server filters records by ownership/permissions and answers with `FamilyTreeSnapshotPayload`. The client never writes.
3. `AnimalRecord` is the single schema. Persisting a new fact = field + mutator + `CODEC` entry (optional!) + `STREAM_CODEC` append + wherever it gets captured, then render it in `TreeRenderer` or a command.

## Conventions and gotchas

- **Mapping quirks** — the source is written against 26.1.1 names; verify a name with `javap` against the deobf jars in `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-common-deobf/<mcver>/` (common) and `.../minecraft-clientonly-deobf/<mcver>/` (client) before assuming it. 26.1.1 specifics:
  - `Identifier`, not `ResourceLocation`; `ResourceKey.identifier()`, not `.location()`.
  - No `CommandSourceStack.hasPermission(...)`; use `Commands.LEVEL_GAMEMASTERS.check(source.permissions())`, or `Commands.hasPermission(...)` as a predicate for `.requires(...)`.
  - Screens render via `GuiGraphicsExtractor` (`extractRenderState`), not `GuiGraphics.render`.
  - `Wolf.getVariant()` is private; use the `WolfVariantInvoker` mixin.
  - Names that changed by 26.2 are NOT edited in source — they are handled by `replace` lines in `stonecutter.gradle` (see the Multi-version build section). When a new-version compile fails with `cannot find symbol`, `javap` the new jar for the new name and add a `replace`, don't touch the source.
- All player-facing text goes through translation keys in `src/main/resources/assets/familytree/lang/en_us.json`.
- Names are matched case-insensitively; multiple pets can share a name, so name-based commands should handle multiple matches (see `runLocate`).
- Build: `./gradlew buildAll` (per-version jars in `versions/<mcver>/build/libs/`), or `./gradlew :26.2:build` for one. Dev run against a specific version: switch active with `./gradlew "Set active project to 26.2"` then `./gradlew runClient` (game dir under `run/`). Compile checks are per version and per source set: `./gradlew :26.2:compileJava :26.2:compileClientJava` (split main/client source sets).
- When touching Minecraft API surface, verify **both** targets compile (`:26.1.1:compileJava :26.1.1:compileClientJava` and the same for `:26.2`) — a change that compiles on one version can break the other.
