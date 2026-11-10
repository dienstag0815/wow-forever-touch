"""Compares the assets inside a built APK with the asset sources of a git revision.

Why: on 08.11.2026 Gradle's incremental asset merge on the build machine kept stale copies of the TouchForever addon; fold8.2 to fold8.6
shipped the addon of 08.11. 13:58 although the sources had changed. Run this after every release build, before publishing.

Usage: python -I tools/apk_assets_check.py <apk> [<repo> [<rev>]]   (repo default ".", rev default HEAD)
Exit 0 = every source asset is in the APK with the same content; 1 = a difference (listed). Files under wow/ must match byte
for byte; other text files are compared with CRLF folded to LF (line endings of a Windows checkout are harmless there).
Extra files in the APK (generated ones such as dexopt/, bundled_components/) are only counted.
"""
import hashlib
import subprocess
import sys
import zipfile

SOURCE_DIRS = ["app/src/modern/assets", "app/src/main/assets"]  # same order as srcDirs in app/build.gradle.kts
STRICT_PREFIX = "wow/"


def git(repo, *args, binary=False):
    out = subprocess.run(["git", "-C", repo, *args], capture_output=True, check=True)
    return out.stdout if binary else out.stdout.decode("utf-8", "replace")


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    apk = sys.argv[1]
    repo = sys.argv[2] if len(sys.argv) > 2 else "."
    rev = sys.argv[3] if len(sys.argv) > 3 else "HEAD"

    with zipfile.ZipFile(apk) as z:
        packed = {n[len("assets/"):]: z.read(n) for n in z.namelist() if n.startswith("assets/") and not n.endswith("/")}

    sources = {}
    for path in git(repo, "ls-tree", "-r", "--name-only", rev, "--", *SOURCE_DIRS).splitlines():
        for d in SOURCE_DIRS:
            if path.startswith(d + "/"):
                sources.setdefault(path[len(d) + 1:], path)

    bad = []
    for rel, path in sorted(sources.items()):
        if rel not in packed:
            bad.append(f"FEHLT   {rel}")
            continue
        src = git(repo, "show", f"{rev}:{path}", binary=True)
        got = packed[rel]
        if not rel.startswith(STRICT_PREFIX):
            src, got = src.replace(b"\r\n", b"\n"), got.replace(b"\r\n", b"\n")
        if hashlib.sha256(src).digest() != hashlib.sha256(got).digest():
            bad.append(f"ANDERS  {rel}  (Quelle {len(src)} B, APK {len(got)} B)")

    extra = len([r for r in packed if r not in sources])
    print(f"Assets: Quelle {len(sources)}, APK {len(packed)} (davon {extra} nur in der APK), Abweichungen {len(bad)}")
    for line in bad:
        print(line)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
