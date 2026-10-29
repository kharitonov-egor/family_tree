# Runtime validation

`./gradlew buildAll --no-parallel` builds seven Fabric, four NeoForge, and one Forge target and runs the shared JUnit suite for each. Install JDK 17, JDK 21, and JDK 25. The 1.20.1 tests and development runs use JDK 17. Tests cover saved records, names, ancestry, ownership, PNG exports, byte-bounded snapshot pages, parent validation, horse stat units checked against the Minecraft Wiki's values, variant inheritance and rarity, animal categories, tracking settings, the farm limit and cleanup order, large farm layouts, and portrait transparency. `WireFormatTest` expects the same payload bytes on every target, which checks the 1.20.1 codec backport against Minecraft's codecs.

The development mods under `src/smoke` and `src/demo` never enter release jars. Their runners fail the Gradle task when the expected PASS file is missing. Use disposable worlds under `build/`. Never use a normal game directory, and never copy a newer Minecraft world into an older target.

## Dedicated server checks

Enable the runner with `-Psmoke`. Fabric uses `build/smoke-<minecraft>/`; NeoForge uses `build/smoke-neoforge-<minecraft>/`; Forge uses `build/smoke-forge-<minecraft>/`. Prepare each directory with an accepted Minecraft EULA and these local-only settings:

```properties
level-name=smoke
server-ip=127.0.0.1
server-port=0
online-mode=false
view-distance=3
simulation-distance=3
spawn-protection=0
```

```sh
./gradlew :1.20.1:runSmoke :1.21.1:runSmoke :1.21.11:runSmoke :26.1.1:runSmoke :26.1.2:runSmoke :26.2:runSmoke :26.3:runSmoke -Psmoke --no-parallel
./gradlew -p neoforge runSmoke -Psmoke -Pminecraft=1.21.1
./gradlew -p neoforge runSmoke -Psmoke -Pminecraft=1.21.11
./gradlew -p neoforge runSmoke -Psmoke -Pminecraft=26.2
./gradlew -p neoforge runSmoke -Psmoke -Pminecraft=26.3
./gradlew -p forge runSmoke -Psmoke -Pminecraft=1.20.1
```

The runner adds tame wolves, invokes Minecraft's breeding method, and checks entity-load discovery, the breeding mixin, inherited ownership, edit permissions, renaming, and death records. It then adds owned horses and a llama, breeds a foal, and checks the recorded stats at discovery, birth, and unload. A camel and an ownerless skeleton horse must stay unrecorded. Then it checks animals other than pets: a wild cow and a cow bred without a player stay out, a player's sheep breeding records the lamb and both parents for the breeder with mixed wool colors, a recorded family keeps growing without a player, an added axolotl keeps its color, and every vanilla variant reads and applies on the running Minecraft version. It rewrites `config/familytree-server.properties` to check a disabled category, the farm limit, and opt-in cleanup with its backup, then restores the file. It writes `smoke-result.txt` and stops the server. The death check calls the shared handler, so it does not prove every loader death event. The separate gameplay run checks a real Fabric death event.

For an upgrade test, copy a previous release's Family Tree data into a disposable world for the same Minecraft version and preserve its save path. To create horse history with an older release, run its jar on a Fabric dedicated server, `forceload` a few chunks, and summon owned horses, for example `summon minecraft:horse 0 -60 0 {Tame:1b,Owner:[I;1,2,3,4],InLove:600}` twice. AI breeding records a foal. Swap in the new jar, let the chunks load, and check `/familytree info` for the retained parents and filled-in stats. Use `-PsmokeHistory=<expected-count>` to require retained records before creating new pets. The 1.20.1, 1.21.1, and 1.21.11 path is `smoke/data/familytree.dat`; 26.x worlds use `smoke/dimensions/minecraft/overworld/data/familytree/familytree.dat`.

1.21.11 reads the 1.21.1 file in place, without an import step. To check that upgrade, run the 1.21.1 smoke on a fresh world, copy its `smoke` folder into `build/smoke-1.21.11/` or `build/smoke-neoforge-1.21.11/`, and run the 1.21.11 smoke with `-PsmokeHistory` set to the number of records the 1.21.1 run created. Copying that world on into `build/smoke-26.1.1/` checks the one-time 26.x import. A 1.20.1 world upgrades to 1.21.1 the same way: copy its `smoke` folder into `build/smoke-1.21.1/` and run that smoke with `-PsmokeHistory`.

