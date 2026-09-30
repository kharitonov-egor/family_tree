# Distribution and download target

The target is 10,000 cumulative Modrinth downloads. The audit baseline was 480. Platform totals include repeat downloads and upgrades. CurseForge needs its own total once the listing is published.

The 1.1.0 work addresses release reliability, current game support, known-family onboarding, page clarity, and PNG sharing. Public daily counters live in `docs/metrics/`; a scheduled GitHub workflow updates them. Review the seven-day change after releases and page changes. The first dated page experiment uses the promise "Keep your first wolf in the family tree after it's gone" and shows an actual exported family before explaining controls.

## Available media

- `family-tree-gameplay.mp4` is a landscape clip of vanilla feeding, AI breeding, recorded parents, and export.
- `family-tree-gameplay-vertical.mp4` keeps the full game image in a vertical layout so both parents remain visible.
- `family-tree-gameplay.webp` is the gallery animation.
- `family-tree-gameplay-export.png` is the actual family export from that scene.
- `family-tree-demo.webp` is the older staged three-generation example.

The real gameplay runner sets up a disposable scene. It uses actual vanilla wolves and breeding, but the scene is automated. Captions and page copy must preserve that distinction. See `testing.md` and `scripts/build_media.py` for capture and packaging.

Creator and modpack outreach are excluded from this execution. No invitations or community posts were sent. The prepared media can support player sharing and future promotion chosen by the owner.

## CurseForge listing

The [Family Tree project](https://www.curseforge.com/minecraft/mc-mods/family-tree) is project `1718788`. The project and all eight 1.1.0 files were submitted on September 30 UTC and are under moderator review. They are not yet publicly listed or synchronized to the CurseForge app.

The listing has the saved logo, MIT license, Utility & QoL and Mobs categories, GitHub source and documentation links, and a bug-report link in the description. Comments and third-party distribution are enabled. Four gallery images have captions that distinguish actual automated vanilla breeding from staged pet records. The animated gameplay scene also renders in the description. The media dialog supports YouTube videos, so the MP4 clips remain on the GitHub release.

| Minecraft | Loader | File ID | Release type |
| --- | --- | --- | --- |
| 1.21.1 | Fabric | 9015237 | Release |
| 26.1.1 | Fabric | 9015261 | Release |
| 26.1.2 | Fabric | 9015267 | Release |
| 26.2 | Fabric | 9015268 | Release |
| 26.3 | Fabric | 9015269 | Release |
| 1.21.1 | NeoForge | 9015270 | Release |
| 26.2 | NeoForge | 9015271 | Release |
| 26.3 | NeoForge | 9015272 | Beta |

Each file has exact Minecraft, loader, and Java tags, both Client and Server tags, and automatic publication after approval. All five Fabric files require Fabric API, project `306612`. NeoForge files have no dependency on Fabric API. The uploaded filenames, sizes, tags, release types, and dependencies were checked through the author API. All eight CDN jars were downloaded and matched the release manifest's SHA-512 hashes.

## Publication record

On September 30 UTC, the tested source was committed as `425356c6826b851948c377162a690a4ed9f1de71` and tagged `v1.1.0`. The [GitHub release](https://github.com/kharitonov-egor/family_tree/releases/tag/v1.1.0) contains eight mod jars, eight source jars, the SHA-512 manifest, and both gameplay clips. The [Modrinth page](https://modrinth.com/mod/familytree) has all eight matching releases, the revised description, and the new gallery. NeoForge 26.3 is beta. The archived Fabric releases now declare Fabric API.

The main and tag build workflows passed all eight Linux jobs. The manually dispatched download-count workflow passed and committed its sample. Public-file verification downloaded and hashed 27 files across GitHub and Modrinth. That check downloaded each of the eight new Modrinth jars once. Those downloads are release verification, not evidence of new players. The audit baseline remains 480 downloads.

CurseForge submission is complete and awaits moderation. The updated `curseforge-upload-kit.zip` on the GitHub release contains the eight verified jars, manifest, listing copy, a logo below the 100 KiB limit, media, changelog, and the existing project's URL. Do not upload duplicate files from the kit. Distribution uploads fetched seven more jars from Modrinth, and CurseForge CDN verification fetched eight jars. These are verification downloads, not evidence of new players. Keep CurseForge counts separate from the 10,000-download Modrinth target.

## Measurement decisions

At 30 days, compare the actual download rate with the remaining target. The audit's 180-day deadline requires roughly 53 additional downloads a day from the baseline. If the rate stays below that, broader distribution is still needed. More code alone cannot establish that the target will be reached.

Keep a dated record of releases, published page changes, download totals, and support issues. Public counters do not expose visitor conversion or unique active players. No in-game telemetry is used.
