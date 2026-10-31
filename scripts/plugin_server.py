"""Run a disposable Paper or Spigot server with the Family Tree plugin. See docs/plugin.md, "Testing"."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
CACHE = Path.home() / '.cache' / 'familytree-paper'


def java_for(minecraft):
    release = 17 if minecraft.startswith('1.20') else 21 if minecraft.startswith('1.') else 25
    override = os.environ.get(f'FT_JAVA_{release}')
    if override:
        return override
    candidate = Path(f'/usr/lib/jvm/java-{release}-openjdk-amd64/bin/java')
    return str(candidate) if candidate.exists() else 'java'


def paper_jar(minecraft):
    request = urllib.request.Request(f'https://fill.papermc.io/v3/projects/paper/versions/{minecraft}/builds/latest',
                                     headers={'User-Agent': 'kharitonov-egor/family_tree-tests'})
    with urllib.request.urlopen(request, timeout=60) as response:
        build = json.load(response)
    download = build['downloads']['server:default']
    target = CACHE / download['name']
    if not target.exists():
        CACHE.mkdir(parents=True, exist_ok=True)
        print('Downloading', download['name'])
        with urllib.request.urlopen(urllib.request.Request(download['url'], headers=request.headers), timeout=600) as response:
            target.write_bytes(response.read())
    return target


def purpur_jar(minecraft):
    target = CACHE / f'purpur-{minecraft}.jar'
    if not target.exists():
        CACHE.mkdir(parents=True, exist_ok=True)
        print('Downloading', target.name)
        request = urllib.request.Request(f'https://api.purpurmc.org/v2/purpur/{minecraft}/latest/download',
                                         headers={'User-Agent': 'kharitonov-egor/family_tree-tests'})
        with urllib.request.urlopen(request, timeout=600) as response:
            target.write_bytes(response.read())
    return target


def plugin_jars():
    libs = ROOT / 'paper' / 'build' / 'libs'
    jars = [path for path in libs.glob('familytree-*+plugin.jar')]
    if len(jars) != 1:
        raise SystemExit(f'Build the plugin first: ./gradlew -p paper build ({len(jars)} jars in {libs})')
    return jars[0], libs / 'familytree-smoke.jar'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--minecraft', default='26.3')
    parser.add_argument('--software', default='paper', choices=['paper', 'purpur', 'spigot'])
    parser.add_argument('--server-jar', type=Path, help='A Spigot jar from BuildTools, or another server jar')
    parser.add_argument('--dir', type=Path)
    parser.add_argument('--port', type=int, default=25711)
    parser.add_argument('--xmx', default='1536M')
    parser.add_argument('--smoke', action='store_true', help='Run the development smoke plugin and stop')
    parser.add_argument('--history', type=int, help='Smoke: require this many records at startup')
    parser.add_argument('--history-only', action='store_true', help='Smoke: only check the history, then stop')
    parser.add_argument('--seed', type=Path, help="Copy a mod world's familytree.dat into the new world's data folder")
    parser.add_argument('--fresh', action='store_true', help='Delete the world before starting')
    parser.add_argument('--timeout', type=int, default=900)
    args = parser.parse_args()

    directory = args.dir or ROOT / 'paper' / 'run' / f'{args.software}-{args.minecraft}'
    directory.mkdir(parents=True, exist_ok=True)
    if args.fresh:
        for name in ['world', 'world_nether', 'world_the_end']:
            shutil.rmtree(directory / name, ignore_errors=True)
    server = args.server_jar or (paper_jar(args.minecraft) if args.software == 'paper'
                                 else purpur_jar(args.minecraft) if args.software == 'purpur' else None)
    if server is None:
        raise SystemExit('Spigot needs --server-jar from BuildTools')
    (directory / 'eula.txt').write_text('eula=true\n')
    properties = directory / 'server.properties'
    values = {}
    if properties.exists():
        for line in properties.read_text().splitlines():
            if '=' in line and not line.startswith('#'):
                key, value = line.split('=', 1)
                values[key] = value
    values.update({'server-port': str(args.port), 'server-ip': '127.0.0.1', 'online-mode': 'false',
                   'spawn-protection': '0', 'view-distance': '4', 'simulation-distance': '4', 'difficulty': 'peaceful',
                   'level-type': values.get('level-type', 'minecraft\\:flat'), 'generate-structures': 'false',
                   'enable-command-block': 'true', 'white-list': 'false', 'enforce-whitelist': 'false'})
    properties.write_text(''.join(f'{key}={value}\n' for key, value in values.items()))
    plugins = directory / 'plugins'
    plugins.mkdir(exist_ok=True)
    for old in plugins.glob('familytree-*.jar'):
        old.unlink()
    plugin, smoke = plugin_jars()
    shutil.copy(plugin, plugins / plugin.name)
    if args.smoke:
        shutil.copy(smoke, plugins / smoke.name)
        result = plugins / 'FamilyTreeSmoke' / 'smoke-result.txt'
        result.unlink(missing_ok=True)
    if args.seed:
        data = directory / 'world' / 'data'
        data.mkdir(parents=True, exist_ok=True)
        shutil.copy(args.seed, data / 'familytree.dat')

    command = [java_for(args.minecraft), f'-Xmx{args.xmx}']
    if args.history is not None:
        command.append(f'-Dfamilytree.smoke.history={args.history}')
    if args.history_only:
        command.append('-Dfamilytree.smoke.historyOnly=true')
    command += ['-jar', str(Path(server).resolve()), '--nogui']
    print('Starting', ' '.join(command), 'in', directory, flush=True)
    if not args.smoke:
        fifo = directory / 'console.fifo'
        if not fifo.exists():
            os.mkfifo(fifo)
        console = os.open(fifo, os.O_RDWR)
        process = subprocess.Popen(command, cwd=directory, stdin=console)
        print(f'Console: echo "<command>" > {fifo}', flush=True)
        sys.exit(process.wait())
    with open(directory / 'smoke.log', 'w') as log:
        process = subprocess.Popen(command, cwd=directory, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT)
        deadline = time.time() + args.timeout
        while process.poll() is None and time.time() < deadline:
            time.sleep(2)
        if process.poll() is None:
            process.terminate()
            process.wait(60)
    if not result.exists():
        print((directory / 'smoke.log').read_text()[-4000:])
        raise SystemExit('No smoke result. See ' + str(directory / 'smoke.log'))
    text = result.read_text()
    print(text)
    sys.exit(0 if 'RESULT PASS' in text else 1)


if __name__ == '__main__':
    main()
