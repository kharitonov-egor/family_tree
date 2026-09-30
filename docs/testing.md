# Runtime validation

`./gradlew buildAll --no-parallel` builds five Fabric and three NeoForge targets and runs the shared JUnit suite for each. Install JDK 21 and JDK 25. Tests cover saved records, names, ancestry, ownership, PNG exports, byte-bounded snapshot pages, and parent validation.

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
./gradlew :1.21.1:runSmoke :26.1.1:runSmoke :26.1.2:runSmoke :26.2:runSmoke :26.3:runSmoke -Psmoke --no-parallel
./gradlew -p neoforge runSmoke -Psmoke -Pminecraft=1.21.1
./gradlew -p neoforge runSmoke -Psmoke -Pminecraft=26.2
./gradlew -p neoforge runSmoke -Psmoke -Pminecraft=26.3
```

The runner adds tame wolves, invokes Minecraft's breeding method, and checks entity-load discovery, the breeding mixin, inherited ownership, edit permissions, renaming, and death records. It writes `smoke-result.txt` and stops the server. The death check calls the shared handler, so it does not prove every loader death event. The separate gameplay run checks a real Fabric death event.

For an upgrade test, copy a previous release's Family Tree data into a disposable world for the same Minecraft version and preserve its save path. Use `-PsmokeHistory=<expected-count>` to require retained records before creating new pets. The 1.21.1 path is `smoke/data/familytree.dat`; modern worlds use `smoke/dimensions/minecraft/overworld/data/familytree/familytree.dat`.

## Client and network checks

Enable the runner with `-Pdemo`. Put a matching disposable world at `build/demo-<minecraft>/saves/Family Tree demo/` or `build/demo-neoforge-<minecraft>/saves/Family Tree demo/`. A Minecraft window opens during the run.

```sh
./gradlew :1.21.1:runDemo :26.1.1:runDemo :26.1.2:runDemo :26.2:runDemo :26.3:runDemo -Pdemo --no-parallel
./gradlew -p neoforge runDemo -Pdemo -Pminecraft=1.21.1
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

## Release packaging

After testing and committing source, run `python scripts/release_manifest.py`. It checks Minecraft metadata, Java class versions, license, icon, and development-class exclusions, then copies the tested artifacts and records hashes under `build/releases/<mod-version>/`. Publish those files with `scripts/publish_modrinth.py --manifest <path-to-manifest>`. The publisher rejects files whose hashes changed after verification.
