# Keep your first wolf in the family tree after it's gone

Family Tree records your Minecraft pets and the animals you breed, with their parents and descendants. Keep deceased pets in the family, find a missing pet's last known location, and export a tree as a PNG.

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

## Every animal you breed

Breed cows, sheep, pigs, chickens, rabbits, goats, foxes, axolotls, frogs, bees, turtles, pandas, ocelots, hoglins, striders, sniffers, armadillos, or breedable animals from other mods, and the baby joins the tree with both parents. A name tag or the linking stick adds any other animal. Wild animals stay out until a player breeds, names, or adds them.

Cards and exports show each animal's variant: wool color, axolotl, mooshroom, frog, and llama colors, panda genes, rabbit and fox types, and cow, pig, and chicken variants on 1.21.5 and newer. A gold ★ marks a blue axolotl, a brown mooshroom, or a brown panda. Select a baby to see how it got its variant, such as two wool colors mixing or a panda showing a gene its parents carried hidden.

![A pink lamb and its red and white parents exported as a PNG](https://raw.githubusercontent.com/kharitonov-egor/family_tree/main/docs/sheep-family-export.png)

An automated scene in Minecraft 26.3. Two sheep named with name tags ate wheat, and vanilla AI bred a pink lamb.

Servers choose which animals join in `config/familytree-server.properties` and can cap unnamed farm animals at a limit, 5,000 by default. Pets and named animals are always recorded. Cleanup of old unnamed farm animals is off unless the server turns it on, and it saves a backup first.

## Start with the pets already in your world

Visit your pets or choose **Find existing pets** to check loaded tamed animals. Older parents are unknown because Minecraft does not retain them. Open a pet, select it, and choose **Add parents** to record a family you remember. Breed pets while the mod is installed to record new children's parents automatically.

Pets join on their own: tamed wolves, cats, parrots, horses, donkeys, mules, llamas, camels, skeleton horses, and zombie horses, plus tamed nautiluses and zombie nautiluses on 1.21.11 and newer. Camels and skeleton trap horses join once a player rides or breeds them. Parrots have individual records and cannot breed in vanilla Minecraft. Horse and donkey parents can link to a mule.

## Browse, find, and share

Search by name, species, or variant, filter living or deceased animals, and open one animal's tree or a whole species. Tabs separate pets, farm animals, wildlife, and modded animals once you have more than one kind. Portraits show each animal's in-game model, so they match coats and wool colors. Wolves in 1.20.1 have one coat. Tree names leave the animal's name tag unchanged. Use **Refresh** to load births and edits that happened while the screen was open.

**Locate pet** reports coordinates and dimension in chat. Unloaded pets show their last recorded location and day. The mod does not load chunks or teleport pets.

**Export family tree** saves a PNG under `screenshots/familytree/`. Choose **Open folder** to find it. Export includes the displayed family beyond the screen edges. Hidden branches stay hidden. A focused tree shows up to six generations in either direction; the species view includes its recorded families. Exports omit player names and coordinates, and nothing uploads automatically.

## Works with Jade, WTHIT, JourneyMap, Xaero's Minimap, and Mod Menu

All optional. Family Tree doesn't bundle them and runs without them.

- **Jade and WTHIT.** Look at a tracked animal to see its Family Tree name, parents, and generation. Horses also show speed, jump, and health beside their parents' values. Tested with Jade on Fabric 26.3 and 1.21.8, NeoForge 1.21.1 and 1.21.4, and Forge 1.20.1, and with WTHIT on Fabric 1.21.11 and NeoForge 1.21.1.
- **JourneyMap and Xaero's Minimap.** **Waypoint** in a pet's tree adds a waypoint at the pet in JourneyMap. With Xaero's Minimap it shares the position in chat, and Xaero's **Add** link opens its waypoint form with the pet's name and position filled in. Tested with both on Fabric 26.3 and 1.21.8, NeoForge 1.21.1 and 1.21.4, and Forge 1.20.1.
- **Mod Menu**, and the Config button in NeoForge's and Forge's mod list, open Family Tree's settings.

Tooltips and waypoints follow Family Tree's permissions. They show only pets the player could open in Family Tree, and they never reveal other players' pet coordinates.

**For pack authors:** install Jade or WTHIT on the server too, so it sends family details for every animal a player looks at. The map and Mod Menu features are client-side. Settings are in `config/familytree-client.properties` on clients and `config/familytree-server.properties` on the server. WTHIT has no Minecraft 26.3 build yet.

## Tell the developer what you think

**Send feedback** in the Family Tree screen opens a form for a message, with an optional contact field if you'd like a reply. The form lists everything that will be sent: your message, the contact if given, the Family Tree, Minecraft, and loader versions, and the game language. Nothing is sent until you press **Send**, and Family Tree never sends anything on its own or in the background. The feedback server uses your IP address only to limit spam and leaves it out of the email.

## Choose the matching file

| Minecraft | Fabric | NeoForge | Forge | Java |
| --- | --- | --- | --- | --- |
| 1.20.1 | Fabric API for 1.20.1 | No build | 47.1.0 or newer | 17 or newer |
| 1.21.1 | Fabric API for 1.21.1 | 21.1.252 or newer within 21.1 | No build | 21 or newer |
| 1.21.4 | Fabric API for 1.21.4 | 21.4.161 or newer within 21.4 | No build | 21 or newer |
| 1.21.5 | Fabric API for 1.21.5 | 21.5.101 or newer within 21.5 | No build | 21 or newer |
| 1.21.6, 1.21.7 | The 1.21.8 file, with Fabric API for your version | No build | No build | 21 or newer |
| 1.21.8 | Fabric API for 1.21.8 | 21.8.57 or newer within 21.8 | No build | 21 or newer |
| 1.21.9 | The 1.21.10 file, with Fabric API for 1.21.9 | No build | No build | 21 or newer |
| 1.21.10 | Fabric API for 1.21.10 | 21.10.64 or newer within 21.10 | No build | 21 or newer |
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

Naturalist 2.0.7 on NeoForge 1.21.1 was tested: bred deer joined the tree with both parents, and their portraits rendered. Other animal mods have not been tested. For a pack trial, check breeding, saved history after a restart, ownership permissions, and client export in a disposable world.
