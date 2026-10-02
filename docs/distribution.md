# Distribution and download target

The target is 10,000 cumulative Modrinth downloads. The audit baseline was 480. Platform totals include repeat downloads and upgrades. On October 2, Modrinth had 566 downloads and CurseForge had 98. Keep the platform totals separate.

The 1.1.0 work addresses release reliability, current game support, known-family onboarding, page clarity, and PNG sharing. Public daily counters live in `docs/metrics/`; a scheduled GitHub workflow updates them. Review the seven-day change after releases and page changes. The first dated page experiment uses the promise "Keep your first wolf in the family tree after it's gone" and shows an actual exported family before explaining controls.

## Available media

- `family-tree-gameplay.mp4` is a landscape clip of vanilla feeding, AI breeding, recorded parents, and export.
- `family-tree-gameplay-vertical.mp4` keeps the full game image in a vertical layout so both parents remain visible.
- `family-tree-gameplay.webp` is the gallery animation.
- `family-tree-gameplay-export.png` is the actual family export from that scene.
- `family-tree-demo.webp` is the older staged three-generation example.

The real gameplay runner sets up a disposable scene. It uses actual vanilla wolves and breeding, but the scene is automated. Captions and page copy must preserve that distinction. See `testing.md` and `scripts/build_media.py` for capture and packaging.

The owner authorized email outreach after the release work. As of October 2, 2026, Gmail Sent records confirm pitches to 70 distinct recipients, including the original 20 and 50 additional contacts. One bounced pitch was retried at an alternate published address, for 71 sends in total. Two original addresses returned permanent delivery failures; one has no verified replacement. Recipients include mod reviewers, survival creators, tutorial channels, and editorial teams. Messages request unpaid consideration. These sends are not evidence of downloads or promised coverage.

All confirmed sends have the Gmail label `minecraft`. A saved Gmail filter applies that label to future mail with "Family Tree" in the subject, including replies to these pitches. Private recipient addresses, source pages, exact messages, delivery failures, and send confirmations stay in the ignored `build/implementation/outreach/` folder. Check replies and delivery failures before deciding on further outreach. No community posts have been sent.

The October 2 page update adds a short creator demonstration, direct gameplay downloads, and modpack trial instructions to Modrinth. The [creator guide](creator-guide.md) explains the demonstration, media captions, and compatibility limits.

## CurseForge listing

The [Family Tree project](https://www.curseforge.com/minecraft/mc-mods/family-tree) is project `1718788`. The project and all eight 1.1.0 files were submitted on September 30 UTC. On October 2, the public listing and all eight files were available, with 98 total downloads. ModDex also has a [merged listing](https://moddex.gg/mod/familytree) for the Modrinth and CurseForge projects.

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

CurseForge publication is complete. The `curseforge-upload-kit.zip` on the GitHub release contains the eight verified jars, manifest, listing copy, a logo below the 100 KiB limit, media, changelog, and the existing project's URL. Do not upload duplicate files from the kit. Distribution uploads fetched seven more jars from Modrinth, and CurseForge CDN verification fetched eight jars. These are verification downloads, not evidence of new players. Keep CurseForge counts separate from the 10,000-download Modrinth target.

## Measurement decisions

At 30 days, compare the actual download rate with the remaining target. The audit's 180-day deadline requires roughly 53 additional downloads a day from the baseline. If the rate stays below that, broader distribution is still needed. More code alone cannot establish that the target will be reached.

Keep a dated record of releases, published page changes, download totals, and support issues. Public counters do not expose visitor conversion or unique active players. No in-game telemetry is used.
