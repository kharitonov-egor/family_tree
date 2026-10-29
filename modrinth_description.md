# Keep your first wolf in the family tree after it's gone

Family Tree records your Minecraft pets, their parents, and their descendants. Keep deceased pets in the family, find a missing pet's last known location, and export a tree as a PNG.

Install the matching file in singleplayer and press **H**. On a server, install Family Tree on the server for tracking and on your client for the screens. Use the same mod version on both sides. Players without the client mod can still use `/familytree`; see below. Fabric builds also need Fabric API and Fabric Loader 0.19.3 or newer. They run on Quilt Loader 0.30.1 or newer too, with Fabric API.

![A pet family exported as a PNG](https://cdn.modrinth.com/data/CVQKDAe7/images/4f59a55c92124812e4c90f47cb8d795a8dd218e4.png)

Moss stays in Clover's tree after his death. This export comes from the automated vanilla-wolf scene below.

![Vanilla wolves breed, then their family tree exports as a PNG](https://cdn.modrinth.com/data/CVQKDAe7/images/1abf61e1f198af7518de096a7d8a5919d5e53391.webp)

A 20-second automated scene in Minecraft 26.2. Vanilla feeding and AI breeding produce the actual puppy; Family Tree records the parents and keeps the deceased parent.

## Works on servers without the client mod

Players who join with an unmodded client type `/familytree` and get a chest menu. It lists their pets with spawn egg icons, names, age, and status, with filters for status and species and a name search. Click a pet to see its parents, grandparents, and children, then click a relative to move through the family. Owners can locate the pet in chat, rename it in an anvil, add parents with a picker, and read the family as a written book.

The menus use the same permissions and cooldowns as the screens and never show coordinates. Items in them can't be taken, moved, or copied. Text follows each player's Minecraft language. Server owners can turn the menu off with `vanillaMenu=false` or add command names such as `commandAliases=pets` in `config/familytree-server.properties`. Players with the client mod keep the full screens.

## Breed better horses

Every horse card shows speed, jump height, and health beside its parents. A green triangle marks a stat higher than both parents, and a red triangle marks one lower than both. Sort your horses by speed, jump, or health, and export the family with its stats.

![A horse family exported with speed, jump, and health](https://cdn.modrinth.com/data/CVQKDAe7/images/e3b88ce556bec5416e211ad4238e962badac4f97.png)

An automated scene in Minecraft 26.3. Two tamed horses ate golden carrots, and vanilla AI bred them. Ember's jump is lower than both parents'.

Stats cover horses, donkeys, mules, llamas, camels, skeleton horses, and zombie horses. Speed is in blocks per second, jump in blocks, and health in hearts. Llamas also show chest slots.

## Start with the pets already in your world

Visit your pets or choose **Find existing pets** to check loaded tamed animals. Older parents are unknown because Minecraft does not retain them. Open a pet, select it, and choose **Add parents** to record a family you remember. Breed pets while the mod is installed to record new children's parents automatically.

The tracker covers tamed wolves, cats, parrots, horses, donkeys, mules, llamas, camels, skeleton horses, and zombie horses. Camels and skeleton trap horses join once a player rides or breeds them. Parrots have individual records and cannot breed in vanilla Minecraft. Horse and donkey parents can link to a mule. On 1.21.11, tamed nautiluses and zombie nautiluses are recorded too.

## Browse, find, and share

Search by pet name or species, filter living or deceased pets, and open one pet's tree or a whole species. Cat, wolf, and horse portraits match their coats. Wolves in 1.20.1 have one coat. Tree names leave the animal's name tag unchanged. Use **Refresh** to load births and edits that happened while the screen was open.

**Locate pet** reports coordinates and dimension in chat. Unloaded pets show their last recorded location and day. The mod does not load chunks or teleport pets.

**Export family tree** saves a PNG under `screenshots/familytree/`. Choose **Open folder** to find it. Export includes the displayed family beyond the screen edges. Hidden branches stay hidden. A focused tree shows up to six generations in either direction; the species view includes its recorded families. Exports omit player names and coordinates, and nothing uploads automatically.

## Choose the matching file

| Minecraft | Fabric | NeoForge | Forge | Java |
| --- | --- | --- | --- | --- |
| 1.20.1 | Fabric API for 1.20.1 | No build | 47.1.0 or newer | 17 or newer |
| 1.21.1 | Fabric API for 1.21.1 | 21.1.252 or newer within 21.1 | No build | 21 or newer |
| 1.21.11 | Fabric API for 1.21.11 | 21.11.48 or newer within 21.11 | No build | 21 or newer |
| 26.1.1 | Fabric API for 26.1.1 | No build | No build | 25 or newer |
| 26.1.2 | Fabric API for 26.1.2 | No build | No build | 25 or newer |
| 26.2 | Fabric API for 26.2 | 26.2.0.88 or newer within 26.2 | No build | 25 or newer |
| 26.3 | Fabric API for 26.3 | 26.3.0.37-beta or newer within 26.3, beta build | No build | 25 or newer |

NeoForge and Forge builds do not need Fabric API. Players without Family Tree on their client use the chest menus described above.

Family Tree is translated into Simplified and Traditional Chinese, Japanese, Korean, Russian, Ukrainian, German, French, Italian, Spanish, Brazilian Portuguese, Polish, and Turkish. The translations are machine-made. Corrections are welcome as [GitHub issues](https://github.com/kharitonov-egor/family_tree/issues) or pull requests.

![Choose and confirm known parents](https://cdn.modrinth.com/data/CVQKDAe7/images/ffa335f35d20cab921cdea37992bf823d38bd70f.webp)

## Permissions and existing history

Owners and operators can edit and locate their pets. The server can allow everyone to browse all families through `viewPolicy=EVERYONE_ALL` in `config/familytree-server.properties`. Public browsing does not reveal other players' pet coordinates or grant editing access.

The linking stick remains available. Rename a stick to `familytree`, right-click two parents and a child, then confirm in chat. Links require distinct pets and cannot create ancestry loops.

Commands correct ages and birth days, inspect records, and manage history. Name-based edits reject duplicate names and offer UUID choices. Operator pruning writes a backup first. See the [command guide](https://github.com/kharitonov-egor/family_tree/blob/main/docs/commands.md).

Version 1.2.0 preserves existing history and adds one optional field for horse stats. Update clients and servers together because the network channels changed. Back up your world before changing Minecraft versions. An older Minecraft build does not make a newer world safe to downgrade.

Each new 1.2.0 build ran in a real game instance before release: Fabric and Forge on 1.20.1, Fabric and NeoForge on 1.21.11, and the horse scenes on Fabric 26.3 and NeoForge 1.21.1. The [release notes](https://github.com/kharitonov-egor/family_tree/blob/v1.2.0/docs/releases/1.2.0.md) list what was tested.

[Release notes](https://github.com/kharitonov-egor/family_tree/blob/v1.2.0/docs/releases/1.2.0.md). [Report a bug](https://github.com/kharitonov-egor/family_tree/issues). [Source](https://github.com/kharitonov-egor/family_tree). MIT licensed.

## Making a video or adding it to a pack?

Try a short demo: tame two wolves, breed them, press **H**, open the puppy's tree, and export it. [Download the landscape clip](https://github.com/kharitonov-egor/family_tree/releases/download/v1.1.0/family-tree-gameplay.mp4) or [vertical clip](https://github.com/kharitonov-egor/family_tree/releases/download/v1.1.0/family-tree-gameplay-vertical.mp4). Both show an automated scene with actual vanilla breeding.

Compatibility with modded animal species or changed animal models has not been tested. For a pack trial, check breeding, saved history after a restart, ownership permissions, and client export in a disposable world.
