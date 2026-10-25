#!/usr/bin/env python3
"""Capture PUBLIC pages only (Playwright). Never types real credentials,
never submits a form, never bypasses CAPTCHA. Output: screenshots/<id>.png
    pip install playwright && playwright install chromium
    python3 backend/video/capture_screenshots.py"""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent.parent
try:
    from playwright.sync_api import sync_playwright
except ImportError:
    raise SystemExit("Install first: pip install playwright && playwright install chromium")
opps = json.loads((ROOT/"data/opportunities.json").read_text())
out = ROOT/"screenshots"; out.mkdir(exist_ok=True)
with sync_playwright() as p:
    b = p.chromium.launch()
    page = b.new_page(viewport={"width": 1080, "height": 1350})
    for o in opps:
        try:
            page.goto(o["official_url"], timeout=45000, wait_until="domcontentloaded")
            page.wait_for_timeout(2500)
            page.screenshot(path=str(out/f"{o['id']}.png"))
            print("saved", o["id"])
        except Exception as e:
            print("skip", o["id"], e)
    b.close()
