# Launch kit

Files for posting Family Tree 1.3.0. Nothing here has been posted.

| File | Use |
| --- | --- |
| [family-tree-showcase.mp4](family-tree-showcase.mp4) | 26.5 seconds, 1920x1080, silent. For Reddit, YouTube, and creators. |
| [family-tree-showcase-vertical.mp4](family-tree-showcase-vertical.mp4) | The same clip at 1080x1920 for Shorts, TikTok, and Reels. |

The clip has six parts: two horses breeding, a pan across four generations, a foal's stats against its parents, a brown panda's gene note, the chest menu on an unmodded client, and the exported PNG. Its footer says the scenes are automated with vanilla breeding plus a two-player server. Keep that line if you trim it.

`python scripts/build_media.py --showcase` rebuilds both clips from the recordings described in [testing.md](../testing.md#page-media).

## Before posting

I couldn't read either subreddit's rules: Reddit blocks this machine. Check the sidebar and pinned posts first, especially for:

- the flair for a mod you made (r/feedthebeast has used flairs like "Mod Release" or "I made a mod!"; r/Minecraft has used a mods flair),
- whether a direct video upload or a link post is preferred,
- self-promotion limits, such as how often you may post your own project,
- whether the download link belongs in the post or in a comment.

Post from your own account, answer comments for the first few hours, and don't cross-post the same day to both subreddits.

## r/feedthebeast draft

**Title:** I made Family Tree: it records who bred whom, so you can follow your horses, wolves, and sheep across generations (Fabric, NeoForge, Forge)

**Video:** family-tree-showcase.mp4

**Body:**

Family Tree records the parents of every animal you breed and draws the family as a tree you can pan and zoom. A few things it does:

- Horse cards show speed, jump, and health, with a green ▲ when a foal beats both parents and a red ▼ when it falls short of both.
- Every breedable animal works, modded ones included. It also explains colors: why a lamb came out pink, or why two normal pandas had a brown cub.
- On a server, players without the mod type `/familytree` and get chest menus with the same families. Other players' animals are view-only, and nobody sees another player's pet coordinates.
- Optional Jade and WTHIT tooltips and JourneyMap or Xaero's waypoints. Export any tree as a PNG.

Minecraft 1.20.1, 1.21.1, 1.21.4 to 1.21.11, and 26.1 to 26.3. MIT licensed, no telemetry.

The breeding footage is an automated test scene with vanilla AI breeding; the server part is a real dedicated server with two clients.

Modrinth: https://modrinth.com/mod/familytree
CurseForge: https://www.curseforge.com/minecraft/mc-mods/family-tree
Source: https://github.com/kharitonov-egor/family_tree

I'd like to hear which animal mods you'd want tested with it.

## r/Minecraft draft

**Title:** I made a mod that draws your animals' family tree and tells you if a foal beat its parents

**Video:** family-tree-showcase.mp4 (or the vertical version if the subreddit favors it)

**First comment:**

It's called Family Tree. Every animal you breed joins a tree with both parents, horses show speed, jump, and health against their parents, and it explains inherited colors like a pink lamb or a brown panda. It works on servers too, even for players who join without the mod.

Free and open source, for Fabric, NeoForge, and Forge on Minecraft 1.20.1 to 26.3: https://modrinth.com/mod/familytree

The breeding clips are an automated test scene with vanilla AI breeding.

## Creator pitch

**Subject:** Family Tree, a Minecraft mod that draws your animals' family tree

Hi NAME,

I made Family Tree, a free Minecraft mod that records who bred whom. Breed two horses and the foal joins a tree with both parents, with a green ▲ on any stat that beat both of them. Keep breeding and you can pan through four, five, or six generations. It also explains colors, like why two normal pandas had a brown cub, and it works on servers, even for friends who join without the mod.

A 26-second clip and stills are here: https://github.com/kharitonov-egor/family_tree/blob/main/docs/creator-guide.md

It runs on Fabric, NeoForge, and Forge for Minecraft 1.20.1 to 26.3: https://modrinth.com/mod/familytree

If it fits a video, I'm happy to answer questions or set up a test world. No pressure either way.

Egor
