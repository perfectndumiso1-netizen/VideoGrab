#!/usr/bin/env python3
"""
SA Opportunities - Telegram autoposter.

Reads credentials ONLY from environment (GitHub Actions secrets):
    TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID
Never hard-code them, never put them in the APK or in this repo.

Modes:
  --mode new     Post NEW verified jobs, grouped by category (Cashiers,
                 Security, Drivers...). First ever run seeds the current
                 backlog silently so the channel is not flooded.
  --mode digest  One daily summary: counts per category/province, and the
                 verified jobs closing soonest.
  --mode test    Send a single test message (use once after adding secrets).
  --dry-run      Print the messages instead of sending (works with no token).

State lives in data/.autopost.json (posted ids + last digest date) and is
committed back by the workflows, so nothing is posted twice.
"""
import argparse, html, json, os, sys, time, urllib.parse, urllib.request
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STATE = ROOT / "data" / ".autopost.json"
MAX_MSG = 3500          # Telegram limit is 4096; stay safely under
MAX_JOBS_PER_RUN = 60

CATEGORY_EMOJI = {
    "Cashiers & Retail": "🛒", "Security": "🛡️", "Drivers & Logistics": "🚚",
    "Cleaners & General Workers": "🧹", "Healthcare": "🏥", "Education": "🎓",
    "Office & Admin": "🗄️", "Banking & Finance": "🏦", "IT & Technology": "💻",
    "Engineering & Trades": "🔧", "Hospitality & Food": "🍽️",
    "Learnerships & Internships": "📚", "Government & Municipal": "🏛️",
    "Other Jobs": "📌",
}

def esc(x): return html.escape(str(x or ""))

def load_state():
    if STATE.exists():
        try: return json.loads(STATE.read_text())
        except Exception: pass
    return {"posted_ids": [], "last_digest": ""}

def save_state(s): STATE.write_text(json.dumps(s, indent=1))

def load_jobs():
    try: return json.loads((ROOT / "data/jobs.json").read_text()).get("jobs", [])
    except Exception: return []

def verified(jobs):
    return [j for j in jobs if j.get("verification", {}).get("status") == "verified_official"]

def job_line(i, j):
    meta = " • ".join(x for x in [j.get("location", ""), j.get("province", ""),
                                  ("⏰ Closes " + j["closing_date"]) if j.get("closing_date") else "",
                                  j.get("job_type", "")] if x)
    out = f"{i}. <b>{esc(j['title'])}</b>\n{esc(j['company'])}"
    if meta: out += f"\n{esc(meta)}"
    out += f"\nApply on the official site: {esc(j['apply_url'])}"
    return out

def build_grouped_messages(jobs):
    """One message per category (chunked), header + numbered jobs."""
    groups = defaultdict(list)
    for j in jobs: groups[j.get("category") or "Other Jobs"].append(j)
    messages = []
    for cat in sorted(groups, key=lambda c: -len(groups[c])):
        emoji = CATEGORY_EMOJI.get(cat, "📌")
        header = f"{emoji} <b>{esc(cat)}</b> — {len(groups[cat])} new verified job(s)\n✅ Confirmed on the employer's official website\n"
        chunk, count = header, 0
        for j in groups[cat]:
            line = job_line(count + 1, j) + "\n\n"
            if len(chunk) + len(line) > MAX_MSG and count:
                messages.append(chunk.strip()); chunk, count = header, 0
                line = job_line(1, j) + "\n\n"
            chunk += line; count += 1
        if count: messages.append(chunk.strip())
    return messages

def build_digest(jobs):
    ver = verified(jobs)
    by_cat, by_prov = defaultdict(int), defaultdict(int)
    for j in ver:
        by_cat[j.get("category") or "Other Jobs"] += 1
        if j.get("province"): by_prov[j["province"]] += 1
    today = datetime.now(timezone.utc).strftime("%d %B %Y")
    lines = [f"🇿🇦 <b>SA Opportunities — daily summary ({today})</b>",
             f"✅ {len(ver)} verified jobs currently open (traced to official employer sites), {len(jobs)} listings tracked in total.\n",
             "<b>By type:</b>"]
    for cat, n in sorted(by_cat.items(), key=lambda kv: -kv[1]):
        lines.append(f"{CATEGORY_EMOJI.get(cat, '📌')} {esc(cat)}: {n}")
    if by_prov:
        lines.append("\n<b>By province:</b>")
        for prov, n in sorted(by_prov.items(), key=lambda kv: -kv[1]): lines.append(f"📍 {esc(prov)}: {n}")
    closing = sorted([j for j in ver if j.get("closing_date")], key=lambda j: j["closing_date"])[:5]
    if closing:
        lines.append("\n<b>⏰ Closing soonest (apply first):</b>")
        for i, j in enumerate(closing, 1): lines.append(job_line(i, j))
    lines.append("\n🚫 Real employers never charge you to apply. New verified jobs are posted here as the scanner finds them (every ±15 min).")
    return "\n".join(lines)

def send(token, chat, text):
    data = urllib.parse.urlencode({"chat_id": chat, "text": text, "parse_mode": "HTML",
                                   "disable_web_page_preview": "true"}).encode()
    req = urllib.request.Request(f"https://api.telegram.org/bot{token}/sendMessage", data=data)
    with urllib.request.urlopen(req, timeout=30) as r: return r.status == 200

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--mode", choices=["new", "digest", "test"], default="new")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    jobs = load_jobs(); state = load_state()
    token, chat = os.environ.get("TELEGRAM_BOT_TOKEN"), os.environ.get("TELEGRAM_CHAT_ID")

    if args.mode == "test":
        messages = ["✅ <b>SA Opportunities autoposter is live!</b>\nNew ✅ verified SA jobs will be posted here automatically (grouped by type), plus a daily summary.\n🚫 Remember: real employers never charge you to apply for a job."]
    elif args.mode == "digest":
        messages = [build_digest(jobs)]
    else:
        posted = set(state.get("posted_ids", []))
        fresh = [j for j in verified(jobs) if j["id"] not in posted]
        if not posted and fresh and not args.dry_run:
            # First run ever: do not flood the channel with the whole backlog.
            state["posted_ids"] = sorted({j["id"] for j in verified(jobs)})
            save_state(state)
            print(f"Seeded {len(state['posted_ids'])} existing verified jobs; only NEW ones will be posted from now on.")
            return 0
        fresh = fresh[:MAX_JOBS_PER_RUN]
        messages = build_grouped_messages(fresh)
        if not args.dry_run:
            state["posted_ids"] = sorted(posted | {j["id"] for j in fresh})

    if args.dry_run:
        print(f"--- {len(messages)} message(s) would be sent ---")
        for m in messages: print(m, "\n---")
        return 0
    if not token or not chat:
        print("TELEGRAM_BOT_TOKEN / TELEGRAM_CHAT_ID not set - add them as GitHub Actions secrets. Skipping.")
        return 0
    sent = 0
    for m in messages:
        try:
            if send(token, chat, m): sent += 1
            time.sleep(1)  # be gentle with Telegram rate limits
        except Exception as e:
            print(f"send failed: {e}", file=sys.stderr)
    if args.mode == "digest": state["last_digest"] = datetime.now(timezone.utc).date().isoformat()
    save_state(state)
    print(f"Sent {sent}/{len(messages)} message(s) [{args.mode}].")
    return 0 if sent == len(messages) else 1

if __name__ == "__main__":
    raise SystemExit(main())
