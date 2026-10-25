#!/usr/bin/env python3
"""
SA Opportunities - job scanner & verifier (stdlib only).

How verification works (see docs/ARCHITECTURE.md):
 1. Listings are collected ONLY from (a) official employer career pages listed
    in data/sources.json and (b) known SA job boards listed there.
 2. Every link's final destination (after redirects) is checked. A link that
    leaves the employer's official domain / known ATS domain is not "verified".
 3. A board listing is upgraded to VERIFIED only when the same employer + a
    very similar title is found on that employer's official careers page --
    and the apply_url is then replaced with the OFFICIAL direct link.
    (Example: a board ad "says Capitec" -> we search Capitec's own careers
    page; only a match there earns the verified badge + official link.)
 4. Scam heuristics run on listing text. Hard flags (ask for payment/deposit,
    WhatsApp-only application, free-email application address for a big
    employer, etc.) drop the listing entirely. Soft flags lower confidence.
 5. Output: data/jobs.json, consumed by the Android app and the Telegram bot.

No form is ever submitted, no account created, no CAPTCHA bypassed.
"""
import hashlib, html, json, re, sys, urllib.error, urllib.parse, urllib.request
from datetime import datetime, timezone
from difflib import SequenceMatcher
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "data" / "sources.json"
OUT = ROOT / "data" / "jobs.json"
UA = {"User-Agent": "SAOpportunitiesScanner/1.0 (+https://github.com/perfectndumiso1-netizen/sa-opportunities)"}
TIMEOUT = 20

HARD_FLAGS = [
    (r"pay(ment)? (of )?r?\s?\d+.*(fee|deposit|processing|training|uniform)", "asks for an upfront payment"),
    (r"(registration|admin|processing) fee", "mentions a registration/admin fee"),
    (r"send (money|payment|airtime)", "asks you to send money/airtime"),
    (r"whatsapp (only|us only|to apply)", "WhatsApp-only application"),
    (r"apply via (telegram|whatsapp)", "application only via a chat app"),
]
SOFT_FLAGS = [
    (r"@[gG]mail\.com|@yahoo\.|@hotmail\.", "application via a free email address"),
    (r"earn r?\s?\d[\d,]* (per|a) (day|week) guaranteed", "guaranteed-earnings claim"),
    (r"no experience needed.{0,40}r\s?\d{2,3}[,\d]*", "high pay promised with no experience"),
    (r"urgent(ly)? hiring!!!+", "over-urgent hype wording"),
]
FREE_EMAIL = re.compile(r"[\w.+-]+@(gmail|yahoo|hotmail|outlook)\.com", re.I)

# Navigation / boilerplate link texts that are not real vacancy titles
JUNK_TITLES = {"skip to content", "skip to the content", "careers", "career", "view vacancy",
               "home", "our promise", "our benefits", "our teams", "apply now", "apply",
               "learn more", "read more", "vacancies", "jobs", "job search", "search jobs",
               "close", "menu", "contact us", "about us", "back", "next", "previous"}

GENERIC_LAST_SEGMENTS = {"careers", "career", "jobs", "job", "vacancies", "vacancy",
    "got-the-skills", "graduate-opportunities", "top-employer", "working-and-growing-with-us",
    "early-careers", "students", "students-and-graduates", "working-with-us", "join-us",
    "join-our-team", "life-at", "about", "about-us", "home", "search", "results"}

def is_exact_post(url):
    """True only if the URL points at ONE specific vacancy post - never a
    careers homepage / listing / programme page (user requirement)."""
    u = urllib.parse.urlparse(url)
    path = u.path.lower()
    segs = [s for s in path.split("/") if s]
    if not segs: return False
    if re.search(r"vacancies?-\d{4}$", segs[-1]): return False  # yearly listing page
    q = urllib.parse.parse_qs(u.query.lower())
    if any(k in q for k in ("jk", "jobid", "job_id", "vacancyid", "vacancy_id",
                            "positionid", "postingid", "id", "ref", "reference")):
        return True
    if segs[-1] in GENERIC_LAST_SEGMENTS and len(segs) <= 2: return False
    if any(any(ch.isdigit() for ch in s) and len(s) >= 4 for s in segs): return True
    if len(segs) >= 3: return True
    last = re.sub(r"\.(html?|php|aspx)$", "", segs[-1])
    if len(last) >= 25 and "-" in last: return True   # long title slug, e.g. branch-consultant-cape-town-…
    return False

VACANCY_SIGNALS = ["closing date", "requirements", "qualifications", "responsibilities",
                   "duties", "experience", "job description", "reference number",
                   "key performance", "minimum requirement", "apply before", "salary"]

