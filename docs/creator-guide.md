# Family Tree creator guide

Family Tree is a free Minecraft Java mod that records the parents and descendants of pets and of the animals players breed. Deceased animals stay in the family, and players can export the tree as a PNG.

[Download on Modrinth](https://modrinth.com/mod/familytree/versions) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/family-tree/files). [Source and license](https://github.com/kharitonov-egor/family_tree). [Report a bug](https://github.com/kharitonov-egor/family_tree/issues).

## Try it in a minute

1. Install the file for your Minecraft version and loader. Fabric also needs the matching Fabric API and Fabric Loader 0.19.3 or newer.
2. In a test world, tame two wolves and breed them using vanilla food.
3. Press **H**, find the puppy, and open its tree to see both parents. Use **Refresh** if the browser was already open.
4. Choose **Export family tree**, then **Open folder** to find the PNG.

In singleplayer, install once. On a multiplayer server, install the same Family Tree version on the server and client. The server records the pets, and the client provides the screens.

For an existing survival world, visit your pets or choose **Find existing pets**. Minecraft does not retain old parentage. **Add parents** lets you record a family you remember; it does not recover forgotten ancestry automatically.

## A horse breeding video idea

Tame two horses, feed them golden carrots, and open the foal's tree. Each card shows speed, jump height, and health. A green triangle marks a stat higher than both parents, and a red one marks a stat lower than both. Breeders can sort the browser by speed, jump, or health to pick the next pair. A possible opening line is "This mod tells you if your foal beat its parents."

Stats are in the units on the Minecraft Wiki: blocks per second, blocks of jump height, and hearts. Horses tamed before installing the mod show stats once they load, but their parents stay unknown.

## A farm or rare-variant video idea

Breed a red sheep with a white one and open the lamb's tree: the card says "Pink wool", and the panel explains that the parents' colors mixed. For a longer video, breed axolotls and wait for a blue baby. Bred axolotls turn blue about 1 time in 1,200, and Family Tree marks the blue one with a gold ★ and a note that neither parent is blue. Brown pandas work the same way, since the panel shows which hidden genes each parent carried. A possible opening line is "I bred axolotls until one turned blue, and the mod kept its whole family."

Animals other than pets join only when a player breeds them, uses a name tag on them, or clicks them with the linking stick. Name the parents before breeding if you want their names on the cards. Unnamed animals show a species name with a short code, such as "Sheep 3F2A1C".

## A short video idea

Open with the exported family image. Show the two wolves, breed the puppy, then open its tree and export the result. A possible opening line is "Minecraft forgets who your wolf's parents were. This mod keeps the family tree."

The mod also retains deceased pets. Show an existing death record if you have one. You do not need to kill a pet to demonstrate the mod.

## Downloadable media

| Asset | Use |
| --- | --- |
| [Landscape gameplay MP4](https://github.com/kharitonov-egor/family_tree/releases/download/v1.1.0/family-tree-gameplay.mp4) | A 20-second demonstration with vanilla wolves |
| [Vertical gameplay MP4](https://github.com/kharitonov-egor/family_tree/releases/download/v1.1.0/family-tree-gameplay-vertical.mp4) | The same scene in a vertical layout |
| [Exported family PNG](family-tree-gameplay-export.png) | The family produced in that scene |
| [Gameplay animation](family-tree-gameplay.webp) | An animated example for an article |
| [Pet browser](tracked-pets-browser.webp) | Search and existing-pet discovery |
| [Horse family export](horse-breeding-export.png) | A foal and its parents with speed, jump, and health |
| [Horse tree screen](horse-breeding-tree.webp) | A foal's stats beside its parents' values |
| [Sheep family export](sheep-family-export.png) | A pink lamb from a red and a white sheep |
| [Sheep tree screen](sheep-family-tree.webp) | The lamb's variant panel with both parents' wool colors |
| [Blue axolotl](blue-axolotl-tree.webp) | A rare blue axolotl with its gold ★ |

The gameplay clips show an automated demonstration in Minecraft 26.2. Vanilla feeding and AI breeding produce the puppy, and the mod records its actual parents. The horse images come from an automated scene in Minecraft 26.3, where golden carrots and vanilla AI breeding produced the foal. The sheep images come from an automated scene in Minecraft 26.3: the player named the parents with name tags and fed them wheat, and vanilla AI bred the lamb. In that scene the blue axolotl was spawned blue and named with a name tag, so it shows the rare marker but was not bred. The browser screenshot uses staged records. Preserve those distinctions in captions.

## Compatibility and limits

Version 1.2.0 supports Fabric on Minecraft 1.20.1, 1.21.1, 1.21.11, 26.1.1, 26.1.2, 26.2, and 26.3. NeoForge builds cover 1.21.1, 1.21.11, 26.2, and 26.3, and Forge covers 1.20.1. NeoForge 26.3 is beta. See the [installation table](../README.md#install) for Java and loader requirements.

Tamed wolves, cats, parrots, horses, donkeys, mules, llamas, camels, skeleton horses, and zombie horses join on their own. Camels and skeleton trap horses join once a player rides or breeds them. Every other breedable animal, vanilla or modded, joins when a player breeds, names, or adds it. Frogs, sniffers, and turtles hatch from spawn or eggs, so their babies need **Add parents**. Parrots cannot breed in vanilla Minecraft. Horse and donkey parents can link to a mule. Naturalist 2.0.7 on NeoForge 1.21.1 was tested with bred deer; other animal mods have not been tested.

Locate reports a pet's last recorded position if it is unloaded. It does not load chunks or teleport animals. Exports omit explicit player names and coordinates, but pet names appear in the image. Nothing uploads automatically.

## For modpack maintainers

Family Tree is MIT licensed. Use the platform's matching release and dependency metadata when adding it to a pack. Test in a disposable world first: discover existing pets, breed a child, restart the server, check that the family remains, and export a tree from the client. Packs with large animal farms should review `farmRecordLimit` and `autoPruneFarmRecords` in `config/familytree-server.properties`; the [README](../README.md#server-settings-for-animals) lists every setting. Check ownership rules with two players before enabling public browsing.

Please report the Minecraft version, loader, Family Tree version, other animal mods, and steps to reproduce any problem. A download or mention does not establish compatibility with a whole pack.
