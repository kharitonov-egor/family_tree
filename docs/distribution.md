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

Create a Minecraft mod project named "Family Tree for Minecraft pets", with the existing repository icon, MIT license, source and issue links, and the revised Modrinth description. Use Utility and Mobs categories where available. Upload one matching jar per Minecraft version and loader. Fabric files require Fabric API. The NeoForge 26.3 file is beta. The tested files and their hashes come from `build/releases/1.1.0/manifest.json`.

The author portal requires a signed-in account to create the project. Platform moderation may delay public availability after submission. Record the project URL and ID here once created; do not describe a pending submission as a live listing.

## Measurement decisions

At 30 days, compare the actual download rate with the remaining target. The audit's 180-day deadline requires roughly 53 additional downloads a day from the baseline. If the rate stays below that, broader distribution is still needed. More code alone cannot establish that the target will be reached.

Keep a dated record of releases, published page changes, download totals, and support issues. Public counters do not expose visitor conversion or unique active players. No in-game telemetry is used.
