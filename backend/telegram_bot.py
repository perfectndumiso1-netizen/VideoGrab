#!/usr/bin/env python3
"""Interactive Telegram bot (long-polling). Run on any always-on host/VPS or
locally:  TELEGRAM_BOT_TOKEN=... python3 backend/telegram_bot.py
GitHub Actions itself is not always-on, so Actions only *sends* alerts
(telegram_notify.py); this polling bot is for chatting: /jobs /verified /help.
It reads the published jobs.json, so answers always show verification status."""
import json, os, time, urllib.request, urllib.parse
TOKEN = os.environ["TELEGRAM_BOT_TOKEN"]
JOBS_URL = os.environ.get("JOBS_JSON_URL",
    "https://raw.githubusercontent.com/perfectndumiso1-netizen/sa-opportunities/main/data/jobs.json")
API = f"https://api.telegram.org/bot{TOKEN}"

def call(method, **kw):
    data = urllib.parse.urlencode(kw).encode() if kw else None
    with urllib.request.urlopen(urllib.request.Request(f"{API}/{method}", data=data), timeout=40) as r:
        return json.loads(r.read())

def jobs():
    with urllib.request.urlopen(JOBS_URL, timeout=30) as r: return json.loads(r.read()).get("jobs", [])

def fmt(j):
    v = j["verification"]; badge = "✅ Verified official" if v["status"] == "verified_official" else "⚠️ Board listing (unverified)"
    return f"<b>{j['title']}</b>\n{j['company']} — {badge} ({v['confidence']}%)\n{j['apply_url']}"

def main():
    offset = 0; print("Bot running. /jobs /verified /help")
    while True:
        try:
            for u in call("getUpdates", offset=offset, timeout=30).get("result", []):
                offset = u["update_id"] + 1
                m = u.get("message", {}); text = (m.get("text") or "").strip().lower(); chat = m.get("chat", {}).get("id")
                if not chat: continue
                if text.startswith("/verified"): pool = [j for j in jobs() if j["verification"]["status"] == "verified_official"]
                elif text.startswith("/jobs"): pool = jobs()
                elif text.startswith("/help") or text.startswith("/start"):
                    call("sendMessage", chat_id=chat, text="/jobs = latest listings\n/verified = only jobs confirmed on the employer's official site\nWe never ask for payment to apply for a job.", parse_mode="HTML"); continue
                else: continue
                reply = "\n\n".join(fmt(j) for j in pool[:8]) or "No listings yet — the scanner runs on a schedule."
                call("sendMessage", chat_id=chat, text=reply, parse_mode="HTML", disable_web_page_preview="true")
        except Exception as e:
            print("poll error:", e); time.sleep(5)

if __name__ == "__main__": main()
