"""Report whether a Minecraft release is out and whether Fabric API, NeoForge, and the optional mods support it.

Exit codes, for scheduled checks:
  0  the release is out and Fabric API has a build for it, so the Fabric port can start
  1  the release is out, but Fabric API has no build for it yet
  2  the release is not out yet
  3  a lookup failed
"""
import argparse
import json
import sys
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ElementTree

MOJANG = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
NEOFORGE = 'https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml'
MODRINTH = 'https://api.modrinth.com/v2/project/{}/version?game_versions={}'
OPTIONAL = {'jade': 'Jade', 'wthit': 'WTHIT', 'modmenu': 'Mod Menu', 'journeymap': 'JourneyMap',
            'xaeros-minimap': "Xaero's Minimap"}


def fetch(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'familytree-release-check (github.com/kharitonov-egor/family_tree)'})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()


def modrinth_versions(project, game_version, loader=None):
    url = MODRINTH.format(project, urllib.parse.quote(json.dumps([game_version])))
    if loader:
        url += '&loaders=' + urllib.parse.quote(json.dumps([loader]))
    return [version['version_number'] for version in json.loads(fetch(url))]


def neoforge_versions(minecraft):
    # NeoForge numbers builds after the game version: 26.4.0.12, 26.4.0.3-beta, 26.1.0.0-alpha.1+snapshot-1.
    root = ElementTree.fromstring(fetch(NEOFORGE))
    versions = [node.text for node in root.iter('version')]
    prefix = minecraft + ('.' if minecraft.count('.') == 1 else '')
    return [version for version in versions if version.startswith(prefix)]


def check(minecraft):
    manifest = json.loads(fetch(MOJANG))
    ids = {version['id']: version for version in manifest['versions']}
    released = ids.get(minecraft, {}).get('type') == 'release'
    previews = [version['id'] for version in manifest['versions']
                if version['id'].startswith(minecraft + '-') and version['type'] == 'snapshot']
    latest_preview = previews[0] if previews else None
    report = {'minecraft': minecraft, 'released': released,
              'release_time': ids[minecraft]['releaseTime'] if released else None,
              'latest_preview': latest_preview,
              'fabric_api': modrinth_versions('P7dR8mSH', minecraft) if released else [],
              'fabric_api_for_preview': modrinth_versions('P7dR8mSH', latest_preview) if latest_preview else [],
              'neoforge': neoforge_versions(minecraft),
              'optional': {}}
    if released:
        for project, name in OPTIONAL.items():
            report['optional'][name] = {loader: modrinth_versions(project, minecraft, loader)[:1]
                                        for loader in ('fabric', 'neoforge')}
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('minecraft', nargs='?', default='26.4')
    parser.add_argument('--json', action='store_true', help='Print the report as JSON')
    args = parser.parse_args()
    try:
        report = check(args.minecraft)
    except Exception as error:
        print(f'Lookup failed: {error}', file=sys.stderr)
        return 3
    if args.json:
        print(json.dumps(report, indent=2))
    else:
        mc = report['minecraft']
        if report['released']:
            print(f'Minecraft {mc} was released at {report["release_time"]}.')
        else:
            print(f'Minecraft {mc} is not released. Latest preview: {report["latest_preview"] or "none"}.')
            if report['latest_preview']:
                found = report['fabric_api_for_preview']
                print(f'Fabric API for {report["latest_preview"]}: {found[0] if found else "none yet"}')
        if report['released']:
            print(f'Fabric API for {mc}: {report["fabric_api"][0] if report["fabric_api"] else "none yet"}')
        stable = [version for version in report['neoforge'] if '-' not in version]
        print(f'NeoForge for {mc}: ' + (f'{report["neoforge"][-1]} (newest), {stable[-1] if stable else "no non-beta build"} (newest non-beta)'
                                         if report['neoforge'] else 'none yet'))
        for name, loaders in report['optional'].items():
            listed = ', '.join(f'{loader} {found[0]}' for loader, found in loaders.items() if found)
            print(f'{name}: {listed or "none yet"}')
    if not report['released']:
        return 2
    return 0 if report['fabric_api'] else 1


if __name__ == '__main__':
    sys.exit(main())
