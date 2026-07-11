# Family Tree

[![Modrinth](https://img.shields.io/modrinth/dt/familytree?logo=modrinth&label=Modrinth)](https://modrinth.com/mod/familytree)

A Fabric mod that automatically tracks the lineage of your tamed, bred pets and shows it as an in-game family tree.

Tame two animals, breed them, and Family Tree records the baby's parents, birth day, name, owner, and species. Browse everything from a dedicated screen with live 3D portraits of each pet, or walk a pet's ancestry as a zoomable tree.

**Download:** [Modrinth](https://modrinth.com/mod/familytree) · [GitHub Releases](https://github.com/kharitonov-egor/family_tree/releases)

> **Versions:** Minecraft `26.1.x` and `26.2` · Fabric Loader `0.19.2` · Fabric API · Java `25`

![Tracked Pets browser](docs/tracked-pets-browser.webp)

![Family tree view](docs/family-tree-view.webp)

## Features

- **Automatic lineage tracking**: when a tracked species breeds, the child is recorded with both parents, its birth (world day + real timestamp), auto-generated name, and owner.
- **Tracked species**: cats, wolves, parrots, horses, donkeys, mules, llamas, camels.
- **Tracked Pets browser**: a searchable, filterable list of every pet:
  - Live **3D model heads** for cats and dogs (correct coat variant), species-colored cards, and an Alive/Deceased status pill.
  - Filter by **All / Alive / Deceased**, search by name or species, and jump into a species' combined tree.
  - Operators can toggle **Showing: Mine / All** to view every player's pets.
- **Family tree view**: pan and zoom an ancestry graph for any pet. Nodes show the 3D head, name, generation, owner, age, and death state.
- **Generation numbers**: every pet shows its lineage depth — a founder (no tracked parents) is **Gen 1**, its offspring **Gen 2**, and so on. Displayed on tree cards and in `/familytree info`.
- **Pet locator**: `/familytree locate <name>` tells you where a pet is (coordinates, dimension, and distance). If its chunk is unloaded, you get the last known spot instead. Pets with the same name are all listed.
- **Death cause tracking**: when a tracked pet dies, the mod records what killed it and when. The tree shows things like "Slain by Creeper, day 43" or "Died of fall, day 12".
- **Display settings**: toggle showing generation, age, and/or birth day from the in-screen Settings button.
- **Linking stick**: rename a stick to `familytree` in an anvil, then right-click parent 1, parent 2, and child, and confirm in chat to record parentage for pets from existing worlds. Sneak-click clears a pet's parents. The clicked pet glows for a few seconds so you always know which one you selected.
- **Management commands**: manually pair/unpair parents, fix ages, import existing pets, and prune records.

## Requirements

- Minecraft `26.1.1` or `26.2` (download the matching jar)
- [Fabric Loader](https://fabricmc.net/) `>= 0.16`
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Java `25` (required by these Minecraft versions)

Install it like any Fabric mod: drop the jar (and Fabric API) into your `mods/` folder. Works in single-player and on dedicated servers. The mod must be installed on the **server** (or single-player world) for tracking; clients without it that join a server with it will simply not see the screen.

## Usage

### Opening the screen

The mod adds an **"Open Family Tree"** keybind. It is bound to **H** by default (rebindable in `Options > Controls > Family Tree`); press it in-game to open the Tracked Pets browser.

### Commands

All commands are under `/familytree`:

| Command | Description |
| --- | --- |
| `/familytree list` | List all tracked pets in chat. |
| `/familytree scan` | Import already-tamed pets that are currently loaded in the world (also refreshes coat variants on existing records). |
| `/familytree locate <name>` | Show a pet's coordinates, dimension, and distance. Falls back to the last known position if the pet is not loaded. Lists every pet with that name. |
| `/familytree info [name]` | Show details for a pet, or command help when no name is given. |
| `/familytree pair <parentA> <parentB> <child>` | Manually link two parents to a child (same species, all distinct). |
| `/familytree unpair <child>` | Clear a child's parent links. |
| `/familytree setage <name> <days>` | Set a pet's age to a relative number of world days. |
| `/familytree setbirth <name> <day>` | Set a pet's birth day directly. |
| `/familytree prune deceased` | Remove all deceased records. *(operators only)* |
| `/familytree prune species <id>` | Remove all records of a species, e.g. `minecraft:wolf`. *(operators only)* |

> Use `/familytree scan` after installing the mod on an existing world to back-fill pets you tamed before, and to populate coat variants for the 3D portraits.

## Building from source

This is a Fabric Loom project that targets multiple Minecraft versions from one source tree using [Stonecutter](https://stonecutter.kikugie.dev/).

```bash
./gradlew buildAll          # build a jar for every supported Minecraft version
./gradlew :26.2:build       # or just one version
```

Jars land in `versions/<mcver>/build/libs/familytree-<modver>+<mcver>.jar`. To launch a dev client/server, switch the active version first, then run:

```bash
./gradlew "Set active project to 26.2"
./gradlew runClient   # or runServer
```

The set of targeted Minecraft versions lives in `settings.gradle`; contributors adding a new version should read the "Multi-version build" section of `CLAUDE.md`.

## Project layout

```
src/main      - common + server: tracking, data model, persistence, commands
src/client    - client: screens (browser, tree), 3D pet rendering, keybind, settings
src/main/resources/familytree.mixins.json - entity hooks (breeding, taming, naming, variants)
```

Pet data is stored server-side (saved data) and streamed to the client on demand; the GUI never trusts client-side state for ownership or visibility.

## License

MIT, see `LICENSE` (or the `license` field in `fabric.mod.json`).
