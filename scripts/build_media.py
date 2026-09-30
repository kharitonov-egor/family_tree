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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--game-directory', type=Path, default=ROOT / 'build/demo-26.2')
    args = parser.parse_args()
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
    exports = list((args.game_directory / 'screenshots/familytree').glob('*.png'))
    newest = max(exports, key=lambda path: path.stat().st_mtime_ns)
    shutil.copyfile(newest, ROOT / 'docs/family-tree-gameplay-export.png')
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