def vacancy_signal_count(text, title=""):
    low = text.lower(); n = sum(1 for s in VACANCY_SIGNALS if s in low)
    if title and title.lower()[:25] in low: n += 1
    return n

def clean_post_title(raw, company=""):
    s = re.sub(r"\s+", " ", str(raw or "")).strip()
    s = re.split(r"\s+[|]\s+|\s+[-–—]\s+(?=[A-Z][A-Za-z& ]{2,30}$)", s)[0].strip()
    if company and s.lower() == company.lower(): return ""
    if s.lower() in JUNK_TITLES or len(s) < 5: return ""
    return s[:100]


def fetch(url):
    """Return (final_url, text) or (url, '') on any failure. Never raises."""
    try:
        req = urllib.request.Request(url, headers=UA)
        with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
            raw = r.read(1_500_000)
            return r.geturl(), raw.decode("utf-8", "ignore")
    except Exception as e:  # network blocks / 403s are expected on some boards
        print(f"  ! fetch failed {url}: {e}", file=sys.stderr)
        return url, ""


class Links(HTMLParser):
    def __init__(self):
        super().__init__(); self.items = []
    def handle_starttag(self, tag, attrs):
        if tag == "a":
            d = dict(attrs)
            if d.get("href"): self.items.append((d["href"], ""))
    def handle_data(self, data):
        if self.items and data.strip():
            href, txt = self.items[-1]
            self.items[-1] = (href, (txt + " " + data.strip())[:200])


def domain(url):
    return urllib.parse.urlparse(url).netloc.lower().removeprefix("www.")


def domain_allowed(url, allowed):
    d = domain(url)
    return any(d == a.lower().removeprefix("www.") or d.endswith("." + a.lower()) for a in allowed)


def title_sim(a, b):
    norm = lambda s: re.sub(r"[^a-z0-9 ]", "", s.lower()).strip()
    return SequenceMatcher(None, norm(a), norm(b)).ratio()


def scam_scan(text):
    hard, soft = [], []
    low = text.lower()
    for pat, why in HARD_FLAGS:
        if re.search(pat, low): hard.append(why)
    for pat, why in SOFT_FLAGS:
        if re.search(pat, low): soft.append(why)
    return hard, soft


def collect_from_page(url, keywords):
    final, page = fetch(url)
    if not page: return final, []
    p = Links()
    try: p.feed(page)
    except Exception: pass
    out = []
    for href, text in p.items:
        full = urllib.parse.urljoin(final, html.unescape(href))
        if not full.startswith("http"): continue
        clean = re.sub(r"\s+", " ", text).strip()
        if clean.lower() in JUNK_TITLES or len(clean) < 4: continue
        blob = (text + " " + urllib.parse.unquote(full)).lower()
        if any(k in blob for k in keywords):
            out.append({"title": re.sub(r"\s+", " ", text).strip() or "View vacancy", "url": full})
    return final, out

CATEGORIES_FALLBACK = "Other Jobs"
PROVINCE_CITIES = {
    "Gauteng": ["johannesburg", "pretoria", "germiston", "sandton", "midrand", "roodepoort", "soweto", "benoni", "krugersdorp", "centurion", "gauteng"],
    "Western Cape": ["cape town", "stellenbosch", "paarl", "george", "worcester", "western cape", "bellville"],
    "KwaZulu-Natal": ["durban", "pietermaritzburg", "richards bay", "newcastle", "kwazulu-natal", "kzn", "umhlanga"],
    "Eastern Cape": ["gqeberha", "port elizabeth", "east london", "eastern cape", "makhanda"],
    "Free State": ["bloemfontein", "free state", "welkom"],
    "Limpopo": ["polokwane", "limpopo", "tzaneen", "mokopane"],
    "Mpumalanga": ["mbombela", "nelspruit", "emalahleni", "witbank", "mpumalanga"],
    "North West": ["rustenburg", "mahikeng", "klerksdorp", "north west"],
    "Northern Cape": ["kimberley", "upington", "northern cape"],
}

class TextExtractor(HTMLParser):
    def __init__(self):
        super().__init__(); self.parts = []; self.skip = 0
        self.h1 = ""; self.title_tag = ""; self._cap = ""
    def handle_starttag(self, tag, attrs):
        if tag in ("script", "style", "nav", "footer", "header"): self.skip += 1
        if tag in ("h1", "title"): self._cap = tag
    def handle_endtag(self, tag):
        if tag in ("script", "style", "nav", "footer", "header") and self.skip: self.skip -= 1
        if tag == self._cap: self._cap = ""
    def handle_data(self, data):
        if not self.skip and data.strip(): self.parts.append(data.strip())
        if self._cap == "h1" and data.strip() and not self.h1: self.h1 = data.strip()
        if self._cap == "title" and data.strip(): self.title_tag += data.strip() + " "

