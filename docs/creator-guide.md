# Family Tree creator guide

Family Tree is a free Minecraft Java mod that records pets' parents and descendants. Deceased pets stay in the family, and players can export the tree as a PNG.

[Download on Modrinth](https://modrinth.com/mod/familytree/versions) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/family-tree/files). [Source and license](https://github.com/kharitonov-egor/family_tree). [Report a bug](https://github.com/kharitonov-egor/family_tree/issues).

## Try it in a minute

1. Install the file for your Minecraft version and loader. Fabric also needs the matching Fabric API and Fabric Loader 0.19.3 or newer.
2. In a test world, tame two wolves and breed them using vanilla food.
3. Press **H**, find the puppy, and open its tree to see both parents. Use **Refresh** if the browser was already open.
4. Choose **Export family tree**, then **Open folder** to find the PNG.

In singleplayer, install once. On a multiplayer server, install the same Family Tree version on the server and client. The server records the pets, and the client provides the screens.

For an existing survival world, visit your pets or choose **Find existing pets**. Minecraft does not retain old parentage. **Add parents** lets you record a family you remember; it does not recover forgotten ancestry automatically.

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

The gameplay clips show an automated demonstration in Minecraft 26.2. Vanilla feeding and AI breeding produce the puppy, and the mod records its actual parents. The browser screenshot uses staged records. Preserve those distinctions in captions.

## Compatibility and limits

Version 1.1.0 supports Fabric on Minecraft 1.21.1, 26.1.1, 26.1.2, 26.2, and 26.3. NeoForge builds cover 1.21.1, 26.2, and 26.3. NeoForge 26.3 is beta. See the [installation table](../README.md#install) for Java and loader requirements.

The tracker covers vanilla tamed wolves, cats, parrots, horses, donkeys, mules, and llamas. Parrots cannot breed in vanilla Minecraft. Horse and donkey parents can link to a mule. Compatibility with modded animal species or changed animal models has not been tested.

Locate reports a pet's last recorded position if it is unloaded. It does not load chunks or teleport animals. Exports omit explicit player names and coordinates, but pet names appear in the image. Nothing uploads automatically.

## For modpack maintainers

Family Tree is MIT licensed. Use the platform's matching release and dependency metadata when adding it to a pack. Test in a disposable world first: discover existing pets, breed a child, restart the server, check that the family remains, and export a tree from the client. Check ownership rules with two players before enabling public browsing.

Please report the Minecraft version, loader, Family Tree version, other animal mods, and steps to reproduce any problem. A download or mention does not establish compatibility with a whole pack.
