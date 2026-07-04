# CLAUDE.md

## What this is

Family Tree is a Fabric mod (Minecraft 26.1.1, Java 25, Loom auto-mappings / Mojang names) that tracks tamed pets and their lineage. Tame or breed a cat, wolf, parrot, or horse-type animal and the mod records it: parents, birth day, name, owner, coat variant, last known location, and eventually its death (day and cause). Players browse it all in a GUI: a searchable pet browser and a pan/zoom family tree with 3D pet portraits.

Mod id `familytree`, package `com.egakh.familytree`. Version lives in `gradle.properties` (`mod_version`).

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
  mixin/                    breed/tame/rename hooks + Cat/Wolf variant invokers

src/client/java/com/egakh/familytree/client/
  screen/FamilyTreeBrowserScreen.java  pet list: search, filters, species tabs
  screen/FamilyTreeViewScreen.java     pan/zoom tree
  screen/TreeRenderer.java             node cards (name, age, owner, death line)
  screen/TreeLayout.java               tree layout
  screen/PetFaceRenderer.java          3D pet head portraits
  keybind/, settings/                  open-tree keybind (unbound by default), display toggles
```

## How data flows

1. Mixins/events (breed, tame, rename, death, unload) call into `PetLifecycleListeners`, which mutates `FamilyTreeState` via `state.update(...)` / `state.put(...)` (both call `setDirty()` so Minecraft persists).
2. Everything is server-authoritative. The client GUI opens by sending `OpenFamilyTreeRequest`; the server filters records by ownership/permissions and answers with `FamilyTreeSnapshotPayload`. The client never writes.
3. `AnimalRecord` is the single schema. Persisting a new fact = field + mutator + `CODEC` entry (optional!) + `STREAM_CODEC` append + wherever it gets captured, then render it in `TreeRenderer` or a command.

## Conventions and gotchas

- **26.1.1 mapping quirks** (verify with `javap` against `~/.gradle/caches/fabric-loom/minecraftMaven/.../minecraft-merged-deobf-26.1.1.jar` before assuming older names):
  - `Identifier`, not `ResourceLocation`; `ResourceKey.identifier()`, not `.location()`.
  - No `CommandSourceStack.hasPermission(...)`; use `Commands.LEVEL_GAMEMASTERS.check(source.permissions())`, or `Commands.hasPermission(...)` as a predicate for `.requires(...)`.
  - Screens render via `GuiGraphicsExtractor` (`extractRenderState`), not `GuiGraphics.render`.
  - `Wolf.getVariant()` is private; use the `WolfVariantInvoker` mixin.
- All player-facing text goes through translation keys in `src/main/resources/assets/familytree/lang/en_us.json`.
- Names are matched case-insensitively; multiple pets can share a name, so name-based commands should handle multiple matches (see `runLocate`).
- Build: `./gradlew build` (jar in `build/libs/`), dev run: `./gradlew runClient` (own game dir under `run/`). Compile checks: `compileJava` + `compileClientJava` (split source sets).
