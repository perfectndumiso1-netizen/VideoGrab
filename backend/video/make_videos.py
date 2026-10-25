#!/usr/bin/env python3
"""
Silent daily videos (NO voice / NO AI narration): 3 videos per run, one per
opportunity in data/opportunities.json. On-screen captions, step numbers,
arrows/callouts, and the official link. If a Playwright screenshot exists in
screenshots/<id>.png it is used as the visual; otherwise a clean illustrated
placeholder screen is drawn (clearly labelled 'illustration').

Demo details are FICTIONAL placeholders (e.g. demo@example.com), drawn as an
overlay only -- they are never typed into or submitted to a real website.

Output: videos/out/<id>.mp4 (ffmpeg) or <id>.gif fallback. 1080x1920, silent.
"""
import json, shutil, subprocess, sys, textwrap
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent.parent
OUT = ROOT / "videos" / "out"; OUT.mkdir(parents=True, exist_ok=True)
W, H, SECONDS_PER_SLIDE, FPS = 1080, 1920, 4, 2  # low fps = small, caption-led

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    raise SystemExit("pip install -r backend/requirements.txt")

def font(size, bold=False):
    for p in ["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
              "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"]:
        if Path(p).exists(): return ImageFont.truetype(p, size)
    return ImageFont.load_default()

def wrap(d, text, f, maxw):
    words, lines, cur = text.split(), [], ""
    for w_ in words:
        t = (cur + " " + w_).strip()
        if d.textlength(t, font=f) <= maxw: cur = t
        else: lines.append(cur); cur = w_
    if cur: lines.append(cur)
    return lines

def slide(title, body, kicker, shot=None, illustration=False):
    img = Image.new("RGB", (W, H), (12, 18, 32)); d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W, 300], fill=(0, 122, 90))
    d.text((60, 60), kicker.upper(), font=font(38, True), fill=(255, 255, 255))
    y = 120
    for line in wrap(d, title, font(64, True), W - 120)[:3]:
        d.text((60, y), line, font=font(64, True), fill=(255, 255, 255)); y += 76
    y = 360
    if shot and Path(shot).exists():
        s = Image.open(shot).convert("RGB"); s.thumbnail((W - 120, 760))
        img.paste(s, (60, y)); d = ImageDraw.Draw(img); y += s.height + 30
        if illustration: d.text((60, y), "Illustration - not a real account", font=font(32), fill=(255, 210, 90)); y += 50
    for line in wrap(d, body, font(48), W - 120):
        d.text((60, y), line, font=font(48), fill=(235, 240, 255)); y += 62
    # callout arrow + box
    d.rounded_rectangle([60, H - 300, W - 60, H - 140], radius=24, outline=(255, 210, 90), width=6)
    d.polygon([(W - 160, H - 300), (W - 100, H - 360), (W - 220, H - 360)], fill=(255, 210, 90))
    d.text((90, H - 270), "Read every step. Never pay to apply.", font=font(40, True), fill=(255, 210, 90))
    d.text((90, H - 210), "SA Opportunities - check before you trust.", font=font(34), fill=(200, 210, 230))
    return img

def build(opp):
    shot = ROOT / "screenshots" / f"{opp['id']}.png"
    frames = [slide(opp["title"], opp["sa_note"] + "\n\n" + opp["cost_note"], "Way to earn online - South Africa",
                    shot if shot.exists() else None, illustration=not shot.exists())]
    for i, step in enumerate(opp["steps"], 1):
        frames.append(slide(f"Step {i}", step, opp["title"], shot if shot.exists() else None, illustration=not shot.exists()))
    frames.append(slide("Before you register", opp["warning"] + f"\n\nOfficial site: {opp['official_url']}",
                        "Stay safe", shot if shot.exists() else None, illustration=not shot.exists()))
    tmp = OUT / opp["id"]; tmp.mkdir(exist_ok=True)
    paths = []
    for i, fr in enumerate(frames):
        p = tmp / f"{i:03d}.png"; fr.save(p); paths.append(p)
    mp4 = OUT / f"{opp['id']}.mp4"
    if shutil.which("ffmpeg"):
        lst = tmp / "list.txt"
        lst.write_text("".join(f"file '{p}'\nduration {SECONDS_PER_SLIDE}\n" for p in paths) + f"file '{paths[-1]}'\n")
        subprocess.run(["ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", str(lst),
                        "-vf", f"fps={FPS},format=yuv420p", "-an", "-movflags", "+faststart", str(mp4)],
                       check=True, capture_output=True)
        print("MP4", mp4)
    else:
        gif = OUT / f"{opp['id']}.gif"
        frames[0].save(gif, save_all=True, append_images=frames[1:], duration=SECONDS_PER_SLIDE * 1000, loop=0)
        print("GIF (ffmpeg not found)", gif)

def main():
    opps = json.loads((ROOT / "data/opportunities.json").read_text())[:3]  # 3 videos per day
    for o in opps: build(o)
    print(f"Built {len(opps)} silent videos in {OUT}")

if __name__ == "__main__": main()
