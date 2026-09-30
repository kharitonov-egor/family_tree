"""Record public platform counts. No player data or platform token is needed."""
import csv
import datetime as dt
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
DIRECTORY = ROOT / 'docs/metrics'


def fetch(path):
    request = urllib.request.Request('https://api.modrinth.com/v2/' + path,
        headers={'User-Agent': 'kharitonov-egor/family_tree-download-counts/1.0'})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def main():
    project = fetch('project/CVQKDAe7')
    versions = fetch('project/CVQKDAe7/version')
    DIRECTORY.mkdir(parents=True, exist_ok=True)
    date = dt.datetime.now(dt.timezone.utc).date().isoformat()
    history_path = DIRECTORY / 'modrinth.csv'
    history = []
    if history_path.exists():
        with history_path.open(newline='') as source:
            history = list(csv.DictReader(source))
    history = [row for row in history if row['date_utc'] != date]
    previous = int(history[-1]['downloads']) if history else project['downloads']
    history.append({'date_utc': date, 'downloads': project['downloads'],
        'followers': project['followers'], 'change_since_previous_sample': project['downloads'] - previous})
    with history_path.open('w', newline='') as output:
        writer = csv.DictWriter(output, fieldnames=list(history[-1]))
        writer.writeheader()
        writer.writerows(history)
    snapshot = {'sampled_at_utc': dt.datetime.now(dt.timezone.utc).isoformat(),
        'project_id': project['id'], 'downloads': project['downloads'], 'followers': project['followers'],
        'versions': [{'id': item['id'], 'version': item['version_number'],
            'minecraft': item['game_versions'], 'loaders': item['loaders'], 'downloads': item['downloads']}
            for item in versions]}
    (DIRECTORY / (date + '.json')).write_text(json.dumps(snapshot, indent=2) + '\n')
    seven_days_ago = dt.date.fromisoformat(date) - dt.timedelta(days=7)
    older = [row for row in history if dt.date.fromisoformat(row['date_utc']) <= seven_days_ago]
    weekly = str(project['downloads'] - int(older[-1]['downloads'])) if older else 'Awaiting seven days of samples'
    remaining = max(0, 10_000 - project['downloads'])
    (DIRECTORY / 'README.md').write_text(
        '# Download progress\n\n'
        f'Latest sample, {date} UTC. Modrinth has {project["downloads"]:,} cumulative downloads '
        f'and {project["followers"]:,} followers. The 10,000-download target needs {remaining:,} more downloads.\n\n'
        f'Download change over seven days: {weekly}.\n\n'
        'These are platform downloads, including repeat downloads and upgrades. They do not count unique players. '
        'The daily workflow records public totals and per-version counts. No in-game telemetry is used. '
        'CurseForge needs a separate counter if a listing is added.\n')
    print(f'Modrinth: {project["downloads"]} downloads; {remaining} to target')


if __name__ == '__main__':
    main()
