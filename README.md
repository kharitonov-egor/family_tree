# Family Tree

[![Modrinth downloads](https://img.shields.io/modrinth/dt/familytree?logo=modrinth&label=Modrinth)](https://modrinth.com/mod/familytree)

Keep the family history of your Minecraft pets and the animals you breed. See who their parents were, find a missing pet's last known location, and save a family tree as an image.

[Download Family Tree](https://modrinth.com/mod/familytree/versions)

[Creator guide, demo steps, and downloadable clips](docs/creator-guide.md)

[Download on CurseForge](https://www.curseforge.com/minecraft/mc-mods/family-tree/files). Fabric and NeoForge builds are available.

![A wolf family exported from Minecraft](docs/family-tree-gameplay-export.png)

Keep your first wolf in the family tree after it's gone. Family Tree records parents and descendants as you play, then exports the family as a PNG.

![Vanilla wolves breed and their family tree exports](docs/family-tree-gameplay.webp)

An automated scene in Minecraft 26.2. Vanilla feeding and AI breeding produce the actual puppy. Family Tree records its parents and retains its deceased ancestor. [Landscape video](docs/family-tree-gameplay.mp4) and [vertical video](docs/family-tree-gameplay-vertical.mp4) are ready to share.

## Install

Choose the file that matches both your Minecraft version and mod loader.

| Minecraft | Loader | Java | Extra dependency |
| --- | --- | --- | --- |
| 1.20.1 | Fabric | 17 or newer | [Fabric API](https://modrinth.com/mod/fabric-api) for 1.20.1 |
| 1.20.1 | Forge 47.1.0 or newer | 17 or newer | None |
| 1.21.1 | Fabric | 21 or newer | [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.1 |
| 1.21.1 | NeoForge 21.1.252 or newer within 21.1 | 21 or newer | None |
| 1.21.4 | Fabric | 21 or newer | Fabric API for 1.21.4 |
| 1.21.4 | NeoForge 21.4.161 or newer within 21.4 | 21 or newer | None |
| 1.21.5 | Fabric | 21 or newer | Fabric API for 1.21.5 |
| 1.21.5 | NeoForge 21.5.101 or newer within 21.5 | 21 or newer | None |
| 1.21.8 | Fabric | 21 or newer | Fabric API for 1.21.8 |
| 1.21.8 | NeoForge 21.8.57 or newer within 21.8 | 21 or newer | None |
| 1.21.10 | Fabric | 21 or newer | Fabric API for 1.21.10 |
| 1.21.10 | NeoForge 21.10.64 or newer within 21.10 | 21 or newer | None |
| 1.21.11 | Fabric | 21 or newer | Fabric API for 1.21.11 |
| 1.21.11 | NeoForge 21.11.48 or newer within 21.11 | 21 or newer | None |
| 26.1.1 | Fabric | 25 or newer | Fabric API for 26.1.1 |
| 26.1.2 | Fabric | 25 or newer | Fabric API for 26.1.2 |
| 26.2 | Fabric | 25 or newer | Fabric API for 26.2 |
| 26.2 | NeoForge 26.2.0.88 or newer within 26.2 | 25 or newer | None |
| 26.3 | Fabric | 25 or newer | Fabric API for 26.3 |
| 26.3 | NeoForge 26.3.0.37-beta or newer within 26.3 | 25 or newer | Beta loader |

Put the matching jar in `mods/`. Fabric builds use Fabric Loader 0.19.3 for testing. They also run on Quilt Loader 0.30.1 or newer with Fabric API. NeoForge and Forge builds do not need Fabric API.

Install Family Tree on the server for tracking and on your client for the screens. In singleplayer, one installation does both. Use the same Family Tree version on both sides. Players without the client mod can still join and type `/familytree` to browse in chest menus. See [Players without the client mod](#players-without-the-client-mod).

## Start with the pets you already have

1. Join your world and press **H**. You can change the key under Options > Controls > Family Tree.
2. Visit your pets. Family Tree records existing tamed pets as their chunks load. **Find existing pets** also checks the pets currently loaded on the server.
3. Open a pet's tree, or choose a species to see its combined family trees. Breed pets while the mod is installed to record their children's parents automatically.

Pets join on their own: tamed cats, wolves, parrots, horses, donkeys, mules, llamas, trader llamas, camels, skeleton horses, and zombie horses. On 1.21.11 and newer, tamed nautiluses and zombie nautiluses join too. Parrots have individual records but cannot breed in vanilla Minecraft. Other animals join when you breed, name, or add them, as described [below](#animals-you-breed-name-or-add). Portraits show each animal's in-game model, so they match coats, markings, and wool colors. Wolves in 1.20.1 have a single coat.

Minecraft does not retain older parentage or birth dates. Newly discovered pets start with an unknown family and the discovery day as their recorded birth day. Select a pet and choose **Add parents** to record a family you remember. The picker searches names, species, and UUIDs. Confirm the two parents before saving. The linking stick and age commands also remain available. Horse and donkey parents can link to a mule.

## Browse, find, and remember

Search by pet name or species, filter living or deceased pets, and follow generations through a tree you can pan and zoom. Select a pet to center it, rename it in the tree, or locate it. Custom tree names leave name tags in the world unchanged.

**Locate pet** reports coordinates, dimension, and distance in chat. If the pet is unloaded, it reports the last recorded location and day. It does not load chunks or teleport pets.

Death records retain the pet's name, family, death day, and cause when known. Settings let you show or hide generation, age, birth day, and horse stats. Hide a branch to focus on one part of a large family, then use **Show all** to restore it.

![The pet browser with search, status filters, and Find existing pets](docs/tracked-pets-browser.webp)

## Players without the client mod

On a server that runs Family Tree, a player with an unmodded client types `/familytree` and gets a chest menu instead of the screens. It lists their pets with spawn egg icons, names, age, and living or deceased status, 45 to a page. Filter by status or species, or search by name in an anvil.

Click a pet to see its parents, grandparents, and children, then click any relative to move through the family. Owners and operators can also:

- locate the pet, with the result in chat,
- rename it in Family Tree through an anvil,
- add or clear its parents with a picker and a confirmation step,
- read its family as a written book.

The menus follow the same permissions and cooldowns as the screens. They never show coordinates, and another player's pets are view-only. Every click is refused before Minecraft moves an item, so players cannot take, move, or copy anything through them, including with shift-click, number keys, dragging, or the offhand key.

Players who have the client mod keep the full screens, and `/familytree menu` opens the chest menu for them too.

Menu text and chat messages follow each player's Minecraft language when Family Tree has a translation, and English otherwise. Server operators can change two settings in `config/familytree-server.properties`, then restart:

- `vanillaMenu=false` turns the chest menu off. `/familytree` then prints the command list.
- `commandAliases=pets,ft` adds `/pets` and `/ft` as other names for `/familytree`. Family Tree skips an alias that another command already uses and logs a warning.

## Horse breeding stats

Family Tree records speed, jump height, and health for horses, donkeys, mules, llamas, trader llamas, camels, skeleton horses, and zombie horses. Speed is in blocks per second, jump in blocks, and health in hearts, using the Minecraft Wiki's conversions. Llamas also show their chest slots.

Each card in a tree shows the stats. A green ▲ marks a stat higher than both parents, and a red ▼ marks one lower than both. Select a foal to compare each value with its parents'. In the browser, **Sort** orders pets by speed, jump, or health.

![A horse family exported with speed, jump, and health](docs/horse-breeding-export.png)

An automated scene in Minecraft 26.3. Two tamed horses ate golden carrots, and vanilla AI bred them. Ember's jump is lower than both parents'.

Stats are base values, so potion effects don't change them. Family Tree reads them when it records an animal and again whenever the server loads, unloads, or scans it. Horses recorded by an older version get stats the next time they load.

Minecraft treats every camel as tamed, and skeleton trap horses are tamed without an owner. Family Tree records these animals once a player rides or breeds them, and that player becomes their owner in Family Tree. Their owner in Minecraft stays unchanged, and wild camels nobody rides stay out of the tree.

## Animals you breed, name, or add

Family Tree also records cows, mooshrooms, sheep, pigs, chickens, rabbits, goats, foxes, axolotls, frogs, bees, turtles, pandas, ocelots, hoglins, striders, sniffers, armadillos, and breedable animals from other mods. Wild animals stay out of the history until a player does one of these things:

- Breeds them. The baby and both parents join, and the player who fed them owns them in Family Tree. Later babies of a recorded family join even when nobody fed the parents.
- Uses a name tag on one. The player who named it owns it.
- Clicks one with the linking stick. The player who clicked owns it, and it becomes the next pet in the linking selection.

A baby belongs to its parents' owner when the parents already have one. Frogs, sniffers, and turtles hatch from spawn or eggs, so Minecraft never links their babies to the parents. Breeding frogs or sniffers still adds both parents for the player; turtles need a name tag or the linking stick. Name or add the hatchlings, then use **Add parents**.

Cards, the details panel, and exports show each animal's variant: axolotl, mooshroom, frog, and llama colors, wool color, panda genes, and rabbit and fox types. On Minecraft 1.21.5 and newer, cows, pigs, and chickens show their warm, cold, or temperate variant. A gold ★ marks a rare variant: a blue axolotl, a brown mooshroom, or a brown panda. Select a baby to compare its variant with its parents'. The panel explains surprises, for example that the parents' wool colors mixed, that a panda shows a gene both parents carried hidden, or that a bred axolotl turned blue, which happens about 1 time in 1,200.

Unnamed animals other than pets are named after their species with a short code, such as "Sheep 3F2A1C". The pet names such as Biscuit stay free for pets. When the history holds more than one kind of animal, the browser shows tabs for Pets, Farm, Wildlife, and Modded, and the species buttons follow the selected tab.

![A pink lamb and its red and white parents exported from Minecraft](docs/sheep-family-export.png)

An automated scene in Minecraft 26.3. The player named two sheep with name tags and fed them wheat. Vanilla AI bred a pink lamb from a red and a white parent, and the lamb kept its automatic name.

Modded animals use the same rules. In a test with Naturalist 2.0.7 on NeoForge 1.21.1, deer named with name tags and fed apples bred through vanilla AI, the fawn joined with both parents, and the deer model appeared in the browser, tree, and export. Other animal mods have not been tested. Mods that store variants their own way show no variant text.

### Server settings for animals

`config/familytree-server.properties` decides which new animals join. Settings never remove history; animals already recorded keep their record up to date. Restart the server after editing the file.

| Setting | Default | What it does |
| --- | --- | --- |
| `trackFarmAnimals` | `true` | Cows, mooshrooms, sheep, pigs, chickens, rabbits, and goats |
| `trackWildlife` | `true` | Foxes, axolotls, frogs, bees, turtles, pandas, ocelots, hoglins, striders, sniffers, armadillos, and other vanilla animals |
| `trackModdedAnimals` | `true` | Breedable animals from other mods |
| `trackedSpecies` | empty | Comma-separated species to record even when their category is off, such as `minecraft:fox` |
| `ignoredSpecies` | empty | Comma-separated species to never record. This also stops new pets of that species. |
| `farmRecordLimit` | `5000` | How many unnamed animals other than pets the history keeps, from 0 to 50,000. At the limit, breeding stops adding them and tells the breeder once. Pets and named animals are always recorded. |
| `autoPruneFarmRecords` | `false` | When the limit is reached, save a JSON backup in `familytree-backups/`, then delete the oldest deceased unnamed animals other than pets until three quarters of the limit remain |

Automatic cleanup never deletes pets, named animals, or living animals, and it removes animals without recorded children before parents. Each cleanup writes a full backup of the history, about 2.3 MB at 5,000 records, and the backups stay until you delete them. `/familytree prune farm` runs the same cleanup by hand for all deceased unnamed animals other than pets.

## Export a family tree

Click **Export family tree**, then **Open folder** to find the saved PNG. Export saves a PNG under `screenshots/familytree/` in your Minecraft folder. The image includes the whole visible tree at a fixed, readable scale, regardless of your current zoom or pan. Hidden branches stay hidden. Very large trees need to be narrowed down before export.

Exports include pet names, generations, variants, horse stats, portraits, and living or deceased status. They omit player names and coordinates. Nothing uploads automatically. Cat, wolf, and horse portraits in exports come from their textures. Other animals' portraits are copied from the in-game model, so the screen briefly shows them while the image is prepared.

![A PNG exported from the example family](docs/family-tree-export.png)

## Existing families and server permissions

Rename a stick to `familytree` in an anvil. Right-click the first parent, second parent, and child, then confirm in chat. Clicking an animal other than a pet that isn't in the history yet adds it for you first. Sneak and right-click a pet to clear its parents after confirmation. Manual links require distinct pets and reject ancestry loops. Horse and donkey parents can link to a mule.

Owners and server operators can locate or edit their pets. Linking two parents requires permission for all three animals. By default, only operators can browse everyone's pets. A server can change `viewPolicy` in `config/familytree-server.properties` to `EVERYONE_ALL` or `OWN_ONLY`, then restart. Public viewing does not grant editing rights or reveal other players' pet locations. The same file holds the chest menu settings described under [Players without the client mod](#players-without-the-client-mod).

[Command reference](docs/commands.md) includes locating, pairing, age corrections, and operator pruning.

## Languages

Family Tree includes English, Simplified Chinese, Traditional Chinese, Japanese, Korean, Russian, Ukrainian, German, French, Italian, Spanish (Spain and Mexico), Brazilian Portuguese, Polish, and Turkish. Everything except English was machine-translated. If a phrase reads wrong in your language, please [open an issue](https://github.com/kharitonov-egor/family_tree/issues) or send a pull request that edits `src/main/resources/assets/familytree/lang/<language>.json`. A test checks that every language file has the same keys and format arguments as `en_us.json`.

## Upgrading to 1.2.0

Existing Family Tree history and custom names remain. Saved records gain one optional `horse_stats` field, and 1.1.0 saves load unchanged. Update the server and clients together because the network channels changed. If you go back to 1.1.0, it keeps every record but drops horse stats on its next save. Minecraft world version restrictions still apply, so each build is for worlds of its own Minecraft version, not for downgrading a newer world.

[Latest release notes](docs/releases/1.2.0.md) | [1.1.0 release notes](docs/releases/1.1.0.md) | [1.0.0 release notes](docs/releases/1.0.0.md)

## Build and test

Install JDK 25, JDK 21, and JDK 17, then use the included Gradle wrapper. The 1.20.1 jars target Java 17 bytecode, and the 1.21.x jars Java 21.

```sh
./gradlew buildAll
./gradlew :1.20.1:build
./gradlew :26.2:build
./gradlew -p neoforge build
./gradlew -p forge build -Pminecraft=1.20.1
```

Fabric jars are in `versions/<minecraft>/build/libs/`, NeoForge jars in `neoforge/build/<minecraft>/libs/`, and Forge jars in `forge/build/<minecraft>/libs/`. `buildAll` runs the shared tests for all twenty targets. Stonecutter generates Fabric sources for each version. NeoForge and Forge compile the generated sources for their selected target with their own event and networking entrypoints. Do not edit generated files under `versions/`.

See [runtime validation](docs/testing.md) for the separate server smoke tests and client demo. [CLAUDE.md](CLAUDE.md) covers the source layout and save compatibility rules.

## License

[MIT](LICENSE)

## Releases and download progress

Version 1.2.0 adds horse breeding stats and builds for Minecraft 1.20.1 and 1.21.11. Update clients and servers together. It preserves existing history, keeps unreadable records in the save, and disables writes to an unsupported future save format. Operator pruning writes a backup before deleting records.

[Release notes](docs/releases/1.2.0.md), [runtime checks](docs/testing.md), and [public download counts](docs/metrics/README.md) describe the release and progress toward 10,000 Modrinth downloads. Run `python scripts/download_counts.py` to update the public counters. The daily GitHub workflow does the same. Repeat downloads and upgrades count toward platform totals.

Build all targets with JDK 25, JDK 21, and JDK 17 installed using `./gradlew buildAll --no-parallel`. Commit the tested source, then run `python scripts/release_manifest.py` to verify jar metadata, Java targets, license, icon, and hashes. Publish the copies under `build/releases/<version>/`.
