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
    if (shots / 'media-panda-variant.png').exists():
        outputs.append(still(shots / 'media-panda-variant.png', 'panda-variant'))
    export = Image.open(newest_export(game_directory)).convert('RGB')
    export.save(MEDIA / 'horse-family-export.png', optimize=True)
    outputs.append(MEDIA / 'horse-family-export.png')
    for path in outputs:
        print(f'{path.relative_to(ROOT)}: {path.stat().st_size // 1024} KiB')


def server_page(shots, recording, start):
    """Packages the two-player server capture: GUI-scale-2 screens halved, chest menus kept at full size."""
    MEDIA.mkdir(parents=True, exist_ok=True)
    def label(image, text):
        out = Image.new('RGB', (image.width, image.height + 26), '#151b22')
        ImageDraw.Draw(out).text((10, 5), text, font=font(15), fill='#e8e2d4')
        out.paste(image, (0, 26))
        return out
    both = Image.open(shots / 'both-restart.png').convert('RGB')
    top = label(both.crop((0, 0, 1600, 1000)).resize((800, 500), Image.Resampling.BOX),
                'Alex, with Family Tree on the client: the tree screen')
    bottom = label(both.crop((2000, 200, 2800, 700)), 'Kai, unmodded Minecraft 1.21.1: /familytree chest menu, same family')
    stack = Image.new('RGB', (800, top.height + bottom.height + 4), '#0b0f14')
    stack.paste(top, (0, 0))
    stack.paste(bottom, (0, top.height + 4))
    outputs = [MEDIA / 'server-two-players.webp']
    stack.save(outputs[0], quality=88, method=6)
    outputs.append(still(shots / 'k-book2.png', 'server-family-book', crop=(500, 0, 1300, 500), scale=1))
    outputs.append(still(shots / 'k-viewonly-info.png', 'server-view-only', crop=(560, 200, 1360, 700), scale=1))
    webp, gif = MEDIA / 'server-chest-menu.webp', MEDIA / 'server-chest-menu.gif'
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-ss', f'{start:.2f}', '-t', '11.4', '-i', str(recording),
                    '-vf', 'fps=10,crop=800:560:400:150', '-c:v', 'libwebp_anim', '-quality', '70', '-compression_level', '6',
                    '-loop', '0', str(webp)], check=True)
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-ss', f'{start:.2f}', '-t', '11.4', '-i', str(recording),
                    '-vf', 'fps=8,crop=800:560:400:150,scale=640:-2:flags=area,split[a][b];[a]palettegen=max_colors=96:stats_mode=diff[p];'
                           '[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle', '-loop', '0', str(gif)], check=True)
    for path in outputs + [webp, gif]:
        print(f'{path.relative_to(ROOT)}: {path.stat().st_size // 1024} KiB')


