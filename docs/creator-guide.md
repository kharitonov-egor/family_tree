# Family Tree creator guide

Family Tree is a free Minecraft Java mod that shows who bred whom. It records the parents of every pet and animal a player breeds, so a family can be followed across generations, each foal's speed, jump, and health compared with its parents', and inherited colors traced back. It runs in singleplayer and on servers, and players without the mod can browse families with `/familytree` on a server that has it.

[Download on Modrinth](https://modrinth.com/mod/familytree/versions) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/family-tree/files). [Source and license](https://github.com/kharitonov-egor/family_tree). [Report a bug](https://github.com/kharitonov-egor/family_tree/issues).

## The short pitch

Minecraft forgets who your animals' parents were. Family Tree keeps them. Breed two horses and the foal joins a tree with both parents, with a green ▲ on any stat that beat both of them. Keep breeding and the tree grows to four, five, six generations you can pan and zoom through. It works on servers too, including for friends who join without the mod.

A possible opening line: "I bred four generations of horses, and this mod drew the whole family tree."

## Try it in a minute

1. Install the file for your Minecraft version and loader. Fabric also needs the matching Fabric API and Fabric Loader 0.19.3 or newer.
2. In a test world, tame two horses and feed them golden carrots.
3. Press **H**, find the foal, and open its tree. Both parents are there, with each stat beside theirs.
4. Choose **Export family tree**, then **Open folder** to find the PNG.

In singleplayer, install once. On a multiplayer server, install the same Family Tree version on the server and on any client that wants the full screens. Players without it type `/familytree`.

For an existing survival world, visit your pets or choose **Find existing pets**. Minecraft does not keep old parentage. **Add parents** records a family you know; it does not recover forgotten ancestry automatically.

## Video ideas

**Horse breeding.** Tame two horses, feed them golden carrots, and open the foal's tree. A green ▲ marks a stat higher than both parents, and a red ▼ one lower than both. Sort the browser by speed, jump, or health to pick the next pair, and show the tree after a few generations. Stats use the Minecraft Wiki's units: blocks per second, blocks of jump height, and hearts.

**A server with friends.** Breed your wolf with a friend's wolf and open the puppy's tree: both owners' animals sit in one family. Have a friend join without the mod and type `/familytree` to browse the same family in chest menus and read it as a book. Set `viewPolicy=EVERYONE_ALL` in `config/familytree-server.properties` so everyone can browse every family; by default players see only their own animals.

**Colors and rare variants.** Breed a red sheep with a white one: the lamb's card says "Pink wool", and the panel explains that the parents' colors mixed. Breed pandas that carry the brown gene hidden until a brown cub appears; Family Tree marks it with a gold ★ and explains the hidden gene. Bred axolotls turn blue about 1 time in 1,200, and the blue one gets the same ★.

Animals other than pets join only when a player breeds them, uses a name tag on them, or clicks them with the linking stick. Name the parents before breeding if you want their names on the cards. Unnamed animals show a species name with a short code, such as "Sheep 3F2A1C".

## Downloadable media

| Asset | What it shows |
| --- | --- |
| [Showcase clip, landscape](launch/family-tree-showcase.mp4) | 26 seconds: horse breeding, the tree, foal stats, a brown panda, the server chest menu, and an export |
| [Showcase clip, vertical](launch/family-tree-showcase-vertical.mp4) | The same clip in 1080x1920 |
| [Tree tour](media/tree-tour.webp) | Panning across four generations of horses |
| [Four generations](media/tree-four-generations.webp) | The same family in one screen |
| [Foal stats](media/foal-stats.webp) | A foal's stats beside both parents, with ▲ and ▼ |
| [Horse breeding](media/horse-breeding.webp) | Two horses breed and the foal joins the tree |
| [Two players on a server](media/server-two-players.webp) | A modded and an unmodded player viewing one family |
| [Chest menu](media/server-chest-menu.webp) | An unmodded client clicking through a family |
| [Family book](media/server-family-book.webp) | The family as a written book |
| [View-only menu](media/server-view-only.webp) | Another player's animal, with no edit buttons |
| [Sheep colors](media/sheep-variant.webp) | A pink lamb from red and white parents |
| [Brown panda](media/panda-variant.webp) | A brown cub from two carriers |
| [Jade tooltip](media/jade-tooltip.webp) | Family details and horse stats in Jade |
| [Exported family](media/horse-family-export.png) | Four generations exported as a PNG |

The horse, sheep, and panda media come from automated scenes in Minecraft 26.3. The script tamed and named the animals, led each pair together, fed them, and grew each foal up before the next generation; vanilla AI did the breeding. The pandas come from a separate run of the same scene. The blue axolotl in the browser was spawned blue and was not bred. The server media come from a Fabric 1.21.1 dedicated server running Family Tree 1.3.0 with two real clients, one with the mod and one without it. Its console summoned and named the three wild wolves and grew one puppy up; the players tamed and fed the wolves, and vanilla AI bred the puppies. Keep those distinctions in captions.

## Compatibility and limits

Version 1.4.0 supports Fabric on Minecraft 1.20.1, 1.21.1, 1.21.4 to 1.21.11, 26.1.1, 26.1.2, 26.2, and 26.3. NeoForge builds cover 1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.2, and 26.3, and Forge covers 1.20.1. NeoForge 26.3 is beta. The Paper and Spigot plugin runs on Paper 1.20.1 to 26.3 and Spigot 1.20.1, 1.21.11, and 26.3. See the [installation table](../README.md#install) for Java and loader requirements.

Tamed wolves, cats, parrots, horses, donkeys, mules, llamas, camels, skeleton horses, and zombie horses join on their own. Camels and skeleton trap horses join once a player rides or breeds them. Every other breedable animal, vanilla or modded, joins when a player breeds, names, or adds it. Frogs, sniffers, and turtles hatch from spawn or eggs, so their babies need **Add parents**. Parrots cannot breed in vanilla Minecraft. Horse and donkey parents can link to a mule. Naturalist 2.0.7 on NeoForge 1.21.1 was tested with bred deer; other animal mods have not been tested.

Locate reports a pet's last recorded position if it is unloaded. It does not load chunks or teleport animals. Exports omit explicit player names and coordinates, but pet names appear in the image. Nothing uploads automatically.

## For modpack maintainers and server owners

Family Tree is MIT licensed. Use the platform's matching release and dependency metadata when adding it to a pack. Test in a disposable world first: discover existing pets, breed a child, restart the server, check that the family remains, and export a tree from the client. Join once without the mod and check `/familytree`. Packs with large animal farms should review `farmRecordLimit` and `autoPruneFarmRecords` in `config/familytree-server.properties`; the [README](../README.md#server-settings-for-animals) lists every setting. Check ownership rules with two players before enabling public browsing.

Please report the Minecraft version, loader, Family Tree version, other animal mods, and steps to reproduce any problem. A download or mention does not establish compatibility with a whole pack.
