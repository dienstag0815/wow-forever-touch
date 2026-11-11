#!/usr/bin/env python3
"""Tester codes for the update source of WoW Forever (create, check, test vectors).

A tester code carries everything the app needs for "Ich bin Tester": server kind, repository address, access token and the
pre-release switch, in ONE string that can be pasted into Settings -> Updates -> Update source.

    Format:  WFT1-<payload><check>
    payload  base64url (no padding) of  obfuscate(compact JSON {"k": kind, "u": repo address, "t": token, "p": 0|1})
    check    4 characters (base64url alphabet) = the low 24 bits of CRC-32 over "WFT1-" + payload; catches typing errors
    obfuscate  XOR with a keystream  SHA-256(KEY || counter as 4 bytes big endian)  (KEY below, the same constant sits in the app)

Be honest about what this is: the key is public (it is in this file and in the public app), so the obfuscation only keeps the
address and token from being readable at a glance. It is NOT encryption. The protection comes from the token itself: give every
tester their own token with read-only access to the releases, and revoke it on the server when the test ends.

The token is never printed except inside the code itself (check shows only that a token is set and how long it is).

    tester_code.py make  --kind gitea|github --url https://host/owner/repo (--token TOKEN | --token-file FILE | --token-stdin) [--prerelease]
    tester_code.py check CODE | --code-file FILE | -          (prints kind, address, pre-release switch, token set yes/no)
    tester_code.py vectors                                    (test vectors for the Kotlin round trip, fake data, to stdout)
"""
import argparse
import base64
import hashlib
import json
import re
import sys
import zlib

PREFIX = "WFT1-"
KEY = b"wow-forever/tester-code/v1"
KINDS = ("gitea", "github")
MAX_CODE_LENGTH = 4000
_SEGMENT = re.compile(r"^[A-Za-z0-9._-]+$")
_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"


class CodeError(ValueError):
    pass


def _keystream(length):
    out = bytearray()
    counter = 0
    while len(out) < length:
        out += hashlib.sha256(KEY + counter.to_bytes(4, "big")).digest()
        counter += 1
    return bytes(out[:length])


def _xor(data):
    return bytes(a ^ b for a, b in zip(data, _keystream(len(data))))


def _check(text):
    crc = zlib.crc32(text.encode("ascii")) & 0xFFFFFF
    return "".join(_ALPHABET[(crc >> shift) & 0x3F] for shift in (18, 12, 6, 0))


def check_repo_url(url):
    """The same rules as the app: https://host[:port]/owner/repo (also with .git or a trailing slash), nothing else."""
    m = re.match(r"^https://([A-Za-z0-9.-]+(?::\d{1,5})?)/([^/?#@\s]+)/([^/?#@\s]+?)(?:\.git)?/?$", url.strip(), re.IGNORECASE)
    if not m or not _SEGMENT.match(m.group(2)) or not _SEGMENT.match(m.group(3)) or ".." in (m.group(2), m.group(3)):
        raise CodeError("repository address must look like https://host/owner/repo")
    return "https://%s/%s/%s" % (m.group(1).lower(), m.group(2), m.group(3))


def make_code(kind, url, token="", prerelease=False):
    if kind not in KINDS:
        raise CodeError("kind must be one of: " + ", ".join(KINDS))
    token = (token or "").strip()
    if any(c.isspace() for c in token):
        raise CodeError("token must not contain spaces or line breaks")
    data = {"k": kind, "u": check_repo_url(url), "p": 1 if prerelease else 0}
    if token:
        data["t"] = token
    compact = json.dumps(data, separators=(",", ":"), sort_keys=True, ensure_ascii=True).encode("ascii")
    payload = base64.urlsafe_b64encode(_xor(compact)).decode("ascii").rstrip("=")
    body = PREFIX + payload
    return body + _check(body)


