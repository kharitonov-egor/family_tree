"""Package screenshots from the development runner as real gameplay media."""
import argparse
from pathlib import Path
import shutil
import subprocess
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]


def font(size):
    for path in ['C:/Windows/Fonts/arial.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf']:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def centered(draw, text, y, width, face, fill='#eee6d6'):
    draw.text((width / 2, y), text, font=face, fill=fill, anchor='mt')


def encode(pattern, destination, webp=False):
    command = ['ffmpeg', '-y', '-loglevel', 'error', '-framerate', '5', '-i', str(pattern)]
    command += ['-c:v', 'libwebp_anim', '-quality', '58', '-loop', '0'] if webp else [
        '-c:v', 'libx264', '-crf', '23', '-pix_fmt', 'yuv420p', '-movflags', '+faststart']
    subprocess.run(command + [str(destination)], check=True)


def newest_export(game_directory):
    exports = list((game_directory / 'screenshots/familytree').glob('*.png'))
    return max(exports, key=lambda path: path.stat().st_mtime_ns)


def horses(game_directory):
    """Copy the horse breeding scene from `runDemo -Pdemo -Phorses` into docs/."""
    assert 'golden carrots; AI breeding' in (game_directory / 'demo-result.txt').read_text()
    shutil.copyfile(newest_export(game_directory), ROOT / 'docs/horse-breeding-export.png')
    screenshot = Image.open(game_directory / 'screenshots/horses-tree.png')
    screenshot.save(ROOT / 'docs/horse-breeding-tree.webp', quality=82)
    for name in ['horse-breeding-export.png', 'horse-breeding-tree.webp']:
        path = ROOT / 'docs' / name
        print(path.name, path.stat().st_size, 'bytes')


def animals(game_directory):
    """Copy the sheep and axolotl scene from `runDemo -Pdemo -Panimals` into docs/."""
    assert 'mixed wool colors' in (game_directory / 'demo-result.txt').read_text()
    shutil.copyfile(newest_export(game_directory), ROOT / 'docs/sheep-family-export.png')
    Image.open(game_directory / 'screenshots/animals-tree.png').save(ROOT / 'docs/sheep-family-tree.webp', quality=82)
    Image.open(game_directory / 'screenshots/animals-axolotl.png').save(ROOT / 'docs/blue-axolotl-tree.webp', quality=82)
    for name in ['sheep-family-export.png', 'sheep-family-tree.webp', 'blue-axolotl-tree.webp']:
        path = ROOT / 'docs' / name
        print(path.name, path.stat().st_size, 'bytes')


MEDIA = ROOT / 'docs/media'


def timeline(path, start):
    """Seconds into the screen recording for each label that `-Pmedia` wrote to media-timeline.txt."""
    marks = {}
    for line in path.read_text().splitlines():
        millis, _, label = line.split(' ', 2)
        marks.setdefault(label, (int(millis) - start) / 1000)
    return marks


def animation(recording, start, duration, name, fps=12, width=800, quality=62):
    """Cuts a clip from the 1600x1000 recording into an animated WebP and a GIF fallback for CurseForge."""
    scale = f'fps={fps},scale={width}:-2:flags=area'
    webp, gif = MEDIA / f'{name}.webp', MEDIA / f'{name}.gif'
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-ss', f'{start:.2f}', '-t', f'{duration:.2f}', '-i', str(recording),
                    '-vf', scale, '-c:v', 'libwebp_anim', '-quality', str(quality), '-compression_level', '6', '-loop', '0', str(webp)], check=True)
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-ss', f'{start:.2f}', '-t', f'{duration:.2f}', '-i', str(recording),
                    '-vf', f'fps=10,scale=640:-2:flags=area,split[a][b];[a]palettegen=max_colors=128:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle',
                    '-loop', '0', str(gif)], check=True)
    return webp, gif


def still(source, name, crop=None, scale=0.5, quality=88):
    """Halves a GUI-scale-2 screenshot, which keeps Minecraft's pixel font sharp at its 1x size."""
    image = Image.open(source).convert('RGB')
    if crop: image = image.crop(crop)
    if scale != 1: image = image.resize((round(image.width * scale), round(image.height * scale)), Image.Resampling.BOX)
    path = MEDIA / f'{name}.webp'
    image.save(path, quality=quality, method=6)
    return path


