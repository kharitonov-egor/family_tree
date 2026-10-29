"""Verify and copy built jars. Publish these copies without rebuilding them."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TARGETS = {'fabric': ['1.20.1', '1.21.1', '1.21.4', '1.21.5', '1.21.8', '1.21.10', '1.21.11',
                      '26.1.1', '26.1.2', '26.2', '26.3'],
           'neoforge': ['1.21.1', '1.21.4', '1.21.5', '1.21.8', '1.21.10', '1.21.11', '26.2', '26.3'],
           'forge': ['1.20.1']}
NEOFORGE = {'1.21.1': '21.1.252', '1.21.4': '21.4.161', '1.21.5': '21.5.101', '1.21.8': '21.8.57',
            '1.21.10': '21.10.64', '1.21.11': '21.11.48', '26.2': '26.2.0.88', '26.3': '26.3.0.37-beta'}
FORGE = {'1.20.1': '47.1.0'}
# Earlier patches the Fabric 1.21.8 and 1.21.10 jars ran on. build.gradle declares the same ranges.
ALSO_RUNS_ON = {'1.21.8': ['1.21.6', '1.21.7'], '1.21.10': ['1.21.9']}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    properties = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text().splitlines()
                      if '=' in line and not line.startswith('#'))
    version = properties['mod_version']
    output = args.output or ROOT / 'build/releases' / version
    output.mkdir(parents=True, exist_ok=True)
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    if subprocess.check_output(['git', 'status', '--porcelain', '--untracked-files=no'], cwd=ROOT, text=True).strip():
        raise SystemExit('Commit the tested source before generating the release manifest.')
    files = []
    for loader, targets in TARGETS.items():
        for mc in targets:
            release_version = f'{version}+{mc}' + ('' if loader == 'fabric' else f'-{loader}')
            base = ROOT / ('versions' if loader == 'fabric' else f'{loader}/build') / mc
            folder = base / ('build/libs' if loader == 'fabric' else 'libs')
            path = folder / f'familytree-{release_version}.jar'
            with zipfile.ZipFile(path) as jar:
                names = jar.namelist()
                assert 'LICENSE_familytree' in names, f'{path}: missing MIT license'
                assert 'assets/familytree/icon.png' in names, f'{path}: missing icon'
                assert not any('/demo/' in name or '/smoke/' in name for name in names), f'{path}: development classes'
                assert jar.read('LICENSE_familytree') == (ROOT / 'LICENSE').read_bytes()
                game_versions = (ALSO_RUNS_ON.get(mc, []) if loader == 'fabric' else []) + [mc]
                if loader == 'fabric':
                    metadata = json.loads(jar.read('fabric.mod.json'))
                    assert metadata['version'] == release_version
                    predicate = '=' + mc if len(game_versions) == 1 else f'>={game_versions[0]} <={mc}'
                    assert metadata['depends']['minecraft'] == predicate
                    dependencies = [{'project_id': 'P7dR8mSH', 'dependency_type': 'required'}]
                    minimum_loader = metadata['depends']['fabricloader']
                elif loader == 'neoforge':
                    metadata = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
                    assert metadata['mods'][0]['version'] == release_version
                    assert metadata['mods'][0].get('logoFile', metadata['mods'][0].get('iconFile')) == 'assets/familytree/icon.png'
                    assert any(item['modId'] == 'minecraft' and item['versionRange'] == '[' + mc + ']'
                               for item in metadata['dependencies']['familytree'])
                    dependencies = []
                    minimum_loader = NEOFORGE[mc]
                else:
                    metadata = tomllib.loads(jar.read('META-INF/mods.toml').decode())
                    assert metadata['mods'][0]['version'] == release_version
                    assert metadata['mods'][0]['logoFile'] == 'assets/familytree/icon.png'
                    assert any(item['modId'] == 'minecraft' and item['versionRange'] == '[' + mc + ']'
                               for item in metadata['dependencies']['familytree'])
                    assert any(item['modId'] == 'forge' and item['versionRange'].startswith('[' + FORGE[mc] + ',')
                               for item in metadata['dependencies']['familytree'])
                    assert 'MixinConfigs: familytree.mixins.json' in jar.read('META-INF/MANIFEST.MF').decode()
                    assert json.loads(jar.read('familytree.mixins.json'))['refmap'] in names, f'{path}: missing refmap'
                    assert 'pack.mcmeta' in names, f'{path}: missing pack.mcmeta'
                    dependencies = []
                    minimum_loader = FORGE[mc]
                java = 17 if mc == '1.20.1' else 21 if mc.startswith('1.') else 25
                expected_major = java + 44
                for name in names:
                    if name.endswith('.class') and name.startswith('com/egakh/familytree/'):
                        assert int.from_bytes(jar.read(name)[6:8], 'big') == expected_major, f'{path}: wrong Java target'
            destination = output / path.name
            shutil.copyfile(path, destination)
            files.append({'filename': path.name, 'version': release_version, 'minecraft': mc,
                'game_versions': game_versions, 'loader': loader,
                'minimum_loader': minimum_loader, 'java': java, 'dependencies': dependencies,
                'release_type': 'beta' if loader == 'neoforge' and mc == '26.3' else 'release',
                'sha512': hashlib.sha512(destination.read_bytes()).hexdigest(), 'size': destination.stat().st_size})
            source = folder / f'familytree-{release_version}-sources.jar'
            shutil.copyfile(source, output / source.name)
    manifest = {'mod_version': version, 'commit': commit, 'license': 'MIT', 'files': files}
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(f'Verified {len(files)} release jars at {commit}. Output: {output}')


if __name__ == '__main__':
    main()
