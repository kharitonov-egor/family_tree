# Family Tree for Paper and Spigot

## Architecture decision

Family Tree's plugin is one jar built against the Bukkit and Spigot API. It does not use server internals, so the same file runs on Spigot, Paper, and Paper forks such as Purpur, on every Minecraft version listed under supported versions.

### What servers run

bStats' global Bukkit charts on October 31, 2026 counted about 187,000 servers:

| Server software | Servers |
| --- | --- |
| Paper | 135,245 |
| Purpur | 16,226 |
| Spigot | 10,800 |
| Leaf | 4,323 |
| Folia | 1,359 |

| Minecraft | Servers |
| --- | --- |
| 26.3 | 50,048 |
| 1.21.11 | 41,496 |
| 26.2 | 39,440 |
| 26.1.2 | 12,498 |
| 1.21.4 | 6,419 |
| 1.21.10 | 4,785 |
| 1.21.1 | 4,690 |
| 1.21.8 | 4,651 |
| 1.20.1 | 4,115 |
| 1.20.4 | 1,943 |

26.3, 1.21.11, 26.2, and 26.1.2 hold three quarters of the servers. The rest is spread across the 1.21 patches and 1.20.1. Versions older than 1.20 are mostly 1.8.8 and 1.12.2 PvP servers, which lack most of the animals Family Tree records.

### Options

**Bukkit API only.** One jar covers Spigot, Paper, Purpur, and Leaf, about 90% of the servers above, across 1.20.1 to 26.3. New Minecraft versions usually keep working without a rebuild. The cost is reuse: the mod's event hooks, menus, and text are written against Minecraft classes, so the plugin needs its own versions of them.

**Paper with paperweight-userdev.** The plugin would compile the Stonecutter-generated shared source against Mojang-mapped Paper internals for each Minecraft version, and reuse the save code, menus, commands, and packet handlers. Each version would need its own Paper dev bundle, which downloads and remaps a server jar before the first build. That means at least nine extra builds, one jar per Minecraft version, and no Spigot support, because Spigot keeps obfuscated names at runtime. The mixins that record breeding, taming, and name tags don't apply on Paper either, so those would become Bukkit event listeners anyway. CraftBukkit also patches the container and command layers the shared menus and commands depend on.

### Decision

Bukkit API only. One jar reaches more servers, keeps `buildAll` to one fast Java project, and doesn't need a new build for every Minecraft release. The plugin still shares what must match the mod exactly:

- **Logic.** The plugin compiles the mod's Minecraft-free classes from `src/main/java` unchanged: permissions and parent-link validation, ancestry and cycle checks, renaming rules, generated names, variant inheritance, horse stat units, tracking rules, animal categories, the farm limit, cooldowns, and language tables. A change to those rules reaches both.
- **Saved history.** The plugin reads and writes the mod's `familytree.dat` format with its own NBT code, using the same `AnimalRecord` field names. A world keeps its history when it moves between the mod and the plugin.
- **Wire format.** The client-mod bridge writes the same payload bytes as the mod's codecs. A test checks the plugin's bytes against the expected bytes in `WireFormatTest`.
- **Translations.** The plugin ships the mod's 15 language files from `src/main/resources`.

Folia is not supported. It runs regions on separate threads and replaces the Bukkit scheduler, so every listener, menu, and save would need region-aware scheduling. Folia is under 1% of servers.
