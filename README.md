# SA Opportunities 🇿🇦

> Project name: **sa-opportunities** (rename the GitHub repository to this -
> see `docs/SETUP.md` step 1; Arena's token was not permitted to rename it).

Finds South African job vacancies, **traces each one back to the employer's
official website before calling it verified**, filters scam listings, sends
alerts to Telegram, shows everything in an Android app, and generates
**3 silent (no voice/AI narration) videos per day** about legitimate ways to
earn online - with captions, steps and screenshots of public pages.

## Parts
| Folder | What |
|---|---|
| `backend/scanner.py` | Scheduled search + official-site tracing + scam filter → `data/jobs.json` |
| `backend/telegram_notify.py` | Sends new ✅ verified jobs to Telegram (GitHub Actions) |
| `backend/telegram_bot.py` | Interactive bot (`/jobs`, `/verified`) - run on any always-on host |
| `backend/video/` | Playwright screenshots of public pages + silent caption videos (3/day) |
| `android/` | Android app (Java) - lists jobs, badges, checks, official apply links |
| `data/sources.json` | Employers (official careers pages) + boards - add more here |
| `data/opportunities.json` | Topics for the daily videos |
| `.github/workflows/` | Scan 3×/day, daily videos, APK build |

## Safety rules built in
- ✅ Verified only when confirmed on the employer's own site; apply link is the official one.
- Payment requests / WhatsApp-only applications are dropped automatically.
- Videos: no voice, fictional demo details only (`demo@example.com`), drawn as
  overlays - **never typed into or submitted to a real site**; no accounts are
  created, no CAPTCHAs bypassed, no earnings promised.
- Secrets (Telegram token) live only in GitHub Actions secrets - never in code or the APK.

Start with **`docs/SETUP.md`**.
