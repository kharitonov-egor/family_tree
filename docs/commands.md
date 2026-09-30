# Family Tree commands

Names match either the original pet name or its custom tree name, without regard to case. Commands also accept an exact UUID. Edits reject ambiguous names and print UUID choices. Quote names that contain spaces in commands with multiple arguments.

| Command | What it does |
| --- | --- |
| `/familytree list` | List pets you may view. |
| `/familytree scan` | Discover loaded tamed pets and refresh existing records. Report counts for pets you may view. |
| `/familytree locate <name>` | Locate your pet, or show its last known position. List every pet with that name you may manage. |
| `/familytree info <name>` | Show a visible pet's history and known parents. |
| `/familytree pair <parentA> <parentB> <child>` | Link distinct pets of the same species, or horse and donkey parents for a mule. Requires permission for all three animals. |
| `/familytree unpair <child>` | Clear the child's parents. |
| `/familytree setage <name> <days>` | Set age in world days. |
| `/familytree setbirth <name> <day>` | Set the recorded birth day. |
| `/familytree prune deceased` | Permanently remove deceased records. Operators only. |
| `/familytree prune species <id>` | Permanently remove records for a species such as `minecraft:wolf`. Operators only. |

Only the owner or an operator may locate, rename, link, or change a pet's age. Public viewing does not grant those permissions. For duplicate names, use an exact UUID printed in chat or the parent picker.

The linking stick uses `/familytree confirmlink` and `/familytree cancellink` through clickable chat buttons. Pending selections expire after two minutes.

Select a pet in the tree and use Add parents for a picker with a confirmation step. Pairing allows horse and donkey parents for a mule. Prune writes a JSON backup in the world before deleting records and cancels if the backup fails.
