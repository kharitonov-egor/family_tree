"""Publish verified artifacts and page assets with the repository's local PAT."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
PROJECT = 'CVQKDAe7'


class Modrinth:
    def __init__(self):
        values = dict(line.split('=', 1) for line in (ROOT / '.env').read_text().splitlines()
                      if '=' in line and not line.lstrip().startswith('#'))
        self.token = values['MODRINTH_TOKEN'].strip().strip('"').strip("'")

    def request(self, method, path, data=None, content_type='application/json'):
        if isinstance(data, dict):
            data = json.dumps(data).encode()
        request = urllib.request.Request('https://api.modrinth.com/v2/' + path, data=data, method=method,
            headers={'Authorization': self.token, 'User-Agent': 'kharitonov-egor/family_tree-publisher/1.1.0',
                     'Content-Type': content_type})
        try:
            with urllib.request.urlopen(request, timeout=90) as response:
                body = response.read()
                return json.loads(body) if body else None
        except urllib.error.HTTPError as error:
            raise RuntimeError(f'Modrinth {method} {path.split("?")[0]} failed with HTTP {error.code}: '
                               + error.read().decode()[:1000]) from None

    def version(self, metadata, filename, content):
        boundary = 'familytree-' + uuid.uuid4().hex
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\n'
                'Content-Type: application/json\r\n\r\n').encode() + json.dumps(metadata).encode() + b'\r\n'
        body += (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
                 'Content-Type: application/java-archive\r\n\r\n').encode() + content
        body += f'\r\n--{boundary}--\r\n'.encode()
        return self.request('POST', 'version', body, f'multipart/form-data; boundary={boundary}')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--manifest', type=Path)
    parser.add_argument('--media', type=Path)
    parser.add_argument('--update-page', action='store_true')
    parser.add_argument('--fix-archive', action='store_true')
    args = parser.parse_args()
    api = Modrinth()
    user = api.request('GET', 'user')
    print('Authenticated publisher:', user['username'])
    if args.manifest:
        manifest = json.loads(args.manifest.read_text())
        versions = api.request('GET', f'project/{PROJECT}/version')
        published = []
        for item in manifest['files']:
            path = args.manifest.parent / item['filename']
            content = path.read_bytes()
            assert hashlib.sha512(content).hexdigest() == item['sha512'], 'Release file changed after verification'
            matches = [version for version in versions if version['version_number'] == item['version']]
            if matches:
                version = matches[0]
                assert any(file['hashes']['sha512'] == item['sha512'] for file in version['files'])
                assert version['game_versions'] == [item['minecraft']] and version['loaders'] == [item['loader']]
            else:
                metadata = {'project_id': PROJECT, 'version_number': item['version'],
                    'name': f'Family Tree {manifest["mod_version"]} for {item["minecraft"]} {item["loader"].capitalize()}',
                    'changelog': (ROOT / 'docs/releases' / (manifest['mod_version'] + '.md')).read_text(),
                    'dependencies': item['dependencies'], 'game_versions': [item['minecraft']],
                    'version_type': item['release_type'], 'loaders': [item['loader']],
                    'featured': True, 'file_parts': ['file'], 'primary_file': 'file'}
                version = api.version(metadata, item['filename'], content)
            published.append({'version': item['version'], 'id': version['id'], 'sha512': item['sha512']})
            (args.manifest.parent / 'modrinth-published.json').write_text(json.dumps(published, indent=2) + '\n')
            print('Verified Modrinth version:', item['version'], version['id'])
    if args.media:
        media = json.loads(args.media.read_text())
        current_titles = {item['title'] for item in media}
        project = api.request('GET', f'project/{PROJECT}')
        for index, item in enumerate(project['gallery']):
            if item['title'] in current_titles:
                continue
            api.request('PATCH', f'project/{PROJECT}/gallery?url=' + urllib.parse.quote(item['url'], safe=''), {
                'featured': False, 'ordering': index + 20,
                'title': item['title'] if item['title'].startswith('Previous release:') else 'Previous release: ' + item['title']})
        registry = {}
        for item in media:
            path = ROOT / item['file']
            content = path.read_bytes()
            assert len(content) <= 5 * 1024 * 1024, 'Modrinth gallery limit exceeded'
            project = api.request('GET', f'project/{PROJECT}')
            existing = next((image for image in project['gallery'] if image['title'] == item['title']), None)
            if not existing:
                query = urllib.parse.urlencode({'ext': path.suffix[1:], 'featured': str(item['featured']).lower(),
                    'title': item['title'], 'description': item['description'], 'ordering': item['ordering']})
                api.request('POST', f'project/{PROJECT}/gallery?{query}', content,
                            'image/png' if path.suffix == '.png' else 'image/webp')
                project = api.request('GET', f'project/{PROJECT}')
                existing = next(image for image in project['gallery'] if image['title'] == item['title'])
            else:
                api.request('PATCH', f'project/{PROJECT}/gallery?url=' + urllib.parse.quote(existing['url'], safe=''), {
                    'featured': item['featured'], 'ordering': item['ordering']})
            registry[item['key']] = existing.get('raw_url') or existing['url']
            print('Published media:', item['title'])
        (ROOT / 'build/implementation/media-urls.json').write_text(json.dumps(registry, indent=2) + '\n')
    if args.fix_archive:
        for version_id in ['9j4QmEV4', 'tJWEkJby', 'jKecFBLl']:
            version = api.request('GET', f'version/{version_id}')
            dependencies = [item for item in version['dependencies'] if item.get('project_id') != 'P7dR8mSH']
            dependencies.append({'project_id': 'P7dR8mSH', 'dependency_type': 'required'})
            patch = {'dependencies': dependencies}
            if version_id == '9j4QmEV4':
                patch['game_versions'] = ['26.1.1']
                note = 'Archive note: the primary file is for Minecraft 26.1.1. The secondary +26.2 file is retained for manual historical downloads. For current Minecraft 26.2 installations, use the separate matching 1.1.0 release. Both old files require Fabric API.\n\n'
                patch['changelog'] = note + version['changelog'].removeprefix(note)
            api.request('PATCH', f'version/{version_id}', patch)
            print('Corrected archived metadata:', version_id)
    if args.update_page:
        api.request('PATCH', f'project/{PROJECT}', {'title': 'Family Tree for Minecraft pets',
            'description': "Keep your pets' parents, descendants, and death history. Find their last known location and export a family tree as a PNG.",
            'body': (ROOT / 'modrinth_description.md').read_text()})
        project = api.request('GET', f'project/{PROJECT}')
        assert project['body'] == (ROOT / 'modrinth_description.md').read_text()
        print('Verified project page:', 'https://modrinth.com/mod/familytree')


if __name__ == '__main__':
    main()
