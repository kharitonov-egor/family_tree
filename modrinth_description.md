# See who bred whom

![Panning and zooming across four generations of horses in the Family Tree screen](media:tree-tour)

*Four generations of horses in Minecraft 26.3. Vanilla AI bred every foal in an automated scene. The script tamed and named the eight founders and grew each foal up before the next generation.*

Family Tree records the parents of every animal you breed. Each foal, puppy, lamb, and cub joins a tree with both parents, so you can follow a line back through the generations, check whether a foal beat its parents' speed, jump, and health, and see where a pink lamb or a brown panda got its color. Press **H** to open it.

It runs in singleplayer and on servers. Players who join a server without the mod can still browse their families with `/familytree`.

Install the file for your Minecraft version and loader from the [table below](#choose-the-matching-file). Fabric builds need Fabric API and Fabric Loader 0.19.3 or newer. They also run on Quilt Loader 0.30.1 or newer with Fabric API.

## Works on servers

![Alex's tree screen above Kai's chest menu, both showing Pepper's family on one server](media:server-two-players)

*A Fabric 1.21.1 dedicated server running Family Tree 1.3.0, with two players online at once. Alex has the mod. Kai joined with unmodded Minecraft. Both views were taken after the server restarted.*

Install Family Tree on the server. It keeps every player's animals in the world save, and each player chooses how to look at them:

- Players with Family Tree on their client press **H** for the full screens: the tree, search, horse stats, and PNG export.
- Players without it type `/familytree` and get chest menus. They list the player's animals with filters and a name search, show each animal's parents, grandparents, and children, and turn a family into a written book. Menu text follows each player's game language.

![Kai clicks Pepper in the chest menu, sees its parents and grandparents, then opens Biscuit](media:server-chest-menu)

*Kai's unmodded client on the same server, with real clicks. The server console summoned and named the three wild wolves and grew Biscuit up. The players tamed and fed the wolves themselves, and vanilla AI bred Biscuit and Pepper.*

Families can cross owners. In the images above, Alex's wolf Biscuit and Kai's wolf Juno had a puppy, Pepper, and both players see the whole family. Only an animal's owner or an operator can rename it, change its parents, or locate it. Everyone else gets a view-only page without coordinates. Items in the menus can't be taken, moved, or copied.

![Kai's view of Alex's wolf, with no Locate, Rename, or Add parents](media:server-view-only)

History lives in the world save, so it survives restarts and Family Tree updates. Pruning writes a JSON backup before it deletes anything, and Family Tree never deletes pets or named animals on its own.

Server settings live in `config/familytree-server.properties`, with a comment above each one that gives its default and allowed values. `/familytree reload` applies changes without a restart and names any value it can't use.

- `viewPolicy` sets who can browse whose animals. With the default, `OP_ONLY_ALL`, players see their own animals and operators see everyone's. `EVERYONE_ALL` lets every player browse every family, as on the server above. `OWN_ONLY` limits everyone, operators included, to their own animals.
- `vanillaMenu=false` turns the chest menus off. `commandAliases=pets,ft` adds `/pets` and `/ft`.
- `trackFarmAnimals`, `trackWildlife`, `trackModdedAnimals`, `trackedSpecies`, and `ignoredSpecies` choose which animals join.
- `farmRecordLimit`, 5,000 by default, caps unnamed farm animals. `autoPruneFarmRecords` is off by default. When on, it backs up the history and removes the oldest deceased unnamed farm animals.
- Other settings cover cooldowns, which owner actions need an operator, the linking stick, and one chat language for everyone.

LuckPerms and other permission mods can grant or deny each action through `familytree.*` nodes, such as `familytree.rename`, `familytree.others.view`, and `familytree.prune`. Without a permission mod, Family Tree uses operator levels as before. Datapacks can choose species with the `familytree:tracked` and `familytree:ignored` tags.

Family Tree ran on dedicated Fabric, NeoForge, and Forge servers with LuckPerms, spark, Lithium, FerriteCore, ModernFix, C2ME, and other common server mods. The [server guide](https://github.com/kharitonov-egor/family_tree/blob/main/docs/servers.md) lists every node, setting, and tested version.

Use the same Family Tree version on the server and on modded clients.

### Paper, Purpur, and Spigot

![The Family Tree plugin's chest menu on a Paper 26.3 server, seen from an unmodded client](media:plugin-chest-menu)

The plugin file runs on Paper from 1.20.1 to 26.3, Purpur 26.3, and Spigot 1.20.1, 1.21.11, and 26.3, with the same commands, chest menus, permission nodes, settings, and languages as the mod. It keeps history in the mod's `familytree.dat`, so a world keeps its families when it moves between the mod and the plugin. Players who have the client mod get its full screens on a plugin server. The [plugin guide](https://github.com/kharitonov-egor/family_tree/blob/main/docs/plugin.md) explains how it differs from the mod.

## Breed better horses

![Clover and Pippin breed after golden carrots, and the foal appears](media:horse-breeding)

![Ember's stats beside both parents: a green triangle for jump and a red one for health](media:foal-stats)

*From the same automated 26.3 scene. The script led each pair together and fed them golden carrots; vanilla AI bred them. Ember jumps higher than both parents and has less health than either.*

Every horse card shows speed, jump height, and health. A green ▲ marks a stat higher than both parents, and a red ▼ marks one lower than both. Select a foal to compare each value with its parents', and sort the browser by speed, jump, or health to pick the next pair. Speed is in blocks per second, jump in blocks, and health in hearts.

Stats cover horses, donkeys, mules, llamas, camels, skeleton horses, and zombie horses. Llamas also show chest slots. Horse and donkey parents can link to a mule.

## Plan the next foal

![The breeding planner for Quill and Sorrel: half siblings, the foal's inbreeding coefficient, and each stat's range and chance to beat both parents](media:planner)

*Quill and Sorrel were bred by vanilla AI in an automated 26.3 scene.*

Pick two animals in the **Breeding planner** before you feed them. For horses, donkeys, mules, and llamas it shows each foal stat's lowest, most likely, and highest value and the chance the foal beats both parents. Family Tree computes those chances exactly from Minecraft's own breeding code. The planner also lists the odds of each wool color, axolotl and mooshroom color, panda gene, horse coat, and other variants, names how the two animals are related, and gives the baby's inbreeding coefficient.

**Best pairs** ranks every pair in a herd by expected speed, jump, health, chest slots, or an overall score, and can skip close relatives. Shift-click a second card in any tree to see how two animals are related, such as half siblings or first cousins. Players without the mod get the same plan from `/familytree breed` or as a book from the chest menu.

## See relatives in the world

![Ember glows white, with its parents in orange, siblings in green, and foals in blue, each labeled](media:relatives)

*Golden carrots and vanilla AI breeding produced the three generations in an automated 26.3 scene.*

Hold the grave accent key (`` ` ``, left of 1) and look at an animal. Its parents, children, siblings, half siblings, grandparents, and grandchildren that are loaded nearby glow in their relation's color, with a label such as "Parent · Comet", and particle lines lead from the parents to it and on to its children. Only you see them. Another player's pet you can't open in Family Tree stays dark. Players without the mod get **Show relatives nearby** on a pet's page in the chest menu.

## Every animal you breed, and where its colors came from

![A pink lamb's tree: a white and a red parent, and a note that their wool colors mixed](media:sheep-variant)

![Cocoa, a brown panda whose parents each carried the brown gene hidden](media:panda-variant)

*Automated 26.3 scenes where the player fed the animals and vanilla AI bred them. The pandas come from a separate run of the same scene, and the cub got its name from a name tag.*

Breed cows, sheep, pigs, chickens, rabbits, goats, foxes, axolotls, frogs, bees, turtles, pandas, ocelots, hoglins, striders, sniffers, armadillos, or breedable animals from other mods, and the baby joins the tree with both parents. A name tag or the linking stick adds any other animal. Wild animals stay out until a player breeds, names, or adds them.

Cards and exports show each animal's variant: wool color, axolotl, mooshroom, frog, and llama colors, panda genes, rabbit and fox types, and cow, pig, and chicken variants on 1.21.5 and newer. A gold ★ marks a blue axolotl, a brown mooshroom, or a brown panda. Select a baby to see how it got its variant, such as two wool colors mixing or a panda showing a gene both parents carried hidden.

Pets join on their own: tamed wolves, cats, parrots, horses, donkeys, mules, llamas, camels, skeleton horses, and zombie horses, plus tamed nautiluses and zombie nautiluses on 1.21.11 and newer. Camels and skeleton trap horses join once a player rides or breeds them. Parrots have individual records and cannot breed in vanilla Minecraft.

## Find any animal

![Switching between the Pets, Farm, and Wildlife tabs, then searching for blue](media:browser-tabs)

*The search finds a blue sheep and a blue axolotl. The axolotl was spawned blue for the scene and named with a name tag; it was not bred.*

Tabs separate pets, farm animals, wildlife, and modded animals once you have more than one kind. Search by name, species, or variant, filter living or deceased animals, and open one animal's tree or a whole species. Portraits show each animal's in-game model, so they match coats and wool colors. Tree names leave the animal's name tag unchanged.

**Locate pet** reports coordinates and dimension in chat. Unloaded pets show their last recorded location and day. The mod does not load chunks or teleport pets. Animals that die keep their place in the tree, with the day and cause.

## Works with Jade, WTHIT, JourneyMap, Xaero's Minimap, and Mod Menu

![A Jade tooltip on Nova: Family Tree name, parents, generation, and stats beside the parents' values](media:jade-tooltip)

All optional. Family Tree doesn't bundle them and runs without them.

- **Jade and WTHIT.** Look at a tracked animal to see its Family Tree name, parents, and generation. Horses also show speed, jump, and health beside their parents' values. Tested with Jade on Fabric 26.3 and 1.21.8, NeoForge 1.21.1 and 1.21.4, and Forge 1.20.1, and with WTHIT on Fabric 1.21.11 and NeoForge 1.21.1.
- **JourneyMap and Xaero's Minimap.** **Waypoint** in a pet's tree adds a waypoint at the pet in JourneyMap. With Xaero's Minimap it shares the position in chat, and Xaero's **Add** link opens its waypoint form with the pet's name and position filled in. Tested with both on Fabric 26.3 and 1.21.8, NeoForge 1.21.1 and 1.21.4, and Forge 1.20.1.
- **Mod Menu**, and the Config button in NeoForge's and Forge's mod list, open Family Tree's settings.

![A JourneyMap waypoint beam over the paddock, added from Nova's tree](media:journeymap-waypoint)

Tooltips and waypoints follow Family Tree's permissions. They show only animals the player could open in Family Tree, and they never reveal other players' pet coordinates. On a server, install Jade or WTHIT there too, so it sends family details for every animal a player looks at. WTHIT has no Minecraft 26.3 build yet.

## Export the family as a PNG

![Nova's four generations exported as a PNG with each horse's speed, jump, and health](media:horse-family-export)

**Export family tree** saves a PNG under `screenshots/familytree/`, and **Open folder** shows it. The image includes the displayed family beyond the screen edges, at a fixed readable scale. Hidden branches stay hidden. A focused tree opens with six generations in either direction, and a tab on the edge cards shows six more. Exports omit player names and coordinates, and nothing uploads automatically.

## Start with the animals you already have

Visit your pets or choose **Find existing pets** to check loaded tamed animals. Minecraft does not keep older parentage, so their parents start unknown. Open a pet, select it, and choose **Add parents** to record parents you know. Breed while the mod is installed, and new babies get their parents automatically.

The linking stick also works. Rename a stick to `familytree`, right-click two parents and a child, then confirm in chat. Links require distinct animals and cannot create ancestry loops.

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

Paper, Purpur, and Spigot servers use the plugin file, `familytree-1.4.0+plugin.jar`, in `plugins/` instead. It runs on the Java version the server already needs.

Family Tree is translated into Simplified and Traditional Chinese, Japanese, Korean, Russian, Ukrainian, German, French, Italian, Spanish, Brazilian Portuguese, Polish, and Turkish. The translations are machine-made. Corrections are welcome as [GitHub issues](https://github.com/kharitonov-egor/family_tree/issues) or pull requests.

## Commands and history

Commands correct ages and birth days, inspect records, and manage history. Name-based edits reject duplicate names and offer UUID choices. Operator pruning writes a backup first. See the [command guide](https://github.com/kharitonov-egor/family_tree/blob/main/docs/commands.md).

Version 1.4.0 preserves existing history and adds no saved fields. A 1.4.0 client still joins a 1.3.0 server, but update clients and servers together to get the relatives key. `/familytree prune` now asks for `confirm` before it removes anything. If Family Tree can't read its history file, it keeps a copy and stops saving instead of writing an empty history over it. Back up your world before changing Minecraft versions. An older Minecraft build does not make a newer world safe to downgrade.

Minecraft 26.4 is still in snapshots. Family Tree builds and tests a Fabric file for 26.4-snapshot-6, and a 26.4 file follows the game's release. The [release notes](https://github.com/kharitonov-egor/family_tree/blob/v1.4.0/docs/releases/1.4.0.md) describe the changes.

## Tell the developer what you think

**Send feedback** in the Family Tree screen opens a form for a message, with an optional contact field if you'd like a reply. The form lists everything that will be sent: your message, the contact if given, the Family Tree, Minecraft, and loader versions, and the game language. Nothing is sent until you press **Send**, and Family Tree never sends anything on its own or in the background. The feedback server uses your IP address only to limit spam and leaves it out of the email.

[Release notes](https://github.com/kharitonov-egor/family_tree/blob/v1.4.0/docs/releases/1.4.0.md). [Report a bug](https://github.com/kharitonov-egor/family_tree/issues). [Source](https://github.com/kharitonov-egor/family_tree). MIT licensed.

## Making a video or adding it to a pack?

The [creator guide](https://github.com/kharitonov-egor/family_tree/blob/main/docs/creator-guide.md) has demo ideas and a 25-second showcase clip in [landscape](https://github.com/kharitonov-egor/family_tree/raw/main/docs/launch/family-tree-showcase.mp4) and [vertical](https://github.com/kharitonov-egor/family_tree/raw/main/docs/launch/family-tree-showcase-vertical.mp4) formats. The clips show the automated scenes and the server described above.

Naturalist 2.0.7 on NeoForge 1.21.1 was tested: bred deer joined the tree with both parents, and their portraits rendered. Other animal mods have not been tested. For a pack trial, check breeding, saved history after a restart, ownership permissions, and client export in a disposable world.