## Client and network checks

Enable the runner with `-Pdemo`. Put a matching disposable world at `build/demo-<minecraft>/saves/Family Tree demo/`, `build/demo-neoforge-<minecraft>/saves/Family Tree demo/`, or `build/demo-forge-<minecraft>/saves/Family Tree demo/`. A Minecraft window opens during the run. To make a 1.20.1 world without copying a newer one, start the vanilla 1.20.1 server jar with `level-name=Family Tree demo` and `level-type=minecraft\:flat`, stop it with `stop`, and copy the world folder.

```sh
./gradlew :1.20.1:runDemo :1.21.1:runDemo :1.21.11:runDemo :26.1.1:runDemo :26.1.2:runDemo :26.2:runDemo :26.3:runDemo -Pdemo --no-parallel
./gradlew -p neoforge runDemo -Pdemo -Pminecraft=1.21.1
./gradlew -p neoforge runDemo -Pdemo -Pminecraft=1.21.11
./gradlew -p neoforge runDemo -Pdemo -Pminecraft=26.2
./gradlew -p neoforge runDemo -Pdemo -Pminecraft=26.3
./gradlew -p forge runDemo -Pdemo -Pminecraft=1.20.1
```

The runner tests a real snapshot and discovery round trip, then displays a staged example for browser, tree, portrait, and export checks. It opens Getting started, chooses a different parent in the picker, confirms through the real network, and verifies the server's saved parent ID. Screenshots and `demo-result.txt` go into the test directory. Test GUI scale 2 and a compact scale such as 4.

On this laptop, Minecraft 26.3 crashed in the Intel OpenGL driver before opening the world. Its Vulkan client passed. Use `-PgraphicsBackend=vulkan` with the 26.3 demo to select that backend explicitly. A crash can cause Minecraft to reset its saved graphics preference. This override affects only the development run.

## Animals other than pets

```sh
./gradlew :26.3:runDemo -Pdemo -Panimals
./gradlew -p neoforge runDemo -Pdemo -Panimals -Pminecraft=1.21.1
```

The animal runner names four sheep and a blue axolotl with real name tags, which records them for the player through the name tag hook. It feeds the sheep wheat in two pens, and vanilla AI breeds a pink lamb from red and white parents and a purple lamb from blue and red parents. It also feeds two frogs slime balls, which records both frogs for the player because frog spawn hatches later without parents. The client must receive both lambs with their wool colors, the pink lamb's `mixed` origin saved at birth, the frogs, and the axolotl through the network. It opens the browser and the pink lamb's tree, exports it, and opens the axolotl's tree. `python scripts/build_media.py --animals build/demo-26.3` copies the export and screens into `docs/`. Each run clears the demo world's Family Tree records first.

To check a modded animal, put the mod's jar in the run directory's `mods/` folder and name the species and its breeding food:

```sh
./gradlew -p neoforge runDemo -Pdemo -Panimals -PmoddedAnimal=naturalist:deer -PmoddedFood=minecraft:apple -Pminecraft=1.21.1
```

The same steps then run with four deer: name tags, feeding, AI breeding, both babies with their parents over the network, the tree, and the export. Naturalist 2.0.7 for NeoForge 1.21.1 passed this check.

```sh
./gradlew :26.3:runDemo -Pdemo -Pfarm
```

The farm runner fences 400 cows, sheep, pigs, and chickens, feeds every one, and waits for vanilla AI breeding to finish. It writes `perf-result.txt` with the save encoding time and compressed size, the snapshot build time, page count, and bytes, a cleanup backup's time and size, the snapshot round trip, and frame rates for the world, a text-only screen, the browser, and the sheep forest. It then adds synthetic records, labeled as such, until the history holds 5,000 unnamed farm animals and measures again. Frame rates under Xvfb come from software rendering and depend on the machine's load, so compare each screen with the text-only screen from the same run.

## Real gameplay capture

```sh
./gradlew :26.2:runDemo -Pdemo -Pgameplay
```

This runner sets up named vanilla wolves in a disposable scene. Feeding uses vanilla interaction methods; the wolves' normal AI produces the puppy. The mod must record the actual child and parents. A real damage event kills one parent. The runner then requests the actual family through the network and exports it. No synthetic AnimalRecord creates that family.

