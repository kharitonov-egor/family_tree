# Family Tree commands

Names match either the original pet name or its custom tree name, without regard to case. Commands also accept an exact UUID. Edits reject ambiguous names and print UUID choices. Quote names that contain spaces in commands with multiple arguments.

| Command | What it does |
| --- | --- |
| `/familytree` | Without the client mod, open the chest menu. With it, print the command list and the key that opens the screens. |
| `/familytree menu` | Open the chest menu, with or without the client mod. |
| `/familytree list` | List pets you may view. |
| `/familytree scan` | Discover loaded tamed pets and refresh existing records. Report counts for pets you may view. |
| `/familytree locate <name>` | Locate your pet, or show its last known position. List every pet with that name you may manage. |
| `/familytree info <name>` | Show a visible pet's history, known parents, and horse stats. |
| `/familytree pair <parentA> <parentB> <child>` | Link distinct pets of the same species, or horse and donkey parents for a mule. Requires permission for all three animals. |
| `/familytree unpair <child>` | Clear the child's parents. |
| `/familytree setage <name> <days>` | Set age in world days. |
| `/familytree setbirth <name> <day>` | Set the recorded birth day. |
| `/familytree prune deceased` | Permanently remove deceased records. Operators only. |
| `/familytree prune species <id>` | Permanently remove records for a species such as `minecraft:wolf`. Operators only. |

Only the owner or an operator may locate, rename, link, or change a pet's age. Public viewing does not grant those permissions. For duplicate names, use an exact UUID printed in chat or the parent picker.

The linking stick uses `/familytree confirmlink` and `/familytree cancellink` through clickable chat buttons. Pending selections expire after two minutes.

Select a pet in the tree and use Add parents for a picker with a confirmation step. Pairing allows horse and donkey parents for a mule. Prune writes a JSON backup in the world before deleting records and cancels if the backup fails.

## Chest menu

Players without the client mod use `/familytree` to open a chest menu. The bottom row holds page arrows, a status filter, a species filter, the scope toggle for players allowed to see everyone's pets, a name search, **Find existing pets**, and Close. Click a pet to open its family. Owners and operators also see Locate, Rename, Add parents, and the family book. Opening the menu shares the half-second cooldown with the screens, and Find existing pets shares the two-second scan cooldown with `/familytree scan`.

## Server settings

`config/familytree-server.properties` holds the server settings. Family Tree writes the file with defaults on first start. Restart the server after editing it.

| Setting | Default | What it does |
| --- | --- | --- |
| `viewPolicy` | `OP_ONLY_ALL` | `OP_ONLY_ALL` lets operators browse everyone's pets. `EVERYONE_ALL` lets every player browse them. `OWN_ONLY` limits everyone to their own pets. Browsing never grants editing or coordinates. |
| `vanillaMenu` | `true` | `false` turns the chest menu off. `/familytree` then prints the command list for everyone. |
| `commandAliases` | empty | Comma-separated extra names for `/familytree`, such as `pets,ft`. Names may use lowercase letters, digits, `-`, and `_`. Family Tree skips a name another command already uses and logs a warning. |
