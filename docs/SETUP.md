# Setup (only the repo owner can do the marked steps)

## 1. Rename the repository (owner only)
Arena's GitHub token got `403 Resource not accessible by integration` when it
tried to rename `VideoGrab`, so do it in GitHub: repo **Settings → General →
Repository name → `sa-opportunities` → Rename**. GitHub redirects old links.
Then update the single `JOBS_JSON_URL` line in `android/app/build.gradle` and
`backend/telegram_bot.py` if the owner/name ever changes again.

## 2. Create the NEW Telegram bot (owner only - never paste the token in chat)
1. In Telegram open **@BotFather** → `/newbot` → choose a name/username.
2. BotFather gives you a token like `123456:ABC...`. **Keep it secret.**
3. Open your new bot, press Start, then open
   `https://api.telegram.org/bot<YOUR_TOKEN>/getUpdates` in a browser after
   sending it a message to find your `chat.id` (or use @userinfobot).
4. In this GitHub repo: **Settings → Secrets and variables → Actions → New
   repository secret**: add `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID`.

## 3. Run it
- **Actions tab → "Scan & verify SA jobs" → Run workflow** (then it runs
  3×/day automatically). Results land in `data/jobs.json`.
- **Actions tab → "Build Android APK (debug)" → Run workflow** → download the
  `sa-opportunities-debug-apk` artifact and install it on your phone
  (allow "install from unknown sources" for your browser/files app).
- **Actions tab → "Daily silent videos" → Run workflow** → 3 silent MP4s are
  uploaded as an artifact and sent to your Telegram for approval before posting.

## 4. Optional: interactive bot
On any always-on computer/VPS: `TELEGRAM_BOT_TOKEN=... python3 backend/telegram_bot.py`
Commands: `/jobs`, `/verified`, `/help`.

## 5. Signing a release APK (later)
Create a keystore, keep it OUT of GitHub, and add signing config locally.
Never commit `*.jks`/`*.keystore` (already in .gitignore).

## Updates & signing (v2.2+)
Every release is signed with the same permanent key (`android/signing/sa-opportunities.p12`, committed by the owner's choice for this sideloaded app) and a higher `versionCode`, so users install new APKs as updates without uninstalling. Generate a new private key before any Play Store release. The job scanner runs every 15 minutes; videos remain 3 per day.