def read_code(code):
    """Returns dict(kind, url, token, prerelease). Raises CodeError with a reason (never with the token)."""
    text = "".join(code.split())
    if not text.startswith(PREFIX):
        raise CodeError("not a tester code (must start with %s)" % PREFIX)
    if len(text) > MAX_CODE_LENGTH or len(text) < len(PREFIX) + 8:
        raise CodeError("code has the wrong length")
    body, check = text[:-4], text[-4:]
    if _check(body) != check:
        raise CodeError("checksum does not match (typing error or incomplete code)")
    payload = body[len(PREFIX):]
    try:
        raw = base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4))
        data = json.loads(_xor(raw).decode("utf-8"))
    except Exception:
        raise CodeError("code content is unusable")
    if not isinstance(data, dict) or data.get("k") not in KINDS or not isinstance(data.get("u"), str):
        raise CodeError("code content is unusable")
    token = data.get("t", "")
    if not isinstance(token, str):
        raise CodeError("code content is unusable")
    return {"kind": data["k"], "url": check_repo_url(data["u"]), "token": token, "prerelease": data.get("p") in (1, True)}


# Fake data only (example host, made-up tokens); the Kotlin test reads the generated file and must decode and re-encode every line.
VECTOR_CASES = [
    ("gitea", "https://git.example.org/owner/repo", "tok-0123456789abcdef", False),
    ("gitea", "https://git.example.org:8443/team/app.releases", "", True),
    ("github", "https://github.com/owner/repo", "fake_token_A1b2C3d4E5f6", True),
    ("github", "https://ghe.example.org/owner/repo", "x", False),
    ("gitea", "https://git.example.org/owner/repo", "Sym_bols-~+/=.:", False),
]


def vectors():
    lines = [
        "# Generated by tools/tester_code.py vectors (do not edit by hand). Fake data only.",
        "# code | kind | address | token | prerelease (0/1)",
    ]
    for kind, url, token, pre in VECTOR_CASES:
        lines.append("|".join([make_code(kind, url, token, pre), kind, url, token, "1" if pre else "0"]))
    return "\n".join(lines) + "\n"


def _read_token(args):
    if args.token is not None:
        return args.token
    if args.token_file:
        with open(args.token_file, encoding="utf-8") as f:
            return f.read().strip()
    if args.token_stdin:
        return sys.stdin.read().strip()
    return ""


def main(argv=None):
    ap = argparse.ArgumentParser(description="Create and check tester codes for the update source.")
    sub = ap.add_subparsers(dest="cmd", required=True)
    mk = sub.add_parser("make", help="create a code")
    mk.add_argument("--kind", required=True, choices=KINDS)
    mk.add_argument("--url", required=True, help="https://host/owner/repo")
    mk.add_argument("--token", help="access token (visible in the shell history; prefer --token-file or --token-stdin)")
    mk.add_argument("--token-file", help="file with the token (first line / whole file, whitespace trimmed)")
    mk.add_argument("--token-stdin", action="store_true", help="read the token from standard input")
    mk.add_argument("--prerelease", action="store_true", help="the tester also gets pre-releases")
    ck = sub.add_parser("check", help="check a code and show what is in it (the token is never shown)")
    ck.add_argument("code", nargs="?", help="the code, or - for standard input")
    ck.add_argument("--code-file", help="file with the code")
    sub.add_parser("vectors", help="write the test vector file to stdout")
    args = ap.parse_args(argv)
    try:
        if args.cmd == "make":
            print(make_code(args.kind, args.url, _read_token(args), args.prerelease))
        elif args.cmd == "check":
            if args.code_file:
                with open(args.code_file, encoding="utf-8") as f:
                    text = f.read()
            elif args.code == "-" or args.code is None:
                text = sys.stdin.read()
            else:
                text = args.code
            info = read_code(text)
            print("OK")
            print("kind:       " + info["kind"])
            print("address:    " + info["url"])
            print("prerelease: " + ("yes" if info["prerelease"] else "no"))
            print("token:      " + ("set (%d characters)" % len(info["token"]) if info["token"] else "none"))
        else:
            sys.stdout.write(vectors())
    except (CodeError, OSError) as e:
        print("ERROR: %s" % e, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
