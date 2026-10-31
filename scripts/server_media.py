"""Drives the two-player server capture for the project page on Xvfb display :61.

The working directory is build/server-media/: a Fabric dedicated server in server/ and two client game directories,
alex/ (Fabric with Family Tree) and kai/ (unmodded). Both windows share one 3200x1000 Xvfb screen, Alex's at x=0 and
Kai's at x=1600, because a multi-screen Xvfb crashed when a client closed. See docs/testing.md#page-media.
"""
import argparse
import math
import os
from pathlib import Path
import re
import signal
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / 'build/server-media'
DISPLAY = ':61'
PORT = '25671'
CLIENTS = {'alex': ('fabric:1.21.1:0.19.3', 'Alex', 0), 'kai': ('1.21.1', 'Kai', 1)}


def xdo(*args):
    return subprocess.run(['xdotool', *args], env={'DISPLAY': DISPLAY}, capture_output=True, text=True).stdout.strip()


def window(slot):
    path = WORK / f'window{slot}.txt'
    return path.read_text().strip() if path.exists() else None


def focus(slot):
    xdo('windowfocus', window(slot))
    time.sleep(0.15)


def console(*lines):
    with open(WORK / 'server/in.fifo', 'w') as fifo:
        for line in lines:
            fifo.write(line + '\n')


def last_line(pattern, wait=0.6):
    time.sleep(wait)
    lines = [line for line in (WORK / 'server/console.log').read_text().splitlines() if re.search(pattern, line)]
    return lines[-1] if lines else ''


def server(action):
    directory = WORK / 'server'
    if action == 'start':
        fifo = directory / 'in.fifo'
        fifo.unlink(missing_ok=True)
        os.mkfifo(fifo)
        holder = subprocess.Popen(['sleep', 'infinity'], stdout=open(fifo, 'w'))
        (directory / 'holder.pid').write_text(str(holder.pid))
        java = subprocess.Popen(['/usr/lib/jvm/java-21-openjdk-amd64/bin/java', '-Xmx1536M', '-jar', 'fabric-server-launch.jar', 'nogui'],
                                cwd=directory, stdin=open(fifo), stdout=open(directory / 'console.log', 'w'), stderr=subprocess.STDOUT)
        (directory / 'server.pid').write_text(str(java.pid))
        for _ in range(120):
            if 'Done (' in (directory / 'console.log').read_text():
                return
            time.sleep(1)
        raise SystemExit('The server did not start')
    console('stop')
    pid = int((directory / 'server.pid').read_text())
    for _ in range(60):
        try:
            os.kill(pid, 0)
        except ProcessLookupError:
            break
        time.sleep(1)
    os.kill(int((directory / 'holder.pid').read_text()), signal.SIGTERM)


def client(name):
    """Starts a client on Xvfb only. The private runtime directory hides the desktop's Wayland socket, so GLFW
    cannot open the window on the real display if Xvfb is missing."""
    version, user, slot = CLIENTS[name]
    runtime = WORK / 'runtime'
    runtime.mkdir(mode=0o700, exist_ok=True)
    env = {key: value for key, value in os.environ.items() if key != 'WAYLAND_DISPLAY'}
    env.update(DISPLAY=DISPLAY, XDG_SESSION_TYPE='x11', XDG_RUNTIME_DIR=str(runtime), SDL_VIDEODRIVER='x11')
    known = set(xdo('search', '--onlyvisible', '--name', 'Minecraft').split())
    subprocess.Popen(['portablemc', '--main-dir', str(Path.home() / '.cache/pmc'), 'start', version, '--mc-dir', str(WORK / name),
                      '-u', user, '--resolution', '1600x1000', '--join-server', '127.0.0.1', '--join-server-port', PORT,
                      '--jvm-arg=-Xmx1536M'], env=env, stdout=open(WORK / f'{name}.log', 'w'), stderr=subprocess.STDOUT)
    for _ in range(150):
        ids = [wid for wid in xdo('search', '--onlyvisible', '--name', 'Minecraft').split() if wid not in known]
        if ids:
            xdo('windowmove', ids[-1], str(slot * 1600), '0')
            (WORK / f'window{slot}.txt').write_text(ids[-1])
            return
        time.sleep(2)
    raise SystemExit(f'No window for {name}')


def face(player, selector, distance, height=0.35):
    """Stands the player south of an entity, looking at it, so a click lands on it."""
    console(f'data get entity {selector} Pos')
    x, y, z = (float(value) for value in re.findall(r'(-?[\d.]+)d', last_line('has the following entity data: \\['))[:3])
    pitch = -math.degrees(math.atan2(height - 1.62, distance))
    console(f'tp {player} {x:.2f} {y:.2f} {z - distance:.2f} 0 {pitch:.1f}')
    time.sleep(1)


def tame(slot, wolf):
    """Right-clicks with bones until the console reports an owner, as a player taming a wolf would."""
    for attempt in range(1, 13):
        click(slot, 800, 500, '3')
        time.sleep(0.8)
        console(f'data get entity @e[type=minecraft:wolf,name={wolf},limit=1] Owner')
        if f'{wolf} has the following entity data' in last_line(wolf):
            return attempt
    raise SystemExit(f'{wolf} was not tamed')


def click(slot, x, y, button='1'):
    focus(slot)
    xdo('mousemove', str(slot * 1600 + int(x)), str(y))
    time.sleep(0.25)
    xdo('click', button)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['server', 'client', 'console', 'key', 'chat', 'click', 'move', 'shot', 'face', 'tame'])
    parser.add_argument('args', nargs='*')
    args = parser.parse_args()
    a = args.args
    if args.command == 'server': server(a[0])
    elif args.command == 'client': client(a[0])
    elif args.command == 'console': console(*a)
    elif args.command == 'key':
        focus(int(a[0]))
        for key in a[1:]:
            xdo('key', key)
            time.sleep(0.1)
    elif args.command == 'chat':
        focus(int(a[0]))
        xdo('key', 't'); time.sleep(0.4)
        xdo('type', '--delay', '40', a[1]); time.sleep(0.2)
        xdo('key', 'Return')
    elif args.command == 'click': click(int(a[0]), a[1], a[2], a[3] if len(a) > 3 else '1')
    elif args.command == 'move': xdo('mousemove', str(int(a[0]) * 1600 + int(a[1])), a[2])
    elif args.command == 'shot':
        subprocess.run(['import', '-display', DISPLAY, '-window', 'root', '-crop', f'1600x1000+{int(a[0]) * 1600}+0', '+repage', a[1]], check=True)
    elif args.command == 'face': face(a[0], a[1], float(a[2]), float(a[3]) if len(a) > 3 else 0.35)
    elif args.command == 'tame': print(f'{a[1]} tamed after {tame(int(a[0]), a[1])} clicks')


if __name__ == '__main__':
    main()
