
"""
Bulk flag submitter for MSG CTF
- Reads exported CSV (teamName, name, loginId, email, password)
- For each team, logs in with the "odd" member (loginId trailing digits odd); if not detectable, uses first member
- Submits the same flag to one challenge

Usage:
  pip install requests
  python bulk_flag_submit.py \
    --base-url https://msgctf.kr \
    --csv generated_passwords.csv \
    --challenge-id 56 \
    --flag test10 \
    --mode odd-per-team \
    --workers 8 \
    --sleep 0.15

Modes:
- odd-per-team: pick the member in each team whose loginId ends with odd digits; fallback to first row
- first-per-team: simply pick the first row for each team
"""

from __future__ import annotations

import argparse
import csv
import time
import re
from dataclasses import dataclass
from typing import Dict, List, Optional, Tuple
import threading
import concurrent.futures

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

@dataclass
class Cred:
    teamName: str
    name: str
    loginId: str
    email: str
    password: str

def make_session(total_retries: int = 2, backoff_factor: float = 0.2, timeout: int = 8) -> requests.Session:
    s = requests.Session()
    retries = Retry(
        total=total_retries,
        read=total_retries,
        connect=total_retries,
        backoff_factor=backoff_factor,
        status_forcelist=(429, 500, 502, 503, 504),
        allowed_methods=frozenset(["GET", "POST", "HEAD", "OPTIONS"]),
        raise_on_status=False,
    )
    adapter = HTTPAdapter(max_retries=retries, pool_connections=20, pool_maxsize=50)
    s.mount("http://", adapter)
    s.mount("https://", adapter)
    s.request_timeout = timeout
    s.headers.update({"Accept": "application/json", "Content-Type": "application/json"})
    return s

def _extract_token(payload: dict) -> Optional[str]:
    if not isinstance(payload, dict): return None
    d = payload.get("data") if isinstance(payload.get("data"), dict) else payload
    for k in ("accessToken", "token", "jwt"):
        v = d.get(k) if isinstance(d, dict) else None
        if isinstance(v, str) and v: return v
    return None

def signin(session: requests.Session, base_url: str, login_id: str, password: str, xff: Optional[str]=None) -> str:
    url = f"{base_url.rstrip('/')}/api/users/sign-in"
    headers = {}
    if xff: headers["X-Forwarded-For"] = xff
    r = session.post(url, json={"loginId": login_id, "password": password}, headers=headers, timeout=session.request_timeout)
    if r.status_code != 200:
        raise RuntimeError(f"Sign-in failed ({login_id}): {r.status_code} {r.text[:200]}")
    tok = _extract_token(r.json())
    if not tok: raise RuntimeError(f"Token not found in sign-in response for {login_id}")
    return tok

def submit_flag(session: requests.Session, base_url: str, challenge_id: int, token: str, flag: str, xff: Optional[str]=None) -> requests.Response:
    url = f"{base_url.rstrip('/')}/api/challenges/{challenge_id}/submit"
    headers = {"Authorization": f"Bearer {token}"}
    if xff: headers["X-Forwarded-For"] = xff
    return session.post(url, json={"submitFlag": flag}, headers=headers, timeout=session.request_timeout)

def load_creds(csv_path: str) -> List[Cred]:
    creds: List[Cred] = []
    with open(csv_path, "r", newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        need = ["teamName","name","loginId","email","password"]
        for k in need:
            if k not in reader.fieldnames:
                raise ValueError(f"CSV missing column '{k}'. Found: {reader.fieldnames}")
        for r in reader:
            creds.append(Cred(
                teamName=(r.get("teamName") or "").strip(),
                name=(r.get("name") or "").strip(),
                loginId=(r.get("loginId") or "").strip(),
                email=(r.get("email") or "").strip(),
                password=(r.get("password") or "").strip(),
            ))
    if not creds:
        raise ValueError("CSV appears empty")
    return creds

def select_accounts(creds: List[Cred], mode: str = "odd-per-team") -> List[Cred]:
    # group by team
    by_team: Dict[str, List[Cred]] = {}
    for c in creds:
        by_team.setdefault(c.teamName, []).append(c)
    selected: List[Cred] = []
    for team, members in by_team.items():
        pick: Optional[Cred] = None
        if mode == "odd-per-team":
            # find member with trailing odd digits in loginId
            for m in members:
                m_digits = re.search(r"(\d+)$", m.loginId or "")
                if m_digits and (int(m_digits.group(1)) % 2 == 1):
                    pick = m
                    break
        # fallback to first member if not found or mode == first-per-team
        if not pick:
            pick = members[0]
        selected.append(pick)
    return selected

def main():
    ap = argparse.ArgumentParser(description="Bulk submit flag to a challenge using exported credentials.")
    ap.add_argument("--base-url", required=True)
    ap.add_argument("--csv", required=True, help="Exported CSV with teamName,name,loginId,email,password")
    ap.add_argument("--challenge-id", type=int, required=True)
    ap.add_argument("--flag", required=True)
    ap.add_argument("--mode", choices=["odd-per-team","first-per-team"], default="odd-per-team")
    ap.add_argument("--workers", type=int, default=8, help="Concurrent workers")
    ap.add_argument("--sleep", type=float, default=0.1, help="Sleep between teams (seconds)")
    ap.add_argument("--xff", default=None, help="Optional X-Forwarded-For to send (same for all requests)")
    args = ap.parse_args()

    session = make_session()
    creds = load_creds(args.csv)
    accounts = select_accounts(creds, mode=args.mode)

    ok = 0
    already = 0
    fail = 0
    lock = threading.Lock()

    def run_one(c: Cred):
        nonlocal ok, already, fail
        try:
            token = signin(session, args.base_url, c.loginId, c.password, xff=args.xff)
            resp = submit_flag(session, args.base_url, args.challenge_id, token, args.flag, xff=args.xff)
            sc = resp.status_code
            txt = resp.text[:200]
            with lock:
                if sc == 200:
                    ok += 1
                    print(f"[OK] {c.teamName}/{c.loginId}: submitted (200)")
                elif sc in (400, 409):
                    already += 1
                    print(f"[SKIP] {c.teamName}/{c.loginId}: {sc} {txt}")
                else:
                    fail += 1
                    print(f"[ERR] {c.teamName}/{c.loginId}: {sc} {txt}")
        except Exception as e:
            with lock:
                fail += 1
                print(f"[ERR] {c.teamName}/{c.loginId}: {e}")

    print(f"Selected accounts: {len(accounts)} teams")
    # Run with limited parallelism to avoid rate limiting
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as ex:
        futs = []
        for c in accounts:
            futs.append(ex.submit(run_one, c))
            time.sleep(args.sleep)  # small stagger across teams
        for f in concurrent.futures.as_completed(futs):
            _ = f.result()

    print("==== SUMMARY ====")
    print(f"OK: {ok}, SKIP/ALREADY: {already}, FAIL: {fail}, TOTAL: {len(accounts)}")

if __name__ == "__main__":
    main()
