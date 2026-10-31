"""Writes the CurseForge description from modrinth_description.md, with GitHub-hosted images and GIF animations."""
import json
from pathlib import Path
import re
import markdown

ROOT = Path(__file__).resolve().parents[1]
RAW = 'https://raw.githubusercontent.com/kharitonov-egor/family_tree/main/'


def main():
    media = {item['key']: item['file'] for item in json.loads((ROOT / 'docs/media.json').read_text()) if 'hold_until' not in item}
    body = (ROOT / 'modrinth_description.md').read_text()

    def url(match):
        path = media[match.group(1)]
        gif = Path(path).with_suffix('.gif')
        return '(' + RAW + (str(gif) if (ROOT / gif).exists() else path) + ')'

    body = re.sub(r'\(media:([a-z0-9-]+)\)', url, body)
    body = body.replace('(#choose-the-matching-file)', '(https://modrinth.com/mod/familytree#choose-the-matching-file)')
    (ROOT / 'docs/curseforge-description.md').write_text(body)
    (ROOT / 'docs/curseforge-description.html').write_text(markdown.markdown(body, extensions=['tables']) + '\n')
    print('Wrote docs/curseforge-description.md and .html')


if __name__ == '__main__':
    main()
