"""Upload verified release jars to CurseForge with the local upload token."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
PROJECT = 1718788
API = 'https://minecraft.curseforge.com/api/'
LOADERS = {'fabric': 'Fabric', 'neoforge': 'NeoForge', 'forge': 'Forge'}


def java_for(minecraft):
    if minecraft.startswith('1.20'):
        return 'Java 17'
    return 'Java 21' if minecraft.startswith('1.21') else 'Java 25'


class CurseForge:
    def __init__(self):
        values = dict(line.split('=', 1) for line in (ROOT / '.env').read_text().splitlines()
                      if '=' in line and not line.lstrip().startswith('#'))
        self.token = values['CURSEFORGE_TOKEN'].strip().strip('"').strip("'")

    def request(self, method, path, data=None, content_type='application/json'):
        request = urllib.request.Request(API + path, data=data, method=method,
            headers={'X-Api-Token': self.token, 'User-Agent': 'kharitonov-egor/family_tree-publisher',
                     'Content-Type': content_type})
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                return json.loads(response.read())
        except urllib.error.HTTPError as error:
            raise RuntimeError(f'CurseForge {method} {path} failed with HTTP {error.code}: '
                               + error.read().decode()[:1000]) from None

    def version_ids(self):
        types = {item['id']: item['slug'] for item in self.request('GET', 'game/version-types')}
        ids = {}
        for item in self.request('GET', 'game/versions'):
            slug = types.get(item['gameVersionTypeID'], '')
            if slug.startswith('minecraft-') or slug in ('modloader', 'java', 'environment'):
                ids[(slug, item['name'])] = item['id']
        return ids

    def upload(self, metadata, filename, content):
        boundary = 'familytree-' + uuid.uuid4().hex
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="metadata"\r\n'
                'Content-Type: application/json\r\n\r\n').encode() + json.dumps(metadata).encode() + b'\r\n'
        body += (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
                 'Content-Type: application/java-archive\r\n\r\n').encode() + content
        body += f'\r\n--{boundary}--\r\n'.encode()
        return self.request('POST', f'projects/{PROJECT}/upload-file', body,
                            f'multipart/form-data; boundary={boundary}')


def game_version(ids, name):
    matches = [value for (slug, version), value in ids.items() if version == name and slug.startswith('minecraft-')]
    assert len(matches) == 1, f'Expected one Minecraft version named {name}, found {matches}'
    return matches[0]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--quilt', action='store_true', help='Also tag Fabric files as Quilt-compatible')
    args = parser.parse_args()
    api = CurseForge()
    ids = api.version_ids()
    manifest = json.loads(args.manifest.read_text())
    changelog = (ROOT / 'docs/releases' / (manifest['mod_version'] + '.md')).read_text()
    record = args.manifest.parent / 'curseforge-published.json'
    published = json.loads(record.read_text()) if record.exists() else []
    done = {item['version'] for item in published}
    for item in manifest['files']:
        if item['version'] in done:
            print('Already uploaded:', item['version'])
            continue
        path = args.manifest.parent / item['filename']
        content = path.read_bytes()
        assert hashlib.sha512(content).hexdigest() == item['sha512'], 'Release file changed after verification'
        loaders = [LOADERS[item['loader']]] + (['Quilt'] if args.quilt and item['loader'] == 'fabric' else [])
        versions = [game_version(ids, item['minecraft']), ids[('java', java_for(item['minecraft']))],
                    ids[('environment', 'Client')], ids[('environment', 'Server')]]
        versions += [ids[('modloader', loader)] for loader in loaders]
        metadata = {'changelog': changelog, 'changelogType': 'markdown',
                    'displayName': f'Family Tree {manifest["mod_version"]} for {item["minecraft"]} '
                                   + LOADERS[item['loader']],
                    'gameVersions': versions, 'releaseType': item['release_type']}
        if item['loader'] == 'fabric':
            metadata['relations'] = {'projects': [{'slug': 'fabric-api', 'type': 'requiredDependency'}]}
        result = api.upload(metadata, item['filename'], content)
        published.append({'version': item['version'], 'id': result['id'], 'sha512': item['sha512']})
        record.write_text(json.dumps(published, indent=2) + '\n')
        print('Uploaded to CurseForge:', item['version'], result['id'])


if __name__ == '__main__':
    main()