def bold(size):
    for path in ['/usr/share/fonts/truetype/clear-sans/ClearSans-Bold.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return font(size)


def frames(recording, start, duration, crop=None, fps=30):
    """Decodes part of a recording into RGB images at the clip's frame rate."""
    vf = f'fps={fps}' + (f',crop={crop}' if crop else '')
    raw = subprocess.run(['ffmpeg', '-loglevel', 'error', '-ss', f'{start:.2f}', '-t', f'{duration:.2f}', '-i', str(recording),
                          '-vf', vf, '-f', 'image2pipe', '-vcodec', 'png', '-'], check=True, capture_output=True).stdout
    images, marker = [], b'\x89PNG\r\n\x1a\n'
    for chunk in raw.split(marker)[1:]:
        images.append(Image.open(__import__('io').BytesIO(marker + chunk)).convert('RGB'))
    return images


def drift(image, count, zoom=0.06):
    """A slow push-in on a still, so screenshots read as part of the clip."""
    out = []
    for i in range(count):
        scale = 1 + zoom * i / max(1, count - 1)
        w, h = image.width / scale, image.height / scale
        box = ((image.width - w) / 2, (image.height - h) / 2, (image.width + w) / 2, (image.height + h) / 2)
        out.append(image.resize(image.size, Image.Resampling.LANCZOS, box=box))
    return out


def showcase(page_directory, recording, start, server_recording, server_start, server_shots):
    """Builds the 1080p landscape and vertical launch clips from the -Pmedia scene and the server capture."""
    marks = timeline(page_directory / 'media-timeline.txt', start)
    shots = page_directory / 'screenshots'
    fps = 30
    segments = [
        ('Breed your animals.', frames(recording, marks['pair Clover x Pippin'] + 0.6, 5.0, fps=fps)),
        ('Every baby joins the family tree.', frames(recording, marks['tree pan'] + 0.2, 7.5, fps=fps)),
        ('See if a foal beat its parents.', drift(Image.open(shots / 'media-foal-stats.png').convert('RGB'), 3 * fps)),
        ('Find out where its colors came from.', drift(Image.open(ROOT / 'build/media/run6/screenshots/media-panda-variant.png').convert('RGB'), 3 * fps)),
        ('Works on servers, even without the mod.', frames(server_recording, server_start + 0.4, 5.5, crop='800:560:400:150', fps=fps)),
        ('Export the family as a PNG.', drift(Image.open(newest_export(page_directory)).convert('RGB'), int(2.5 * fps), zoom=0.03)),
    ]
    note = 'Automated scenes with vanilla breeding, and a two-player server. Minecraft 26.3 and 1.21.1.'
    out = ROOT / 'build/implementation/showcase'
    for layout in ('landscape', 'vertical'):
        directory = out / layout
        shutil.rmtree(directory, ignore_errors=True)
        directory.mkdir(parents=True)
        index = 0
        for caption, images in segments:
            for image in images:
                if layout == 'landscape':
                    canvas = Image.new('RGB', (1920, 1080), '#0e1419')
                    scale = min(1920 / image.width, 960 / image.height)
                    shown = image.resize((round(image.width * scale), round(image.height * scale)), Image.Resampling.LANCZOS)
                    canvas.paste(shown, ((1920 - shown.width) // 2, 1080 - 60 - shown.height))
                    draw = ImageDraw.Draw(canvas)
                    centered(draw, caption, 14, 1920, bold(48), '#f4ecd8')
                    centered(draw, note + '  modrinth.com/mod/familytree', 1080 - 44, 1920, font(24), '#9fb0b8')
                else:
                    canvas = Image.new('RGB', (1080, 1920), '#0e1419')
                    draw = ImageDraw.Draw(canvas)
                    centered(draw, 'Family Tree', 170, 1080, bold(84), '#f4ecd8')
                    centered(draw, 'for Minecraft', 270, 1080, font(40), '#c9bfa8')
                    scale = min(1080 / image.width, 900 / image.height)
                    shown = image.resize((round(image.width * scale), round(image.height * scale)), Image.Resampling.LANCZOS)
                    canvas.paste(shown, ((1080 - shown.width) // 2, 400 + (900 - shown.height) // 2))
                    centered(draw, caption, 1370, 1080, bold(50), '#f4ecd8')
                    centered(draw, 'modrinth.com/mod/familytree', 1700, 1080, font(38), '#c9bfa8')
                    centered(draw, 'Automated scenes with vanilla breeding,', 1790, 1080, font(26), '#8d9ea6')
                    centered(draw, 'and a two-player server.', 1828, 1080, font(26), '#8d9ea6')
                canvas.save(directory / f'{index:05}.png')
                index += 1
        name = 'family-tree-showcase' + ('' if layout == 'landscape' else '-vertical')
        target = ROOT / 'docs/launch' / f'{name}.mp4'
        target.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(fps), '-i', str(directory / '%05d.png'),
                        '-c:v', 'libx264', '-crf', '24', '-preset', 'slow', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(target)], check=True)
        print(f'{target.relative_to(ROOT)}: {index / fps:.1f} s, {target.stat().st_size // 1024} KiB')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--game-directory', type=Path, default=ROOT / 'build/demo-26.2')
    parser.add_argument('--horses', type=Path, help='Package only the horse scene from this game directory')
    parser.add_argument('--animals', type=Path, help='Package only the sheep and axolotl scene from this game directory')
    parser.add_argument('--page', type=Path, help='Package the -Pmedia scene from this game directory')
    parser.add_argument('--recording', type=Path, help='Screen recording of the -Pmedia run')
    parser.add_argument('--recording-start', type=int, help='Epoch milliseconds when the recording started')
    parser.add_argument('--showcase', action='store_true', help='With --page, also build the launch clips')
    parser.add_argument('--server-recording', type=Path, help="Recording of the unmodded client's chest menu")
    parser.add_argument('--server-start', type=float, help='Seconds into that recording where the menu walkthrough starts')
    parser.add_argument('--server-shots', type=Path, help='Screenshots from scripts/server_media.py; packages the server media')
    args = parser.parse_args()
    if args.server_shots:
        server_page(args.server_shots, args.server_recording, args.server_start)
        return
    if args.showcase:
        showcase(args.page, args.recording, args.recording_start, args.server_recording, args.server_start, None)
        return
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