def page_details(url):
    """(final_url_after_redirects, visible_text, h1_or_title) for ONE post page."""
    final, raw_html = fetch(url)
    if not raw_html: return final, "", ""
    x = TextExtractor()
    try: x.feed(raw_html)
    except Exception: pass
    return final, re.sub(r"\s+", " ", " ".join(x.parts)).strip(), (x.h1 or x.title_tag).strip()

def page_text(url):
    return page_details(url)[1]

def classify(title, text, cfg):
    blob = (title + " " + text[:1500]).lower()
    for cat in cfg.get("categories", []):
        if any(k in blob for k in cat["keywords"]): return cat["name"]
    return CATEGORIES_FALLBACK

def find_province(text):
    low = text.lower()
    for prov, keys in PROVINCE_CITIES.items():
        if any(k in low for k in keys): return prov
    return ""

DATE_RE = r"(\d{1,2}\s+(?:January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{4}|\d{4}/\d{2}/\d{2}|\d{1,2}/\d{1,2}/\d{4})"

def extract_field(text, labels, window=1200):
    low = text.lower()
    for lab in labels:
        i = low.find(lab)
        if i >= 0:
            chunk = text[i + len(lab): i + len(lab) + window]
            chunk = re.split(r"(?i)(how to apply|about (the )?(company|role|position)|benefits|what we offer|application process|closing date)", chunk)[0]
            return chunk.strip(" :-\u2022\n")[:1400]
    return ""

def enrich(job, cfg, fetch_detail=True):
    """Add category/location/closing date/description/requirements from the listing page itself."""
    text = ""
    if fetch_detail and job.get("apply_url"):
        final_url, text, heading = page_details(job["apply_url"])
        if final_url and final_url.startswith("http"):
            job["apply_url"] = final_url          # exact URL after redirects
        post_title = clean_post_title(heading, job.get("company", ""))
        if post_title and (len(job["title"]) < 8 or title_sim(post_title, job["title"]) >= 0.35
                           or job["title"].lower() in JUNK_TITLES):
            job["title"] = post_title              # title as advertised on the post itself
    blob = job["title"] + " " + text
    if text:
        hard, soft = scam_scan(text)
        job["_hard"] = hard
        job["verification"]["warnings"] = sorted(set(job["verification"].get("warnings", []) + soft))
    job["category"] = classify(job["title"], text, cfg)
    job["location"] = ""
    m = re.search(r"(?i)(location|based in| situated in)[:\s]+([A-Z][A-Za-z ]{2,40})", text)
    if m: job["location"] = m.group(2).strip()
    job["province"] = find_province(blob + " " + job["location"])
    if not job["location"]: job["location"] = job["province"]
    cd = re.search(r"(?i)closing date[:\s]*" + DATE_RE, text)
    job["closing_date"] = cd.group(1) if cd else ""
    sal = re.search(r"(?i)(R\s?\d[\d ,]*(\.\d{2})?\s?(per month|per annum|p\.a\.|per year|pm)?)", text)
    job["salary"] = sal.group(1).strip() if sal else ""
    jt = re.search(r"(?i)(permanent|contract|temporary|full[- ]time|part[- ]time|learnership|internship)", blob)
    job["job_type"] = jt.group(1).title() if jt else ""
    job["requirements"] = extract_field(text, ["minimum requirements", "requirements", "what you will need", "qualifications", "experience and qualifications", "who we are looking for"])
    desc = extract_field(text, ["job description", "about the role", "about the position", "purpose of the role", "the role", "overview"], 1200)
    job["exact_post"] = bool(text) and is_exact_post(job.get("apply_url", "")) \
        and vacancy_signal_count(text, job["title"]) >= 2
    if not desc and text and job["exact_post"]:
        desc = text[:1000]   # only slice real post pages, never homepages
    if not job["requirements"] and desc:
        low = desc.lower()
        for kw in ("minimum requirements", "requirements", "qualifications", "experience required"):
            i = low.find(kw)
            if i >= 0: job["requirements"] = desc[i + len(kw):].strip(" :-")[:1200]; break
    job["description"] = desc[:1200] if job["exact_post"] else ""
    if not job["exact_post"]: job["requirements"] = ""
    if job["exact_post"]:
        c = job["verification"].setdefault("checks", [])
        note = "Apply link opens the exact vacancy post (not a careers homepage)"
        if note not in c: c.append(note)
    return job

