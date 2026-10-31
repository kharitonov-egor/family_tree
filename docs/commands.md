# Family Tree commands

Names match either the original pet name or its custom tree name, without regard to case. Commands also accept an exact UUID. When a name matches more than one pet, the command prints each choice with its UUID; click one to fill in the command with that UUID. Quote names that contain spaces in commands with multiple arguments. `info` and `locate` accept the name with or without quotes.

| Command | What it does |
| --- | --- |
| `/familytree` | Without the client mod, open the chest menu. With it, print the command list and the key that opens the screens. |
| `/familytree menu` | Open the chest menu, with or without the client mod. |
| `/familytree list` | List animals you may view, up to 100 lines. |
| `/familytree scan` | Discover loaded tamed pets and refresh animals already in the history. Report counts for animals you may view. |
| `/familytree locate <name>` | Locate your pet, or show its last known position. List every pet with that name you may manage. |
| `/familytree info <name>` | Show a visible animal's history, known parents, variant, and horse stats. For a baby, also show its parents' variants and how it got its own. |
| `/familytree pair <parentA> <parentB> <child>` | Link distinct pets of the same species, or horse and donkey parents for a mule. Requires permission for all three animals. |
| `/familytree unpair <child>` | Clear the child's parents. |
| `/familytree breed <a> <b>` | Predict a foal of two visible animals: each stat's range, most likely value, and chance to beat both parents, the variant odds, the foal's inbreeding coefficient, and how the two are related. Quote names that contain spaces. |
| `/familytree setage <name> <days>` | Set age in world days. |
| `/familytree setbirth <name> <day>` | Set the recorded birth day. |
| `/familytree prune deceased` | Permanently remove deceased records. Operators only by default. |
| `/familytree prune farm` | Permanently remove deceased unnamed animals other than pets. Pets, named animals, and living animals stay. Operators only by default. |
| `/familytree prune species <id>` | Permanently remove records for a species such as `minecraft:wolf`. Operators only by default. |
| `/familytree reload` | Read `config/familytree-server.properties` again and report each value Family Tree can't use. Operators only by default. |
| `/familytree settings [all]` | List the server settings that differ from their defaults, or all of them, and the species in the `familytree:tracked` and `familytree:ignored` tags. Operators only by default. |

`setage`, `setbirth`, `list`, and the screens count world days. A world day is 24,000 ticks, or 20 minutes of the world running, so sleeping and `/time` don't add days.

Only the owner or an operator may locate, rename, link, or change a pet's age. Public viewing does not grant those permissions. A permission mod such as LuckPerms can grant or deny each command and action through `familytree.*` permission nodes; [Family Tree on servers](servers.md#permission-nodes) lists them. A command a player's nodes deny answers with the node it needs. For duplicate names, click a choice in chat or use the parent picker.

The linking stick uses `/familytree confirmlink` and `/familytree cancellink` through clickable chat buttons. Pending selections expire after two minutes. Clicking an animal other than a pet that isn't in the history adds it for you, unless the server doesn't record its species.

Select a pet in the tree and use Add parents for a picker with a confirmation step. Pairing allows horse and donkey parents for a mule. Prune first says how many records it would remove; run the same command again with `confirm` at the end, such as `/familytree prune farm confirm`, to remove them. It then writes a JSON backup named by date and time in the world's `familytree-backups/` folder and cancels if the backup fails. Children of a removed animal keep its ID as a parent, and their trees mark the missing parent.

## Chest menu

Players without the client mod use `/familytree` to open a chest menu. The bottom row holds page arrows, a status filter, a species filter, the scope toggle for players allowed to see everyone's pets, a name search, **Find existing pets**, and Close. Click a pet to open its family. Owners and operators also see Locate, Rename, Add parents, and the family book. A living animal that can breed also shows **Plan breeding**, which lists the mates the player may view and opens the planner's prediction as a book. Opening the menu shares the half-second cooldown with the screens, and Find existing pets shares the two-second scan cooldown with `/familytree scan`; both lengths are server settings. A button disappears when the player's permission node denies its action, so a server can hide Locate, Rename, Add parents, Find existing pets, or Plan breeding.

## Server settings

`config/familytree-server.properties` holds the server settings. Family Tree writes the file with defaults and a comment for each setting on first start. Run `/familytree reload` after editing it. These are the settings for commands and the chest menu; [Family Tree on servers](servers.md#settings) lists every setting, including cooldowns, owner actions, the linking stick, and the chat language.

| Setting | Default | What it does |
| --- | --- | --- |
| `viewPolicy` | `OP_ONLY_ALL` | `OP_ONLY_ALL` lets operators browse everyone's pets. `EVERYONE_ALL` lets every player browse them. `OWN_ONLY` limits everyone to their own pets. Browsing never grants editing or coordinates. |
| `vanillaMenu` | `true` | `false` turns the chest menu off. `/familytree` then prints the command list for everyone. |
| `commandAliases` | empty | Comma-separated extra names for `/familytree`, such as `pets,ft`. Names may use lowercase letters, digits, `-`, and `_`. Family Tree skips a name another command already uses and logs a warning. Takes effect after `/reload` or a restart. |
