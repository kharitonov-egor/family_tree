"""Play Family Tree's plugin checklist with an unmodded client, on a Paper or Spigot server.

Starts a disposable server with scripts/plugin_server.py, launches a vanilla client through portablemc on an Xvfb
display, and drives it with xdotool: taming and breeding by hand, a name tag, the chest menu's search, rename, and
Add parents, every input that could move an item, a pet owned by someone else, and a restart. Results come from the
server console and the saved familytree.dat. Screenshots land in the run directory's shots/ folder for review.

    python scripts/plugin_client_check.py --minecraft 1.21.11 --display :66
"""
import argparse
import gzip
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
GUI_SCALE = 2
WIDTH, HEIGHT = 1280, 720


def version_tuple(minecraft):
    return tuple(int(part) for part in minecraft.split('.'))


class Session:
    def __init__(self, args):
        self.args = args
        self.minecraft = args.minecraft
        self.run = args.dir or ROOT / 'paper' / 'run' / f'client-check-{args.software}-{args.minecraft}'
        self.game = self.run / 'game'
        self.shots = self.run / 'shots'
        self.server_dir = self.run / 'server'
        self.fifo = self.server_dir / 'console.fifo'
        self.log_path = self.run / 'server.log'
        self.client_log = self.run / 'client.log'
        self.results = []
        self.chat_offset = 0
        self.server = None
        self.client = None
        self.top = 0

    # Processes

    def start_server(self, fresh):
        command = [sys.executable, str(ROOT / 'scripts' / 'plugin_server.py'), '--minecraft', self.minecraft,
                   '--software', self.args.software, '--dir', str(self.server_dir), '--port', str(self.args.port)]
        if self.args.server_jar:
            command += ['--server-jar', str(self.args.server_jar)]
        if fresh:
            command.append('--fresh')
        self.log_offset = self.log_path.stat().st_size if self.log_path.exists() else 0
        self.server = subprocess.Popen(command, stdout=open(self.log_path, 'a'), stderr=subprocess.STDOUT)
        self.wait_log('Done (', 600)

    def stop_server(self):
        self.console('stop')
        self.server.wait(180)

    def start_client(self):
        self.game.mkdir(parents=True, exist_ok=True)
        (self.game / 'options.txt').write_text('onboardAccessibility:false\nguiScale:2\nlang:en_us\npauseOnLostFocus:false\n'
                                               'renderDistance:4\nsimulationDistance:5\nmaxFps:30\ntutorialStep:none\n'
                                               'skipMultiplayerWarning:true\njoinedFirstServer:true\nsoundCategory_master:0.0\n')
        env = {key: value for key, value in os.environ.items() if key != 'WAYLAND_DISPLAY'}
        env.update({'DISPLAY': self.args.display, 'SDL_VIDEODRIVER': 'x11'})
        self.client = subprocess.Popen(['portablemc', '--main-dir', str(Path.home() / '.cache' / 'pmc'), 'start', self.minecraft,
                                        '--mc-dir', str(self.game), '-u', 'Tester', '--join-server', '127.0.0.1',
                                        '--join-server-port', str(self.args.port), '--resolution', f'{WIDTH}x{HEIGHT}',
                                        '--jvm-arg=-Xmx2G'], stdout=open(self.client_log, 'w'), stderr=subprocess.STDOUT, env=env)
        self.wait_log('Tester joined the game', 300)
        time.sleep(8)
        geometry = self.xdo('getwindowgeometry', self.window())
        match = re.search(r'Position: (\d+),(\d+)', geometry)
        self.top = int(match.group(2)) if match else 0

    def stop_client(self):
        if self.client and self.client.poll() is None:
            for pid in subprocess.run(['pgrep', '-f', str(self.game)], capture_output=True, text=True).stdout.split():
                subprocess.run(['kill', pid])
            self.client.wait(60)

    # Server console and log

    def console(self, command):
        with open(self.fifo, 'w') as fifo:
            fifo.write(command + '\n')
        time.sleep(0.4)

    def log(self):
        with open(self.log_path, errors='replace') as log:
            log.seek(self.log_offset)
            return log.read()

    def wait_log(self, text, seconds):
        deadline = time.time() + seconds
        while time.time() < deadline:
            if text in self.log():
                return True
            if self.server.poll() is not None:
                raise SystemExit(f'Server stopped. See {self.log_path}')
            time.sleep(2)
        raise SystemExit(f'Timed out waiting for "{text}". See {self.log_path}')

    def ask(self, command, wait=1.5):
        start = len(self.log())
        self.console(command)
        time.sleep(wait)
        return self.log()[start:]

    def check(self, what, passed, detail=''):
        self.results.append(f'{"PASS" if passed else "FAIL"} {what}' + (f' ({detail})' if detail and not passed else ''))
        print(self.results[-1], flush=True)

    # Client input

    def xdo(self, *args):
        return subprocess.run(['xdotool', *args], env={**os.environ, 'DISPLAY': self.args.display},
                              capture_output=True, text=True).stdout.strip()

    def window(self):
        """The game window. 26.x clients name it only in UTF-8 and map a hidden utility window over the screen's middle."""
        tree = subprocess.run(['xwininfo', '-root', '-children'], env={**os.environ, 'DISPLAY': self.args.display},
                              capture_output=True, text=True).stdout
        best, area = '', 0
        for match in re.finditer(r'(0x[0-9a-f]+) "(Minecraft[^"]*)".*?(\d+)x(\d+)\+', tree):
            size = int(match.group(3)) * int(match.group(4))
            if 'Hidden' not in match.group(2) and size > area:
                best, area = match.group(1), size
        return best

    def focus(self):
        window = self.window()
        if window:
            self.xdo('windowfocus', '--sync', window)

    def key(self, *keys):
        self.focus()
        self.xdo('key', '--delay', '60', *keys)
        time.sleep(0.4)

    def chat(self, text):
        self.focus()
        self.xdo('key', 't')
        time.sleep(0.7)
        self.xdo('type', '--delay', '25', text)
        time.sleep(0.3)
        self.xdo('key', 'Return')
        time.sleep(1.2)

    def type(self, text):
        self.focus()
        self.xdo('type', '--delay', '40', text)
        time.sleep(0.6)

    def use(self):
        self.focus()
        self.xdo('click', '3')
        time.sleep(0.6)

    def point(self, gx, gy):
        return int(gx * GUI_SCALE), int(self.top + gy * GUI_SCALE)

    def move(self, gx, gy):
        x, y = self.point(gx, gy)
        self.focus()
        self.xdo('mousemove', str(x), str(y))
        time.sleep(0.3)

    def click(self, gx, gy, button='1', modifier=None):
        self.move(gx, gy)
        if modifier:
            self.xdo('keydown', modifier)
        self.xdo('click', button)
        if modifier:
            self.xdo('keyup', modifier)
        time.sleep(1.0)

    def chest(self, slot):
        left, top = (WIDTH // GUI_SCALE - 176) // 2, (HEIGHT // GUI_SCALE - 222) // 2
        if slot < 54:
            row, column = divmod(slot, 9)
            return left + 16 + column * 18, top + 26 + row * 18
        index = slot - 54
        if index < 27:
            row, column = divmod(index, 9)
            return left + 16 + column * 18, top + 147 + row * 18
        return left + 16 + (index - 27) * 18, top + 205

    def anvil(self, slot):
        left, top = (WIDTH // GUI_SCALE - 176) // 2, (HEIGHT // GUI_SCALE - 166) // 2
        return {0: (left + 35, top + 55), 2: (left + 142, top + 55), 'field': (left + 112, top + 30)}[slot]

    def slot(self, slot, button='1', modifier=None):
        self.click(*self.chest(slot), button, modifier)

    def shot(self, name):
        self.shots.mkdir(parents=True, exist_ok=True)
        subprocess.run(['import', '-window', 'root', str(self.shots / f'{name}.png')],
                       env={**os.environ, 'DISPLAY': self.args.display})

    def enter_text(self, text, shot=None):
        """Types into the anvil, or into chat when the server asked for chat (Spigot before 1.21 has no anvil API)."""
        time.sleep(0.8)
        if 'in chat' in self.client_log.read_text(errors='replace')[self.chat_offset:]:
            self.chat_offset = len(self.client_log.read_text(errors='replace'))
            if shot:
                self.shot(shot)
            self.chat(text)
            time.sleep(1.0)
            return
        self.click(*self.anvil('field'))
        self.key('ctrl+a', 'BackSpace')
        self.type(text)
        if shot:
            self.shot(shot)
        self.click(*self.anvil(2))

    def same_slot(self, shot, reference, slot):
        """Compares two slots of a chest screenshot. An empty button slot shows the same pane as the reference."""
        crops = []
        for index, target in enumerate((reference, slot)):
            x, y = self.point(*self.chest(target))
            crop = self.shots / f'{shot}-slot{index}.png'
            subprocess.run(['convert', str(self.shots / f'{shot}.png'), '-crop', f'28x28+{x - 14}+{y - 14}', '+repage', str(crop)])
            crops.append(str(crop))
        result = subprocess.run(['compare', '-metric', 'AE', *crops, 'null:'], capture_output=True, text=True)
        for crop in crops:
            Path(crop).unlink(missing_ok=True)
        try:
            return float(result.stderr.split()[0]) < 20
        except (IndexError, ValueError):
            return False

    def look(self, x, z, pitch=30):
        self.console(f'tp Tester {x} -60 {z - 2} 0 {pitch}')
        time.sleep(1.5)

    # Version differences

    def name_tag(self, name):
        version = version_tuple(self.minecraft)
        if version < (1, 20, 5):
            return f"name_tag{{display:{{Name:'\"{name}\"'}}}}"
        if version < (1, 21, 5):
            return f"name_tag[custom_name='\"{name}\"']"
        return f'name_tag[custom_name="{name}"]'

    def inventory(self):
        """The player's items as the server sees them. 1.21.5 moved the offhand from Inventory to equipment."""
        parts = []
        for path in ['Inventory'] + (['equipment'] if version_tuple(self.minecraft) >= (1, 21, 5) else []):
            reply = self.ask(f'data get entity Tester {path}')
            match = re.findall(r'has the following entity data: (.*)', reply)
            parts.append(match[-1].strip() if match else 'unknown')
        return ' | '.join(parts)

    def history_file(self):
        world = self.server_dir / 'world'
        modern = world / 'dimensions' / 'minecraft' / 'overworld' / 'data' / 'familytree' / 'familytree.dat'
        return modern if (world / 'dimensions' / 'minecraft' / 'overworld').is_dir() and version_tuple(self.minecraft)[0] >= 26 \
            else world / 'data' / 'familytree.dat'


def read_records(path):
    def read(data, kind):
        if kind == 1: return struct.unpack('>b', data.read(1))[0]
        if kind == 2: return struct.unpack('>h', data.read(2))[0]
        if kind == 3: return struct.unpack('>i', data.read(4))[0]
        if kind == 4: return struct.unpack('>q', data.read(8))[0]
        if kind == 5: return struct.unpack('>f', data.read(4))[0]
        if kind == 6: return struct.unpack('>d', data.read(8))[0]
        if kind == 7: return data.read(struct.unpack('>i', data.read(4))[0])
        if kind == 8: return data.read(struct.unpack('>H', data.read(2))[0]).decode('utf-8', 'replace')
        if kind == 9:
            element = data.read(1)[0]
            return [read(data, element) for _ in range(struct.unpack('>i', data.read(4))[0])]
        if kind == 10:
            out = {}
            while True:
                entry = data.read(1)[0]
                if entry == 0:
                    return out
                name = data.read(struct.unpack('>H', data.read(2))[0]).decode()
                out[name] = read(data, entry)
        if kind == 11:
            count = struct.unpack('>i', data.read(4))[0]
            return list(struct.unpack(f'>{count}i', data.read(4 * count)))
        if kind == 12:
            count = struct.unpack('>i', data.read(4))[0]
            return list(struct.unpack(f'>{count}q', data.read(8 * count)))
        raise ValueError(kind)
    with gzip.open(path) as data:
        data.read(1)
        data.read(struct.unpack('>H', data.read(2))[0])
        return read(data, 10)['data']['records']


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--minecraft', required=True)
    parser.add_argument('--software', default='paper', choices=['paper', 'purpur', 'spigot'])
    parser.add_argument('--server-jar', type=Path)
    parser.add_argument('--dir', type=Path)
    parser.add_argument('--port', type=int, default=25714)
    parser.add_argument('--display', default=':66')
    parser.add_argument('--keep', action='store_true', help='Leave the server and client running at the end')
    args = parser.parse_args()
    s = Session(args)
    if s.run.exists():
        shutil.rmtree(s.run / 'shots', ignore_errors=True)
    s.run.mkdir(parents=True, exist_ok=True)
    s.log_path.write_text('')
    try:
        s.start_server(fresh=True)
        s.start_client()
        for command in ['op Tester', 'gamemode survival Tester', 'difficulty peaceful', 'time set day',
                        'kill @e[type=!player]', 'clear Tester']:
            s.console(command)
        s.shot('joined')

        # Taming and breeding by hand.
        s.console('summon wolf 0.5 -60 2.5 {NoAI:1b}')
        s.console('summon wolf 2.5 -60 2.5 {NoAI:1b}')
        s.console('give Tester bone 64')
        for x in (0.5, 2.5):
            s.look(x, 2.5)
            for _ in range(15):
                s.use()
        listing = s.ask('familytree list')
        s.check('taming two wolves by hand records both', listing.count('[Wolf]') == 2, listing[-300:])
        s.console('clear Tester')
        s.console('give Tester beef 4')
        for x in (0.5, 2.5):
            s.look(x, 2.5)
            s.use()
        s.console('execute as @e[type=wolf] run data merge entity @s {NoAI:0b,Sitting:0b}')
        time.sleep(15)
        listing = s.ask('familytree list')
        s.check('feeding them beef records the puppy', listing.count('[Wolf]') == 3, listing[-300:])

        # A name tag and three named sheep for the parent picker. Freeze the wolves so they can't walk into the click.
        s.console('execute as @e[type=wolf] run data merge entity @s {NoAI:1b}')
        s.console('clear Tester')
        for index, name in enumerate(['Woolly', 'Fluffy', 'Puff']):
            x = 10.5 + index * 2
            s.console(f'summon sheep {x} -60 2.5 {{NoAI:1b}}')
            for _ in range(3):
                s.console('clear Tester')
                s.console(f'give Tester {s.name_tag(name)}')
                s.look(x, 2.5)
                s.use()
                if f'- {name} [' in s.ask('familytree list'):
                    break
        listing = s.ask('familytree list')
        s.check('name tags add sheep', all(name in listing for name in ['Woolly', 'Fluffy', 'Puff']), listing[-400:])

        # The chest menu: search, family, rename, Add parents.
        s.console('clear Tester')
        s.console('give Tester diamond 5')
        s.console('give Tester stick 3')
        s.console('item replace entity Tester weapon.offhand with apple 2')
        s.console('tp Tester 0.5 -60 -6 0 0')
        before = s.inventory()
        s.chat('/familytree')
        s.shot('menu-browser')
        s.chat_offset = len(s.client_log.read_text(errors='replace'))
        s.slot(50)
        s.enter_text('Woolly', 'menu-search')
        s.slot(0)
        s.move(20, 20)
        s.shot('menu-family')
        s.check("the owner's family view has Locate, Rename, and Add parents",
                not any(s.same_slot('menu-family', 45, slot) for slot in (47, 48, 50)))
        s.chat_offset = len(s.client_log.read_text(errors='replace'))
        s.slot(48)
        s.enter_text('Woolly Prime', 'menu-rename')
        info = s.ask('familytree info Woolly Prime')
        s.check('the anvil renames the sheep in Family Tree', 'Woolly Prime' in info and 'No record' not in info, info[-300:])
        s.slot(50)
        s.slot(0)
        s.slot(0)
        s.shot('menu-confirm')
        s.slot(38)
        s.shot('menu-linked')
        info = s.ask('familytree info Woolly Prime')
        s.check('Add parents links both chosen sheep', 'Fluffy' in info and 'Puff' in info, info[-300:])
        s.check('the anvil never gives back its display item', s.inventory() == before, f'{before} -> {s.inventory()}')

        # Every input that could move an item.
        s.slot(81, '1', 'shift')
        s.move(*s.chest(0))
        s.key('1')
        s.key('2')
        x, y = s.point(*s.chest(82))
        s.focus()
        s.xdo('mousemove', str(x), str(y))
        s.xdo('click', '--repeat', '2', '--delay', '80', '1')
        x, y = s.point(*s.chest(81))
        s.xdo('mousemove', str(x), str(y))
        s.xdo('mousedown', '1')
        for target in (83, 84, 1, 2):
            x, y = s.point(*s.chest(target))
            s.xdo('mousemove', str(x), str(y))
            time.sleep(0.15)
        s.xdo('mouseup', '1')
        s.move(*s.chest(0))
        s.key('q')
        s.key('ctrl+q')
        s.key('f')
        s.move(*s.chest(81))
        s.key('f')
        s.slot(82)
        s.key('Escape')
        time.sleep(1)
        s.shot('after-inputs')
        after = s.inventory()
        s.check('shift-click, number keys, double-click, drag, Q, F, and closing leave the inventory unchanged', after == before,
                f'{before} -> {after}')

        # Someone else's pet.
        s.console('summon wolf 5.5 -60 -9.5 {Owner:[I;1,2,3,4]}')
        s.console('familytree scan')
        s.console('save-all')
        time.sleep(3)
        stranger = [record for record in read_records(s.history_file()) if record.get('owner') == [1, 2, 3, 4]]
        name = stranger[0]['name'] if stranger else 'missing'
        s.console('deop Tester')
        config = s.server_dir / 'plugins' / 'FamilyTree' / 'config.yml'
        config.write_text(re.sub(r'(?m)^viewPolicy: .*$', 'viewPolicy: EVERYONE_ALL', config.read_text()))
        reply = s.ask('familytree reload')
        s.check('reload applies viewPolicy', 'Reloaded' in reply, reply[-200:])
        s.chat('/familytree')
        s.shot('nonowner-browser')
        s.chat_offset = len(s.client_log.read_text(errors='replace'))
        s.slot(50)
        s.enter_text(name)
        s.slot(0)
        s.move(20, 20)
        s.shot('nonowner-family')
        s.check("a non-owner's family view has no Locate, Rename, or Add parents",
                all(s.same_slot('nonowner-family', 45, slot) for slot in (47, 48, 50)))
        s.key('Escape')
        s.chat(f'/familytree locate {name}')
        s.chat(f'/familytree pair Fluffy Puff "{name}"')
        s.chat('/familytree prune deceased')
        client = s.client_log.read_text(errors='replace')
        s.check("a non-owner can't locate or edit someone else's pet by command",
                client.count(f"No record for '{name}'") >= 2 and 'familytree.prune' in client, client[-500:])

        # Restart.
        s.console('save-all')
        time.sleep(3)
        saved = read_records(s.history_file())
        woolly = next((record for record in saved if record.get('tree_name') == 'Woolly Prime'), None)
        s.check('the save has the puppy with both parents and the linked sheep',
                any(record.get('species') == 'minecraft:wolf' and 'parent_a' in record and 'parent_b' in record for record in saved)
                and woolly is not None and 'parent_a' in woolly and 'parent_b' in woolly)
        s.stop_client()
        s.stop_server()
        s.start_server(fresh=False)
        loaded = re.search(r'Family Tree loaded (\d+) records', s.log())
        s.check('history survives a restart', loaded is not None and int(loaded.group(1)) == len(saved),
                f'{loaded.group(1) if loaded else None} of {len(saved)}')
        info = s.ask('familytree info Woolly Prime')
        s.check('names and parents survive a restart', 'Fluffy' in info and 'Puff' in info, info[-300:])
    finally:
        if not args.keep:
            s.stop_client()
            if s.server and s.server.poll() is None:
                s.stop_server()
        failed = any(line.startswith('FAIL') for line in s.results)
        s.results.append('RESULT FAIL' if failed or not s.results else 'RESULT PASS')
        (s.run / 'client-check.txt').write_text('\n'.join(s.results) + '\n')
        print(s.results[-1])


if __name__ == '__main__':
    main()