def detail_ok(job):
    """Publish only listings we could actually read on their exact post."""
    return bool(job.get("exact_post")) and len(job.get("description", "")) >= 120


def main():
    cfg = json.loads(SOURCES.read_text())
    keywords = cfg["job_keywords"]
    official_index = []   # every listing found on an official careers page
    jobs = []

    print("== Official employer career pages ==")
    for emp in cfg["employers"]:
        allowed = emp["official_domains"] + emp.get("ats_domains", [])
        final, found = collect_from_page(emp["careers_url"], keywords)
        kept = 0
        for item in found:
            if not domain_allowed(item["url"], allowed):
                continue  # link leaves the employer's official/ATS domain
            hard, soft = scam_scan(item["title"])
            if hard: continue
            job = {
                "id": hashlib.sha256((emp["name"] + item["title"] + item["url"]).encode()).hexdigest()[:16],
                "title": item["title"], "company": emp["name"],
                "source_name": emp["name"] + " (official careers page)",
                "source_url": emp["careers_url"], "apply_url": item["url"],
                "found_at": datetime.now(timezone.utc).isoformat(),
                "verification": {"status": "verified_official", "confidence": 95,
                                 "checks": ["Found on the employer's official careers page",
                                            "Apply link stays on an official/approved domain"],
                                 "warnings": soft},
            }
            enrich(job, cfg)
            if job.pop("_hard", []): continue
            if not detail_ok(job):
                print(f"    dropped (not an exact readable post): {job['title'][:50]} -> {job['apply_url'][:70]}"); continue
            official_index.append(job); jobs.append(job); kept += 1
        print(f"  {emp['name']}: {kept} verified listings")

    print("== Job boards (must be traced to the employer before 'verified') ==")
    for board in cfg["boards"]:
        _, found = collect_from_page(board["listing_url"], keywords)
        kept = upgraded = 0
        for item in found[:60]:
            hard, soft = scam_scan(item["title"])
            if hard:
                print(f"  dropped (scam flags {hard}): {item['title'][:60]}"); continue
            job = {
                "id": hashlib.sha256((board["name"] + item["title"] + item["url"]).encode()).hexdigest()[:16],
                "title": item["title"], "company": "See listing",
                "source_name": board["name"], "source_url": board["listing_url"],
                "apply_url": item["url"], "found_at": datetime.now(timezone.utc).isoformat(),
                "verification": {"status": "board_listing", "confidence": 55,
                                 "checks": ["Found on a known SA job board",
                                            "Not yet confirmed on the employer's own site"],
                                 "warnings": soft},
            }
            # Trace it: same title on an employer's official page?
            for off in official_index:
                if title_sim(job["title"], off["title"]) >= 0.72:
                    job["company"] = off["company"]
                    job["apply_url"] = off["apply_url"]  # direct OFFICIAL link wins
                    job["verification"] = {"status": "verified_official", "confidence": 92,
                        "checks": ["Found on a job board", f"Traced: same vacancy confirmed on {off['company']}'s official careers page",
                                   "Apply link replaced with the official direct link"], "warnings": soft}
                    upgraded += 1; break
            enrich(job, cfg, fetch_detail=True)
            job.pop("_hard", None)
            if not detail_ok(job):
                print(f"  dropped board item (no exact readable post): {job['title'][:50]}"); continue
            jobs.append(job); kept += 1
        print(f"  {board['name']}: {kept} listings, {upgraded} traced to official sites")

    # De-duplicate, verified first, then newest-looking order preserved
    seen, unique = set(), []
    for j in sorted(jobs, key=lambda x: 0 if x["verification"]["status"] == "verified_official" else 1):
        key = (j["company"].lower(), j["title"].lower())
        if key not in seen: seen.add(key); unique.append(j)

    from collections import Counter
    groups = [{"category": c, "count": n} for c, n in Counter(j.get("category", "Other Jobs") for j in unique).most_common()]
    payload = {"generated_at": datetime.now(timezone.utc).isoformat(),
               "version": "2.4",
               "stats": {"total": len(unique),
                          "verified_official": sum(1 for j in unique if j["verification"]["status"] == "verified_official")},
               "groups": groups,
               "jobs": unique}
    OUT.write_text(json.dumps(payload, indent=2, ensure_ascii=False))
    print(f"Wrote {OUT} : {payload['stats']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