This is an automated demonstration scene, not footage from a long-running survival world. The older three-generation example is staged data and remains labeled as an example.

```sh
./gradlew :26.3:runDemo -Pdemo -Phorses
./gradlew -p neoforge runDemo -Pdemo -Phorses -Pminecraft=1.21.1
./gradlew :1.20.1:runDemo -Pdemo -Phorses
./gradlew -p forge runDemo -Pdemo -Phorses -Pminecraft=1.20.1
```

The horse runner tames two vanilla horses, feeds them golden carrots, and waits for AI breeding. The client must receive the foal's and parents' stats through the network. It rides a camel to check that the rider becomes its owner, sorts the browser by speed, opens the foal's tree, and exports it. Each run clears the demo world's Family Tree records first, so use a disposable world. `python scripts/build_media.py --horses build/demo-26.3` copies the tree screenshot and the export into `docs/`.

On Linux without a free display, run the client under Xvfb: `xvfb-run -a -s "-screen 0 1600x1000x24" ./gradlew ...`. Unset `WAYLAND_DISPLAY` first so the window cannot open on a desktop session. Minecraft 26.3 opens its window through SDL; set `SDL_VIDEO_DRIVER=x11` so SDL uses Xvfb. To test GUI scale 4, set `guiScale:4` in the run directory's `options.txt`. Before trusting a run, read `DISPLAY` from the game process's `/proc/<pid>/environ` to confirm it uses the Xvfb display.

## Release jars in a real 1.20.1 instance

The development runs load unobfuscated classes. Check the release jars in launcher-style instances too. [portablemc](https://github.com/mindstorm38/portablemc) launches offline accounts:

```sh
portablemc --main-dir ~/.cache/pmc start fabric:1.20.1:0.19.3 --mc-dir build/real-fabric -u Tester --join-world "Family Tree real"
portablemc --main-dir ~/.cache/pmc start forge::1.20.1-47.4.28 --mc-dir build/real-forge -u Tester --join-world "Family Tree real"
```

Put the release jar in each instance's `mods/`; Fabric also needs Fabric API 0.92.12+1.20.1. Use a disposable 1.20.1 world with commands allowed. Tame two summoned wolves with bones and feed them beef. Breed them, open the tree with **H**, export a PNG, rename and locate a pet, then quit through the pause menu and reopen the world to check the family. Repeat the Forge run on 47.1.0, the oldest Forge build the jar declares.

## Chest menus on a vanilla client

The chest menus need a real dedicated server and an unmodded client. Install the release jar on a Fabric, NeoForge, or Forge server in a disposable directory with `online-mode=false`, `server-ip=127.0.0.1`, and `difficulty=peaceful`. Keep `spawn-animals=true` on 1.21.1 and older; with `false`, those servers delete every animal each tick, including summoned pets. Start a vanilla client with portablemc, for example `portablemc --main-dir ~/.cache/pmc start 1.21.1 --mc-dir build/vanilla-client -u Tester --join-server 127.0.0.1 --join-server-port 25565`. Set `lang:` in its `options.txt` to check the server's translations.

Summon owned, named wolves from the server console with the player's offline UUID as `Owner`, set `InLove:600` on two of them, and summon one inside the ground to record a suffocation. Then type `/familytree` and check:

- the list, filters, search, and Find existing pets,
- a family, moving to a parent and back,
- Locate, Rename, Add parents with its confirmation, and the book,
- a pet owned by someone else, which must show no Locate, Rename, or Add parents,
- shift-click, number keys, double-click, dragging, middle-click, `Q`, `F` over an icon, and closing with an item picked up from the inventory. Compare `data get entity <player> Inventory` before and after, and check the offhand slot in the hotbar.

Join the same server with a modded client as well. `/familytree` must print the command list, and **H** must open the screens.

## Release packaging

After testing and committing source, run `python scripts/release_manifest.py`. It checks Minecraft metadata, Java class versions, license, icon, and development-class exclusions, then copies the tested artifacts and records hashes under `build/releases/<mod-version>/`. Publish those files with `scripts/publish_modrinth.py --manifest <path-to-manifest>`. The publisher rejects files whose hashes changed after verification.
