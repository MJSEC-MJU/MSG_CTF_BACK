#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import argparse
import csv
import sys
import time
import secrets
import hashlib
import base64
import re
from dataclasses import dataclass
from typing import Dict, List, Optional, Tuple
from urllib.parse import urlparse

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

# ===== Models =====
@dataclass
class Member:
    email: str
    loginId: str
    password: str
    univ: str
    role: str = "ROLE_USER"
    teamName: Optional[str] = None
    name: Optional[str] = None

# ===== Helpers (policy) =====
_ALLOWED_SPECIAL = "!@#$%^&*"
_LOWER = "abcdefghijklmnopqrstuvwxyz"
_UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
_DIGITS = "0123456789"
_ALLOWED_ALL = _LOWER + _UPPER + _DIGITS + _ALLOWED_SPECIAL

def derive_login_from_email(email: str, used: set, lowercase: bool = True) -> str:
    local = (email or '').split('@')[0]
    base = re.sub(r'[^A-Za-z0-9]', '', local)
    if lowercase:
        base = base.lower()
    if not base:
        base = "user" + ''.join(secrets.choice(_DIGITS) for _ in range(3))
    candidate = base
    suffix = 2
    while candidate in used:
        candidate = f"{base}{suffix}"
        suffix += 1
    used.add(candidate)
    return candidate

def generate_policy_password(length: int = 16) -> str:
    length = max(8, int(length))
    chars = [
        secrets.choice(_LOWER),
        secrets.choice(_UPPER),
        secrets.choice(_DIGITS),
        secrets.choice(_ALLOWED_SPECIAL),
    ]
    for _ in range(length - 4):
        chars.append(secrets.choice(_ALLOWED_ALL))
    secrets.SystemRandom().shuffle(chars)
    return ''.join(chars)

def validate_password_policy(pw: str) -> bool:
    if not pw or len(pw) < 8: return False
    has_lower = any(c in _LOWER for c in pw)
    has_upper = any(c in _UPPER for c in pw)
    has_digit = any(c in _DIGITS for c in pw)
    has_special = any(c in _ALLOWED_SPECIAL for c in pw)
    all_allowed = all((c in _ALLOWED_ALL) for c in pw)
    return has_lower and has_upper and has_digit and has_special and all_allowed

def gen_password(mode: str = "hex64") -> str:
    m = (mode or "hex64").lower()
    if m == "hex64":  return secrets.token_hex(32)
    if m == "hex32":  return secrets.token_hex(16)
    if m == "sha256": return hashlib.sha256(secrets.token_bytes(32)).hexdigest()
    if m == "base64": return base64.urlsafe_b64encode(secrets.token_bytes(32)).decode().rstrip("=")
    return secrets.token_hex(32)

# ===== HTTP =====
def make_session(total_retries: int = 3, backoff_factor: float = 0.5, timeout: int = 10) -> requests.Session:
    s = requests.Session()
    retries = Retry(
        total=total_retries,
        read=total_retries,
        connect=total_retries,
        backoff_factor=backoff_factor,
        status_forcelist=(429, 500, 502, 503, 504),
        allowed_methods=frozenset(["GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"]),
        raise_on_status=False,
    )
    adapter = HTTPAdapter(max_retries=retries, pool_connections=20, pool_maxsize=50)
    s.mount("http://", adapter)
    s.mount("https://", adapter)
    s.request_timeout = timeout
    return s

def _extract_token(payload: dict) -> Optional[str]:
    if not isinstance(payload, dict): return None
    d = payload.get("data") if isinstance(payload.get("data"), dict) else payload
    for k in ("accessToken", "token", "jwt"):
        v = d.get(k) if isinstance(d, dict) else None
        if isinstance(v, str) and v: return v
    return None

