# Distribution and download target

The target is 10,000 cumulative Modrinth downloads. The audit baseline was 480. Platform totals include repeat downloads and upgrades. On October 2, Modrinth had 566 downloads and CurseForge had 98. Keep the platform totals separate.

The 1.1.0 work addresses release reliability, current game support, known-family onboarding, page clarity, and PNG sharing. Public daily counters live in `docs/metrics/`; a scheduled GitHub workflow updates them. Review the seven-day change after releases and page changes. The first dated page experiment uses the promise "Keep your first wolf in the family tree after it's gone" and shows an actual exported family before explaining controls.

## Available media

The current page media are in `docs/media/`, listed with their captions in `docs/media.json`, and the launch clips are in `docs/launch/`. [testing.md](testing.md#page-media) describes how they were captured. The horse, sheep, and panda scenes are automated with vanilla AI breeding; the server scene used two real clients. Captions and page copy must preserve that distinction. The older wolf clips and images stay in `docs/` because earlier pages and the v1.1.0 release link to them.

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

## 1.2.0 publication

On October 29, 2026 UTC, commit `94ea9e7` was tagged `v1.2.0`. The [GitHub release](https://github.com/kharitonov-egor/family_tree/releases/tag/v1.2.0) has twelve jars, their source jars, and the manifest. Modrinth had 1,023 downloads when it went out.

Modrinth has twelve matching versions. The seven Fabric versions also list Quilt, after a Quilt Loader 0.30.1 server started on every Fabric target and 1.21.1 and 26.3 clients joined it and opened the browser. The page summary now mentions horse breeding stats, and the gallery has the horse export and tree images. Version metadata and SHA-512 hashes were checked through the API without downloading the files.

CurseForge received the same twelve files through the upload API, file IDs 9365774 to 9365785. Each has its Minecraft version, loader, Java version, Client, and Server tags. Fabric files also carry Quilt and require Fabric API. They go public after CurseForge's review. The upload API cannot edit the project description, which still describes 1.1.0. `scripts/publish_curseforge.py` reads `CURSEFORGE_TOKEN` from the ignored `.env`.

## 1.3.0 publication

On October 31, 2026 UTC, commit `976d03a` was tagged `v1.3.0`. The [GitHub release](https://github.com/kharitonov-egor/family_tree/releases/tag/v1.3.0) has twenty jars, their source jars, and the manifest. Modrinth had 1,113 downloads and 11 followers the day before.

The release adds Fabric and NeoForge builds for 1.21.4, 1.21.5, 1.21.8, and 1.21.10. It also fixes left clicks on Minecraft 26.3, which numbers mouse buttons from 1 instead of 0. Since 1.1.0, clicking a browser row or tree card there did nothing, and dragging didn't pan. A real 26.3 client clicked a browser row, opened the tree, and panned it with the 1.3.0 jar.

Modrinth has twenty matching versions, checked by version ID for hash, game versions, loaders, and release type without downloading files. The eleven Fabric versions also list Quilt, after a Quilt Loader 0.30.1 server started with Family Tree on each of them. The page body describes 1.3.0, and the gallery adds the sheep export and the blue axolotl images.

CurseForge received the same twenty files through the upload API, file IDs 9380487 to 9380506, with Quilt on the Fabric files. They go public after CurseForge's review. The project description there still describes 1.1.0.

The Send feedback form works from this release on. The Vercel endpoint has its Resend key and sends from `feedback@hawksconnect.com`, and a test message reached the developer's Gmail.

## Page refresh after 1.3.0

The page used to lead with "Keep your first wolf in the family tree after it's gone", which sold the mod as a memorial. The refresh leads with seeing who bred whom: lineages across generations, foal stats against both parents, inherited colors, and servers. Death history stays on the page as a feature.

Modrinth search ranks matches in the title above matches in the summary, then sorts by downloads. On October 31 Family Tree had 1,133 downloads. A public search API check that day found:

| Query | Results | Family Tree before | Strongest results |
| --- | --- | --- | --- |
| family tree | 1 | 1st | none |
| horse breeding | 3 | not listed | Horse Breeding Fix, 68k |
| animal breeding | 14 | not listed | none above 9k |
| breeding stats | 3 | not listed | none above 1k |
| horse stats | 6 | not listed | Horse Stats Vanilla, 393k |
| breeding | 78 | not listed | Just Enough Breeding, 8.6M |
| lineage | 1 | not listed | a bee mod, 158 |
| pedigree | 0 | | |
| genetics | 19 | not listed | Realistic Horse Genetics, 114k |
| pets | 169 | 46th | Adorable Hamster Pets, 5.7M |
| animals | 250 | not listed | Respawning Animals, 674k |

The new title is "Family Tree: Animal Breeding & Lineage". "Breeding" in the title should place Family Tree about 12th of 78 for "breeding", near the top for "animal breeding", and first for "lineage". The summary carries "pets", "horses", "farm animals", "horse breeding stats", "foal", and "servers", which brings "horse breeding", "horse stats", and "breeding stats" onto the first page. "Pets" moved from the title to the summary; at 46th it was on the third page anyway. Modrinth's rules ask for titles that are the project's name "without any other unnecessary filler data" and enforce that loosely, so the title stays short. Categories stay Mobs and Utility, with Management added as an additional category, as Just Enough Breeding uses it.

The gallery was rebuilt from `docs/media.json`: 15 items led by the four-generation tree, with the 1.0.0, 1.0.1, 1.1.0, and "Previous release" items removed. The icon stays: a pixel-art replacement drawn on a 48-pixel grid read more clearly as two parents and a foal at 48 px, but it looked flatter and less like a tree, so it did not clearly beat the current one.

CurseForge's description can't be edited through the upload API, and its site blocks automated browsers. To update it by hand, paste `docs/curseforge-description.md` into the Markdown editor, or `docs/curseforge-description.html` into the WYSIWYG one. Its images load from GitHub, with GIFs for animations. Then upload the gallery items in `docs/media.json` with their captions, and remove the 1.1.0 images.

## Measurement decisions

At 30 days, compare the actual download rate with the remaining target. The audit's 180-day deadline requires roughly 53 additional downloads a day from the baseline. If the rate stays below that, broader distribution is still needed. More code alone cannot establish that the target will be reached.

Keep a dated record of releases, published page changes, download totals, and support issues. Public counters do not expose visitor conversion or unique active players. No in-game telemetry is used. The optional Send feedback form sends only what a player writes, when they press Send.
