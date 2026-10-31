"""Publish verified artifacts and page assets with the repository's local PAT."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
PROJECT = 'CVQKDAe7'
MIME = {'.png': 'image/png', '.webp': 'image/webp', '.gif': 'image/gif', '.jpg': 'image/jpeg'}


class Modrinth:
    def __init__(self):
        values = dict(line.split('=', 1) for line in Path(os.environ.get('FAMILYTREE_ENV', ROOT / '.env')).read_text().splitlines()
                      if '=' in line and not line.lstrip().startswith('#'))
        self.token = values['MODRINTH_TOKEN'].strip().strip('"').strip("'")

    def request(self, method, path, data=None, content_type='application/json'):
        if isinstance(data, dict):
            data = json.dumps(data).encode()
        request = urllib.request.Request('https://api.modrinth.com/v2/' + path, data=data, method=method,
            headers={'Authorization': self.token, 'User-Agent': 'kharitonov-egor/family_tree-publisher',
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
    parser.add_argument('--prune-gallery', action='store_true', help='With --media, delete gallery items missing from it')
    parser.add_argument('--update-page', action='store_true')
    parser.add_argument('--icon', type=Path, help='Replace the project icon with this PNG or WebP')
    parser.add_argument('--fix-archive', action='store_true')
    parser.add_argument('--quilt', action='store_true', help='Also list Fabric files for Quilt')
    args = parser.parse_args()
    api = Modrinth()
    user = api.request('GET', 'user')
    print('Authenticated publisher:', user['username'])
    if args.manifest:
        manifest = json.loads(args.manifest.read_text())
        versions = api.request('GET', f'project/{PROJECT}/version')
        published = []
        for item in manifest['files']:
            loaders = [item['loader']] + (['quilt'] if args.quilt and item['loader'] == 'fabric' else [])
            path = args.manifest.parent / item['filename']
            content = path.read_bytes()
            assert hashlib.sha512(content).hexdigest() == item['sha512'], 'Release file changed after verification'
            matches = [version for version in versions if version['version_number'] == item['version']]
            if matches:
                version = matches[0]
                assert any(file['hashes']['sha512'] == item['sha512'] for file in version['files'])
                assert sorted(version['game_versions']) == sorted(item.get('game_versions', [item['minecraft']]))
                assert sorted(version['loaders']) == sorted(loaders)
            else:
                metadata = {'project_id': PROJECT, 'version_number': item['version'],
                    'name': f'Family Tree {manifest["mod_version"]} for {item["minecraft"]} {item["loader"].capitalize()}',
                    'changelog': (ROOT / 'docs/releases' / (manifest['mod_version'] + '.md')).read_text(),
                    'dependencies': item['dependencies'], 'game_versions': item.get('game_versions', [item['minecraft']]),
                    'version_type': item['release_type'], 'loaders': loaders,
                    'featured': True, 'file_parts': ['file'], 'primary_file': 'file'}
                version = api.version(metadata, item['filename'], content)
            published.append({'version': item['version'], 'id': version['id'], 'sha512': item['sha512']})
            (args.manifest.parent / 'modrinth-published.json').write_text(json.dumps(published, indent=2) + '\n')
            print('Verified Modrinth version:', item['version'], version['id'])
    if args.media:
        sync_gallery(api, json.loads(args.media.read_text()), args.prune_gallery)
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
    if args.icon:
        api.request('PATCH', f'project/{PROJECT}/icon?ext={args.icon.suffix[1:]}', args.icon.read_bytes(),
                    'image/png' if args.icon.suffix == '.png' else 'image/webp')
        print('Updated icon:', api.request('GET', f'project/{PROJECT}')['icon_url'])
    if args.update_page:
        page = json.loads((ROOT / 'docs/modrinth_page.json').read_text())
        body = page_body(api, json.loads((ROOT / 'docs/media.json').read_text()))
        api.request('PATCH', f'project/{PROJECT}', {'title': page['title'], 'description': page['summary'],
            'categories': page['categories'], 'additional_categories': page['additional_categories'], 'body': body})
        project = api.request('GET', f'project/{PROJECT}')
        assert project['body'] == body and project['title'] == page['title'] and project['description'] == page['summary']
        assert sorted(project['categories']) == sorted(page['categories'])
        print('Verified project page:', 'https://modrinth.com/mod/familytree')


def gallery_url(item):
    return item.get('raw_url') or item['url']


def sync_gallery(api, media, prune):
    """Uploads docs/media.json items by title, applies their order and captions, and with prune deletes the rest."""
    titles = {item['title'] for item in media}
    if prune:
        for image in api.request('GET', f'project/{PROJECT}')['gallery']:
            if image['title'] not in titles:
                api.request('DELETE', f'project/{PROJECT}/gallery?url=' + urllib.parse.quote(image['url'], safe=''))
                print('Removed media:', image['title'])
    registry = {}
    for item in media:
        path = ROOT / item['file']
        content = path.read_bytes()
        assert len(content) <= 5 * 1024 * 1024, 'Modrinth gallery limit exceeded'
        existing = next((image for image in api.request('GET', f'project/{PROJECT}')['gallery']
                         if image['title'] == item['title']), None)
        if not existing:
            query = urllib.parse.urlencode({'ext': path.suffix[1:], 'featured': str(item['featured']).lower(),
                'title': item['title'], 'description': item['description'], 'ordering': item['ordering']})
            api.request('POST', f'project/{PROJECT}/gallery?{query}', content, MIME[path.suffix])
            existing = next(image for image in api.request('GET', f'project/{PROJECT}')['gallery']
                            if image['title'] == item['title'])
        else:
            query = urllib.parse.urlencode({'url': existing['url'], 'featured': str(item['featured']).lower(),
                'title': item['title'], 'description': item['description'], 'ordering': item['ordering']})
            api.request('PATCH', f'project/{PROJECT}/gallery?{query}')
        registry[item['key']] = gallery_url(existing)
        print('Published media:', item['title'])
    (ROOT / 'build/implementation').mkdir(parents=True, exist_ok=True)
    (ROOT / 'build/implementation/media-urls.json').write_text(json.dumps(registry, indent=2) + '\n')


def page_body(api, media):
    """Replaces media:<key> links in modrinth_description.md with the gallery's CDN URLs."""
    gallery = {image['title']: gallery_url(image) for image in api.request('GET', f'project/{PROJECT}')['gallery']}
    urls = {item['key']: gallery[item['title']] for item in media if item['title'] in gallery}
    body = (ROOT / 'modrinth_description.md').read_text()
    missing = sorted(set(re.findall(r'\(media:([a-z0-9-]+)\)', body)) - set(urls))
    assert not missing, f'Upload these gallery items first: {missing}'
    return re.sub(r'\(media:([a-z0-9-]+)\)', lambda match: '(' + urls[match.group(1)] + ')', body)

if __name__ == '__main__':
    main()
