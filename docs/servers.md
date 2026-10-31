# Family Tree on servers

Install Family Tree on the server for tracking. Players with the client mod get the screens; players without it type `/familytree` for chest menus. This page is for server owners and modpack authors: permission nodes, every server setting, datapack tags, and the setups Family Tree was tested with.

## Admin commands

| Command | Node | What it does |
| --- | --- | --- |
| `/familytree reload` | `familytree.reload` | Reads `config/familytree-server.properties` again. Reports each value it can't use, in the log and to the admin who ran it, and uses the default for that value. |
| `/familytree settings` | `familytree.settings` | Lists the settings that differ from their defaults, which permission API Family Tree uses, and the species in the `familytree:tracked` and `familytree:ignored` tags. |
| `/familytree settings all` | `familytree.settings` | Lists every setting. |

The console can always run both.

## Permission nodes

Family Tree checks these nodes through fabric-permissions-api on Fabric and through PermissionAPI on NeoForge and Forge. LuckPerms hooks into both. Without a permission mod, or when a node is unset, each node falls back to the default in the table, which is what Family Tree 1.3.0 did with operator levels. An operator is a player with permission level 2 or higher, or the level in `operatorLevel`.

| Node | Default | Covers |
| --- | --- | --- |
| `familytree.use` | everyone | `/familytree`, `list`, `info`, the Family Tree screens, and Jade and WTHIT tooltips |
| `familytree.menu` | everyone | The chest menu: `/familytree` without the client mod, and `/familytree menu` |
| `familytree.scan` | everyone | `/familytree scan`, and **Find existing pets** in the screens and the chest menu |
| `familytree.locate` | everyone, or operators with `ownersCanLocate=false` | `/familytree locate`, **Locate pet**, map waypoints |
| `familytree.rename` | everyone, or operators with `ownersCanRename=false` | Tree names from the screens and the chest menu |
| `familytree.parents` | everyone, or operators with `ownersCanEditParents=false` | `pair`, `unpair`, **Add parents**, and confirming links with the linking stick |
| `familytree.age` | everyone, or operators with `ownersCanSetAge=false` | `setage`, `setbirth` |
| `familytree.linkingstick` | everyone | Clicking animals with the linking stick, which also adds animals other than pets |
| `familytree.others.view` | set by `viewPolicy`: operators, everyone, or nobody | Browsing every player's pets |
| `familytree.others.manage` | operators | Locating, renaming, and editing other players' pets. Each action also needs its own node. |
| `familytree.prune` | operators | `/familytree prune deceased`, `farm`, and `species` |
| `familytree.reload` | operators | `/familytree reload` |
| `familytree.settings` | operators | `/familytree settings` |

A player who owns a pet needs only the action's node to act on it. Acting on someone else's pet needs the action's node and `familytree.others.manage`. Browsing never grants editing or coordinates. Commands a player's nodes deny disappear from their tab completion; `/familytree reload` resends the command list to everyone online.

On Fabric, fabric-permissions-api is optional. Family Tree doesn't bundle it. LuckPerms and most other permission mods include it, and without it Family Tree uses the defaults above.

## Settings

Family Tree writes `config/familytree-server.properties` on first start, with a comment above every setting that gives its default and allowed values. Edit it, then run `/familytree reload` or restart. A 1.3.0 file loads unchanged and gains the new settings with their defaults.

When a value can't be used, Family Tree uses the default, or the nearest allowed number, and keeps your text in the file so you can fix it. Settings it doesn't recognize, such as a misspelled key, stay at the end of the file under "Not recognized". Family Tree rewrites the file to add missing settings and comments, so comments you add yourself are not kept.

