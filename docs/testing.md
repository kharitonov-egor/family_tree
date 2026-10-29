# Runtime validation

`./gradlew buildAll --no-parallel` builds six Fabric and four NeoForge targets and runs the shared JUnit suite for each. Install JDK 21 and JDK 25. Tests cover saved records, names, ancestry, ownership, PNG exports, byte-bounded snapshot pages, parent validation, and horse stat units checked against the Minecraft Wiki's values.

The development mods under `src/smoke` and `src/demo` never enter release jars. Their runners fail the Gradle task when the expected PASS file is missing. Use disposable worlds under `build/`. Never use a normal game directory, and never copy a newer Minecraft world into an older target.

## Dedicated server checks

Enable the runner with `-Psmoke`. Fabric uses `build/smoke-<minecraft>/`; NeoForge uses `build/smoke-neoforge-<minecraft>/`. Prepare each directory with an accepted Minecraft EULA and these local-only settings:

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
./gradlew :1.21.1:runSmoke :1.21.11:runSmoke :26.1.1:runSmoke :26.1.2:runSmoke :26.2:runSmoke :26.3:runSmoke -Psmoke --no-parallel
./gradlew -p neoforge runSmoke -Psmoke -Pminecraft=1.21.1
./gradlew -p neoforge runSmoke -Psmoke -Pminecraft=1.21.11
./gradlew -p neoforge runSmoke -Psmoke -Pminecraft=26.2
./gradlew -p neoforge runSmoke -Psmoke -Pminecraft=26.3
```

The runner adds tame wolves, invokes Minecraft's breeding method, and checks entity-load discovery, the breeding mixin, inherited ownership, edit permissions, renaming, and death records. It then adds owned horses and a llama, breeds a foal, and checks the recorded stats at discovery, birth, and unload. A camel and an ownerless skeleton horse must stay unrecorded. It writes `smoke-result.txt` and stops the server. The death check calls the shared handler, so it does not prove every loader death event. The separate gameplay run checks a real Fabric death event.

For an upgrade test, copy a previous release's Family Tree data into a disposable world for the same Minecraft version and preserve its save path. To create horse history with an older release, run its jar on a Fabric dedicated server, `forceload` a few chunks, and summon owned horses, for example `summon minecraft:horse 0 -60 0 {Tame:1b,Owner:[I;1,2,3,4],InLove:600}` twice. AI breeding records a foal. Swap in the new jar, let the chunks load, and check `/familytree info` for the retained parents and filled-in stats. Use `-PsmokeHistory=<expected-count>` to require retained records before creating new pets. The 1.21.1 and 1.21.11 path is `smoke/data/familytree.dat`; 26.x worlds use `smoke/dimensions/minecraft/overworld/data/familytree/familytree.dat`.

1.21.11 reads the 1.21.1 file in place, without an import step. To check that upgrade, run the 1.21.1 smoke on a fresh world, copy its `smoke` folder into `build/smoke-1.21.11/` or `build/smoke-neoforge-1.21.11/`, and run the 1.21.11 smoke with `-PsmokeHistory` set to the number of records the 1.21.1 run created. Copying that world on into `build/smoke-26.1.1/` checks the one-time 26.x import.

## Client and network checks

Enable the runner with `-Pdemo`. Put a matching disposable world at `build/demo-<minecraft>/saves/Family Tree demo/` or `build/demo-neoforge-<minecraft>/saves/Family Tree demo/`. A Minecraft window opens during the run.

```sh
./gradlew :1.21.1:runDemo :1.21.11:runDemo :26.1.1:runDemo :26.1.2:runDemo :26.2:runDemo :26.3:runDemo -Pdemo --no-parallel
./gradlew -p neoforge runDemo -Pdemo -Pminecraft=1.21.1
./gradlew -p neoforge runDemo -Pdemo -Pminecraft=1.21.11
./gradlew -p neoforge runDemo -Pdemo -Pminecraft=26.2
./gradlew -p neoforge runDemo -Pdemo -Pminecraft=26.3
```

The runner tests a real snapshot and discovery round trip, then displays a staged example for browser, tree, portrait, and export checks. It opens Getting started, chooses a different parent in the picker, confirms through the real network, and verifies the server's saved parent ID. Screenshots and `demo-result.txt` go into the test directory. Test GUI scale 2 and a compact scale such as 4.

On this laptop, Minecraft 26.3 crashed in the Intel OpenGL driver before opening the world. Its Vulkan client passed. Use `-PgraphicsBackend=vulkan` with the 26.3 demo to select that backend explicitly. A crash can cause Minecraft to reset its saved graphics preference. This override affects only the development run.

## Real gameplay capture

```sh
./gradlew :26.2:runDemo -Pdemo -Pgameplay
```

This runner sets up named vanilla wolves in a disposable scene. Feeding uses vanilla interaction methods; the wolves' normal AI produces the puppy. The mod must record the actual child and parents. A real damage event kills one parent. The runner then requests the actual family through the network and exports it. No synthetic AnimalRecord creates that family.

This is an automated demonstration scene, not footage from a long-running survival world. The older three-generation example is staged data and remains labeled as an example.

```sh
./gradlew :26.3:runDemo -Pdemo -Phorses
./gradlew -p neoforge runDemo -Pdemo -Phorses -Pminecraft=1.21.1
```

The horse runner tames two vanilla horses, feeds them golden carrots, and waits for AI breeding. The client must receive the foal's and parents' stats through the network. It rides a camel to check that the rider becomes its owner, sorts the browser by speed, opens the foal's tree, and exports it. Each run clears the demo world's Family Tree records first, so use a disposable world. `python scripts/build_media.py --horses build/demo-26.3` copies the tree screenshot and the export into `docs/`.

On Linux without a free display, run the client under Xvfb: `xvfb-run -a -s "-screen 0 1600x1000x24" ./gradlew ...`. Unset `WAYLAND_DISPLAY` first so the window cannot open on a desktop session. Minecraft 26.3 opens its window through SDL; set `SDL_VIDEO_DRIVER=x11` so SDL uses Xvfb. To test GUI scale 4, set `guiScale:4` in the run directory's `options.txt`.

## Release packaging

After testing and committing source, run `python scripts/release_manifest.py`. It checks Minecraft metadata, Java class versions, license, icon, and development-class exclusions, then copies the tested artifacts and records hashes under `build/releases/<mod-version>/`. Publish those files with `scripts/publish_modrinth.py --manifest <path-to-manifest>`. The publisher rejects files whose hashes changed after verification.