def _signin(session: requests.Session, base_url: str, login_id: str, password: str,
            xff: Optional[str] = None, timeout: int = 10) -> str:
    url = f"{base_url.rstrip('/')}/api/users/sign-in"
    headers = {"Accept": "application/json", "Content-Type": "application/json"}
    if xff: headers["X-Forwarded-For"] = xff
    r = session.post(url, json={"loginId": login_id, "password": password},
                     headers=headers, timeout=timeout)
    if r.status_code != 200:
        raise RuntimeError(f"Sign-in failed ({login_id}): {r.status_code} {r.text[:200]}")
    data = r.json()
    token = _extract_token(data)
    if not token:
        raise RuntimeError(f"Token not found in response: {data}")
    return token

# ===== CSV & API =====
def load_members_from_csv(csv_path: str, team_col: str = "teamName") -> Tuple[List[Member], bool, bool]:
    members: List[Member] = []
    with open(csv_path, "r", newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        headers = [h.strip() for h in (reader.fieldnames or [])]
        has_team_col = team_col in headers
        has_name_col = "name" in headers
        for r in reader:
            for k in ("email", "univ", "role"):
                if k not in r or not r[k]:
                    raise ValueError(f"CSV row missing required field '{k}': {r}")
            team = r.get(team_col, "").strip() if has_team_col else None
            name = (r.get("name") or "").strip() if has_name_col else None
            members.append(Member(
                email=(r.get("email") or "").strip(),
                loginId=(r.get("loginId") or "").strip(),      # will be overridden from email
                password=(r.get("password") or "").strip(),    # may be overridden
                univ=(r.get("univ") or "").strip(),
                role=((r.get("role") or "ROLE_USER").strip() or "ROLE_USER"),
                teamName=team or None,
                name=name or None,
            ))
    if not members:
        raise ValueError("CSV appears empty.")
    return members, has_team_col, has_name_col

def register_member(session: requests.Session, base_url: str, m: Member) -> bool:
    url = f"{base_url.rstrip('/')}/api/admin/add/member"
    body = {"email": m.email, "loginId": m.loginId, "password": m.password, "univ": m.univ, "role": m.role}
    resp = session.post(url, json=body, timeout=session.request_timeout)
    if resp.status_code in (200, 201):
        print(f"[OK] Registered: {m.email}")
        return True
    elif resp.status_code == 409:
        print(f"[SKIP] Already exists (409): {m.email} | detail={resp.text[:200]}")
        return True
    else:
        print(f"[ERR] Register failed: {m.email} | {resp.status_code} | {resp.text[:500]}")
        return False

def create_team(session: requests.Session, base_url: str, team_name: str) -> bool:
    url = f"{base_url.rstrip('/')}/api/admin/team/create"
    resp = session.post(url, params={"teamName": team_name}, timeout=session.request_timeout)
    if resp.status_code in (200, 201):
        print(f"[OK] Team created: {team_name}")
        return True
    elif resp.status_code == 409:
        print(f"[SKIP] Team already exists (409): {team_name} | detail={resp.text[:200]}")
        return True
    else:
        print(f"[ERR] Create team failed: {team_name} | {resp.status_code} | {resp.text[:500]}")
        return False

def add_member_to_team(session: requests.Session, base_url: str, team_name: str, email: str) -> bool:
    url = f"{base_url.rstrip('/')}/api/admin/team/member/{team_name}"
    resp = session.post(url, params={"email": email}, timeout=session.request_timeout)
    if resp.status_code in (200, 201):
        print(f"[OK] Added to team {team_name}: {email}")
        return True
    elif resp.status_code == 409:
        print(f"[SKIP] Already in team (409): {email} -> {team_name} | detail={resp.text[:200]}")
        return True
    else:
        print(f"[ERR] Add member failed: {email} -> {team_name} | {resp.status_code} | {resp.text[:500]}")
        return False


def delete_team(session: requests.Session, base_url: str, team_name: str) -> bool:
    url = f"{base_url.rstrip('/')}/api/admin/team/delete/{team_name}"
    resp = session.delete(url, timeout=session.request_timeout)
    if resp.status_code in (200, 204):
        print(f"[OK] Deleted team: {team_name}")
        return True
    elif resp.status_code == 404:
        print(f"[SKIP] Team not found (404): {team_name}")
        return True
    else:
        print(f"[ERR] Delete team failed: {team_name} | {resp.status_code} | {resp.text[:500]}")
        return False


def delete_member_by_id(session: requests.Session, base_url: str, user_id: int, *, label: str = "") -> bool:
    url = f"{base_url.rstrip('/')}/api/admin/delete/member/{user_id}"
    resp = session.delete(url, timeout=session.request_timeout)
    target = label or str(user_id)
    if resp.status_code in (200, 204):
        print(f"[OK] Deleted user: {target}")
        return True
    elif resp.status_code == 404:
        print(f"[SKIP] User not found (404): {target}")
        return True
    else:
        print(f"[ERR] Delete user failed: {target} | {resp.status_code} | {resp.text[:500]}")
        return False


def build_user_lookup_by_email(session: requests.Session, base_url: str) -> Dict[str, Dict]:
    url = f"{base_url.rstrip('/')}/api/admin/member"
    resp = session.get(url, timeout=session.request_timeout)
    if resp.status_code != 200:
        print(f"[WARN] Failed to fetch member list: {resp.status_code} | {resp.text[:300]}")
        return {}

    payload = resp.json()
    if isinstance(payload, dict) and isinstance(payload.get("data"), list):
        records = payload["data"]
    elif isinstance(payload, list):
        records = payload
    else:
        print(f"[WARN] Unexpected member list payload: {payload}")
        return {}

    lookup: Dict[str, Dict] = {}
    for record in records:
        email = (record.get("email") or "").lower()
        if email:
            lookup[email] = record
    return lookup


def delete_members_from_csv(session: requests.Session, base_url: str, members: List[Member], sleep: float) -> None:
    lookup = build_user_lookup_by_email(session, base_url)
    if not lookup:
        print("[WARN] User lookup empty, skipping member deletions.")
        return

    for m in members:
        record = lookup.get((m.email or "").lower())
        if not record:
            print(f"[SKIP] User email not found: {m.email}")
            continue
        user_id = record.get("userId")
        if user_id is None:
            print(f"[SKIP] userId missing for email: {m.email} ({record})")
            continue
        delete_member_by_id(session, base_url, user_id, label=m.email)
        time.sleep(sleep)


def delete_teams(session: requests.Session, base_url: str, team_names: List[str], sleep: float) -> None:
    for team_name in team_names:
        delete_team(session, base_url, team_name)
        time.sleep(sleep)

# ===== Main =====
def main():
    ap = argparse.ArgumentParser(description="Bulk register members and create teams (email-localpart -> loginId, policy passwords).")
    ap.add_argument("--base-url", required=True)
    ap.add_argument("--token")
    ap.add_argument("--login-id")
    ap.add_argument("--password")
    ap.add_argument("--auth-endpoint", default="/api/users/sign-in")
    ap.add_argument("--xff", default=None)
    ap.add_argument("--cookie-fallback", action="store_true")
    ap.add_argument("--cookie-name", default="accessToken")

    ap.add_argument("--csv", required=True, help="CSV with columns: email,univ,role[,teamName][,name][,password]")
    ap.add_argument("--team-column", default="teamName")
    ap.add_argument("--team-prefix", default="team")
    ap.add_argument("--per-team", type=int, default=2)

    ap.add_argument("--random-password", default="policy",
                    choices=["none", "policy", "hex64", "hex32", "sha256", "base64"])
    ap.add_argument("--password-length", type=int, default=16)
    ap.add_argument("--export-passwords", default=None, help="Output CSV with teamName,name,loginId,email,password")

    ap.add_argument("--auth-header", default="Authorization")
    ap.add_argument("--auth-scheme", default="Bearer")
    ap.add_argument("--login-case", choices=["lower", "original"], default="lower")
    ap.add_argument("--sleep", type=float, default=0.2)

    ap.add_argument("--cleanup-members", action="store_true",
                    help="Delete members listed in CSV after processing.")
    ap.add_argument("--cleanup-teams", action="store_true",
                    help="Delete teams derived from CSV after processing.")
    ap.add_argument("--cleanup-only", action="store_true",
                    help="Skip registration/team creation and only run cleanup (defaults to both members and teams).")

    args = ap.parse_args()

    session = make_session()
    session.headers.update({"Accept": "application/json", "Content-Type": "application/json"})
    if args.xff:
        session.headers.update({"X-Forwarded-For": args.xff})

    # token
    if args.token:
        token = args.token
    else:
        if not (args.login_id and args.password):
            print("[FATAL] Provide either --token OR --login-id/--password")
            sys.exit(2)
        token = _signin(session, args.base_url, args.login_id, args.password, xff=args.xff, timeout=10)

    if args.auth_scheme:
        session.headers.update({args.auth_header: f"{args.auth_scheme} {token}"})
    else:
        session.headers.update({args.auth_header: token})

    if args.cookie_fallback:
        host = urlparse(args.base_url).hostname or "localhost"
        session.cookies.set(args.cookie_name, token, domain=host, path="/")

    # load CSV
    members, has_team_col, has_name_col = load_members_from_csv(args.csv, team_col=args.team_column)

    # derive loginId from email local-part (sanitized)
    used = set()
    for m in members:
        m.loginId = derive_login_from_email(m.email, used, lowercase=(args.login_case == "lower"))

    cleanup_members = args.cleanup_members
    cleanup_teams = args.cleanup_teams
    if args.cleanup_only and not (cleanup_members or cleanup_teams):
        cleanup_members = True
        cleanup_teams = True

    # passwords
    if not args.cleanup_only and args.random_password != "none":
        for m in members:
            if args.random_password == "policy":
                m.password = generate_policy_password(args.password_length)
            else:
                m.password = gen_password(args.random_password)
            if not validate_password_policy(m.password):
                m.password = generate_policy_password(args.password_length)

    teams: Dict[str, List[Member]] = {}
    groups: List[List[Member]] = []
    team_names: List[str] = []
    if has_team_col:
        for m in members:
            if not m.teamName:
                print(f"[FATAL] teamName missing for member {m.email}")
                sys.exit(3)
            teams.setdefault(m.teamName, []).append(m)
        team_names = list(teams.keys())
    else:
        groups = [members[i:i + args.per_team] for i in range(0, len(members), args.per_team)]
        team_names = [f"{args.team_prefix}-{idx:02d}" for idx in range(1, len(groups) + 1)]

    if not args.cleanup_only:
        # register
        print("=== Registering members ===")
        for m in members:
            register_member(session, args.base_url, m)
            time.sleep(args.sleep)

        # teams
        if has_team_col:
            print(f"=== Creating {len(teams)} teams from CSV ===")
            for team_name, group in teams.items():
                if not create_team(session, args.base_url, team_name):
                    print(f"[WARN] Skipping additions for team: {team_name}")
                    continue
                for m in group:
                    add_member_to_team(session, args.base_url, team_name, m.email)
                    time.sleep(args.sleep)
        else:
            print(f"=== Auto-creating teams of {args.per_team} with prefix '{args.team_prefix}' ===")
            for idx, group in enumerate(groups, start=1):
                team_name = f"{args.team_prefix}-{idx:02d}"
                if not create_team(session, args.base_url, team_name):
                    print(f"[WARN] Skipping additions for team: {team_name}")
                    continue
                for m in group:
                    add_member_to_team(session, args.base_url, team_name, m.email)
                    time.sleep(args.sleep)

    if cleanup_members:
        print("=== Deleting members listed in CSV ===")
        delete_members_from_csv(session, args.base_url, members, args.sleep)

    if cleanup_teams:
        print(f"=== Deleting {len(team_names)} team(s) ===")
        delete_teams(session, args.base_url, team_names, args.sleep)

    # export
    if args.export_passwords and not args.cleanup_only:
        import os
        os.makedirs(os.path.dirname(args.export_passwords), exist_ok=True)
        with open(args.export_passwords, "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(["teamName", "name", "loginId", "email", "password"])
            for m in members:
                name = (m.name or m.loginId or (m.email.split('@')[0] if m.email else ""))
                w.writerow([m.teamName or "", name, m.loginId, m.email, m.password])
        print(f"[OK] Exported: {args.export_passwords}")

    print("=== Done ===")

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n[ABORTED]")