| Setting | Default | Allowed | What it does |
| --- | --- | --- | --- |
| `viewPolicy` | `OP_ONLY_ALL` | `OP_ONLY_ALL`, `EVERYONE_ALL`, `OWN_ONLY` | Who may browse every player's pets: operators, everyone, or nobody. Default for `familytree.others.view`. |
| `operatorLevel` | `2` | 1 to 4 | The permission level Family Tree treats as an operator |
| `ownersCanLocate` | `true` | `true`, `false` | `false` leaves locating and waypoints to operators. Default for `familytree.locate`. |
| `ownersCanRename` | `true` | `true`, `false` | `false` leaves renaming to operators. Default for `familytree.rename`. |
| `ownersCanEditParents` | `true` | `true`, `false` | `false` leaves parent edits to operators. Default for `familytree.parents`. |
| `ownersCanSetAge` | `true` | `true`, `false` | `false` leaves age corrections to operators. Default for `familytree.age`. |
| `vanillaMenu` | `true` | `true`, `false` | `false` turns the chest menu off for everyone |
| `commandAliases` | empty | comma-separated names | Extra names for `/familytree`, such as `pets,ft`. Takes effect after `/reload` or a restart. |
| `listLimit` | `100` | 1 to 1000 | Lines `/familytree list` prints |
| `openCooldownMillis` | `500` | 0 to 60000 | Per player: opening the screens or chest menu |
| `scanCooldownMillis` | `2000` | 0 to 60000 | Per player: `/familytree scan` and **Find existing pets** |
| `serverScanCooldownMillis` | `2000` | 0 to 60000 | Shared by all players: time between any two scans |
| `locateCooldownMillis` | `250` | 0 to 60000 | Per player: Locate and waypoints |
| `editCooldownMillis` | `250` | 0 to 60000 | Per player: renames and parent edits |
| `linkingStick` | `true` | `true`, `false` | `false` turns the linking stick off |
| `linkingStickName` | `familytree` | 1 to 50 characters | The anvil name that makes a stick the linking stick, ignoring case |
| `linkingSelectionSeconds` | `120` | 10 to 3600 | How long the linking stick keeps a selection |
| `serverLanguage` | `auto` | `auto` or a language code | `auto` uses each player's language for chat and chest menus. A code such as `en_us` uses one language for everyone. |
| `farmLimitMessage` | `true` | `true`, `false` | Whether a breeder hears once when `farmRecordLimit` stops recording |
| `locateTeleportLink` | `true` | `true`, `false` | Whether operators can click Locate's coordinates to fill in a teleport command |
| `discoverPetsOnLoad` | `true` | `true`, `false` | Whether tamed pets missing from the history join it when their chunk loads |
| `trackFarmAnimals` | `true` | `true`, `false` | Cows, mooshrooms, sheep, pigs, chickens, rabbits, and goats |
| `trackWildlife` | `true` | `true`, `false` | Foxes, axolotls, frogs, bees, turtles, pandas, and other vanilla animals |
| `trackModdedAnimals` | `true` | `true`, `false` | Breedable animals from other mods |
| `trackedSpecies` | empty | species ids | Species to record even when their category is off |
| `ignoredSpecies` | empty | species ids | Species to never record, including new pets |
| `farmRecordLimit` | `5000` | 0 to 50000 | Unnamed animals other than pets the history keeps |
| `autoPruneFarmRecords` | `false` | `true`, `false` | Back up, then delete the oldest deceased unnamed animals other than pets at the limit |

Every cooldown accepts 0, which turns it off. The server-wide scan cooldown protects busy servers, so keep it unless scans are rare.

## Datapack tags

Two entity type tags choose species from a datapack or KubeJS: `familytree:tracked` records a species even when its category is off, and `familytree:ignored` never records it. Family Tree ships both empty, so other tags can reference them.

```json
{
  "replace": false,
  "values": ["minecraft:bee", "naturalist:snail"]
}
```

Put that file at `data/familytree/tags/entity_type/ignored.json` in a datapack. Minecraft 1.20.1 uses `tags/entity_types/` instead. With KubeJS: `ServerEvents.tags('entity_type', event => event.add('familytree:ignored', 'minecraft:bee'))`. Tags change with `/reload`.

When they disagree, Family Tree decides in this order and stops at the first match:

1. `ignoredSpecies` in the settings file: not recorded.
2. `trackedSpecies` in the settings file: recorded.
3. The `familytree:ignored` tag: not recorded.
4. The `familytree:tracked` tag: recorded.
5. Pets are recorded. Other animals follow `trackFarmAnimals`, `trackWildlife`, and `trackModdedAnimals`.

The settings file is the server owner's, so it wins over a modpack's tags. Like the settings, tags only decide which new animals join. They never remove history.
