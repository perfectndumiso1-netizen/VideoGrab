# How it works
GitHub Actions (scheduled) → backend/scanner.py → data/jobs.json (in repo)
   ├─ Android app reads jobs.json (raw.githubusercontent.com) - no server costs
   ├─ telegram_notify.py sends NEW verified jobs to Telegram
   └─ daily-videos.yml: screenshots (public pages only) → 3 silent MP4s → Telegram for approval

## Verification ("trace it before you trust it")
1. Collect from official employer career pages + known SA boards (data/sources.json).
2. Follow links; final domain must be the employer's official/ATS domain.
3. Board listings are traced: same title (≥72% similarity) on the employer's
   official page ⇒ status `verified_official` and apply_url REPLACED with the
   official direct link (e.g. Capitec's own site, not the board's copy).
4. Scam filter: payment/deposit requests, WhatsApp-only applications,
   chat-app-only applications ⇒ dropped. Free-email/guaranteed-pay hype ⇒ warnings.
5. Anything not traced stays `board_listing` (⚠️), never presented as verified.

## Honest limits
No tool searches "the whole internet" or proves a job is genuine. Some sites
block scrapers (you'll see fetch failures in Actions logs) — add sources/APIs
over time. A search-API key (e.g. Google Programmable Search) can be added as
a GitHub secret later to widen discovery; never put keys in the APK.