def page(game_directory, recording, start):
    """Packages the `-Pmedia` scene into docs/media/: page animations, stills, and the exported family."""
    assert 'four horse generations' in (game_directory / 'demo-result.txt').read_text()
    MEDIA.mkdir(parents=True, exist_ok=True)
    marks = timeline(game_directory / 'media-timeline.txt', start)
    shots = game_directory / 'screenshots'
    outputs = []
    outputs += animation(recording, marks['tree pan'], marks['tree pan end'] - marks['tree pan'], 'tree-tour')
    outputs += animation(recording, marks['pair Clover x Pippin'] + 0.6, 7.0, 'horse-breeding')
    outputs += animation(recording, marks['browser pets'] + 0.4, marks['browser search'] - marks['browser pets'] + 4.2, 'browser-tabs', fps=10)
    outputs.append(still(shots / 'media-tree-fit.png', 'tree-four-generations'))
    outputs.append(still(shots / 'media-foal-stats.png', 'foal-stats'))
    outputs.append(still(shots / 'media-sheep-variant.png', 'sheep-variant'))
    outputs.append(still(shots / 'media-jade.png', 'jade-tooltip', crop=(400, 0, 1200, 660), scale=1))
    outputs.append(still(shots / 'media-waypoint-world.png', 'journeymap-waypoint'))
    outputs.append(still(shots / 'media-browser-search.png', 'browser-search'))
    if (shots / 'media-panda-variant.png').exists():
        outputs.append(still(shots / 'media-panda-variant.png', 'panda-variant'))
    export = Image.open(newest_export(game_directory)).convert('RGB')
    export.save(MEDIA / 'horse-family-export.png', optimize=True)
    export.resize((800, round(export.height * 800 / export.width)), Image.Resampling.LANCZOS).save(
        MEDIA / 'horse-family-export-small.webp', quality=90, method=6)
    outputs += [MEDIA / 'horse-family-export.png', MEDIA / 'horse-family-export-small.webp']
    for path in outputs:
        print(f'{path.relative_to(ROOT)}: {path.stat().st_size // 1024} KiB')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--game-directory', type=Path, default=ROOT / 'build/demo-26.2')
    parser.add_argument('--horses', type=Path, help='Package only the horse scene from this game directory')
    parser.add_argument('--animals', type=Path, help='Package only the sheep and axolotl scene from this game directory')
    parser.add_argument('--page', type=Path, help='Package the -Pmedia scene from this game directory')
    parser.add_argument('--recording', type=Path, help='Screen recording of the -Pmedia run')
    parser.add_argument('--recording-start', type=int, help='Epoch milliseconds when the recording started')
    args = parser.parse_args()
    if args.page:
        page(args.page, args.recording, args.recording_start)
        return
    if args.horses:
        horses(args.horses)
        return
    if args.animals:
        animals(args.animals)
        return
    assert 'vanilla feeding; AI breeding' in (args.game_directory / 'demo-result.txt').read_text()
    frames = sorted((args.game_directory / 'screenshots').glob('gameplay-[0-9]*.png'))
    assert len(frames) >= 95, 'Gameplay capture is incomplete'
    landscape = ROOT / 'build/implementation/media-landscape'
    vertical = ROOT / 'build/implementation/media-vertical'
    landscape.mkdir(parents=True, exist_ok=True); vertical.mkdir(parents=True, exist_ok=True)
    for i, source in enumerate(frames):
        tick = int(source.stem.split('-')[1])
        text = ('Breed your named wolves.' if tick < 140 else
                'Clover joins the family.' if tick < 240 else
                "Keep the parents, even after they're gone." if tick < 300 else
                'Export the family as a PNG.')
        screenshot = Image.open(source).convert('RGB')
        horizontal = Image.new('RGB', (1280, 856), '#101820')
        horizontal.paste(screenshot.resize((1280, 800), Image.Resampling.LANCZOS), (0, 56))
        draw = ImageDraw.Draw(horizontal); centered(draw, text, 13, 1280, font(28))
        horizontal.save(landscape / f'{i:04}.png')
        portrait = Image.new('RGB', (720, 1280), '#101820')
        draw = ImageDraw.Draw(portrait)
        centered(draw, 'Your wolf has a family tree.', 120, 720, font(38))
        centered(draw, text if len(text) < 40 else 'Remember the parents you lost.', 225, 720, font(28))
        portrait.paste(screenshot.resize((720, 450), Image.Resampling.LANCZOS), (0, 370))
        centered(draw, 'Family Tree for Minecraft pets', 970, 720, font(32))
        centered(draw, 'modrinth.com/mod/familytree', 1030, 720, font(27))
        centered(draw, 'Automated scene. Vanilla wolves and breeding.', 1150, 720, font(20), '#a9bac0')
        portrait.save(vertical / f'{i:04}.png')
    encode(landscape / '%04d.png', ROOT / 'docs/family-tree-gameplay.mp4')
    encode(landscape / '%04d.png', ROOT / 'docs/family-tree-gameplay.webp', webp=True)
    encode(vertical / '%04d.png', ROOT / 'docs/family-tree-gameplay-vertical.mp4')
    shutil.copyfile(newest_export(args.game_directory), ROOT / 'docs/family-tree-gameplay-export.png')
    screenshot = Image.open(args.game_directory / 'screenshots/gameplay-tree.png')
    screenshot.save(ROOT / 'docs/family-tree-gameplay-view.webp', quality=82)
    screenshot = Image.open(ROOT / 'build/demo-26.2/screenshots/parent-links.png')
    screenshot.save(ROOT / 'docs/parent-links.webp', quality=82)
    screenshot = Image.open(ROOT / 'build/demo-26.2/screenshots/browser.png')
    screenshot.save(ROOT / 'docs/tracked-pets-browser.webp', quality=82)
    for name in ['family-tree-gameplay.mp4', 'family-tree-gameplay.webp', 'family-tree-gameplay-vertical.mp4']:
        path = ROOT / 'docs' / name
        print(path.name, path.stat().st_size, 'bytes')


if __name__ == '__main__':
    main()
