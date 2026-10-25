#!/usr/bin/env python3
"""Send NEW verified jobs to Telegram. Secrets come from env only:
TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID (GitHub Actions secrets). Never in code/APK."""
import json, os, sys, urllib.request, urllib.parse
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
STATE = ROOT / "data" / ".notified.json"

def send(token, chat, text):
    data = urllib.parse.urlencode({"chat_id": chat, "text": text, "parse_mode": "HTML",
                                   "disable_web_page_preview": "true"}).encode()
    req = urllib.request.Request(f"https://api.telegram.org/bot{token}/sendMessage", data=data)
    with urllib.request.urlopen(req, timeout=30) as r: return r.status == 200

def main():
    token, chat = os.environ.get("TELEGRAM_BOT_TOKEN"), os.environ.get("TELEGRAM_CHAT_ID")
    if not token or not chat:
        print("Telegram secrets not set - skipping notification."); return 0
    jobs = json.loads((ROOT/"data/jobs.json").read_text()).get("jobs", [])
    seen = set(json.loads(STATE.read_text())) if STATE.exists() else set()
    fresh = [j for j in jobs if j["id"] not in seen and j["verification"]["status"] == "verified_official"]
    for j in fresh[:10]:
        badge = "✅ VERIFIED on official site"
        meta = " • ".join(x for x in [j.get('category',''), j.get('location',''), ("Closes: "+j['closing_date']) if j.get('closing_date') else ""] if x)
        msg = (f"<b>{j['title']}</b>\n{j['company']}" + (f"\n{meta}" if meta else "") + f"\n{badge} "
               f"(confidence {j['verification']['confidence']}%)\nApply (official link): {j['apply_url']}")
        if send(token, chat, msg): seen.add(j["id"])
    STATE.write_text(json.dumps(sorted(seen)))
    print(f"Notified {len(fresh[:10])} new verified jobs."); return 0

if __name__ == "__main__": raise SystemExit(main())
