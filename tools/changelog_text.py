"""Gibt Abschnitte aus CHANGELOG.md als eine ASCII-Zeile fuer die Option `--text` des Upload-Skripts der Download-Seite aus.

Der Launcher liest das Feld `text` von latest.json als Aenderungsliste, wenn es `## <Version>`-Ueberschriften enthaelt
(Changelog.kt: `\\n` = Zeilenumbruch, `\\uXXXX` = Sonderzeichen). Die Zeile enthaelt deshalb nur ASCII: Umlaute, Anfuehrungszeichen
und Zeilenumbrueche der Konsole/Befehlszeile koennen nichts mehr zerstoeren.

Aufruf (PowerShell, im Repo-Ordner, CHANGELOG.md vorher pflegen):
  $t = python -I tools\\changelog_text.py [--anzahl 6] [--seit 2.3.3-fold8.9] [--datei CHANGELOG.md]
  <Upload-Skript der Download-Seite> --datei ... --version ... --sha256 ... --text $t
Ohne --seit kommen die neuesten --anzahl Versionen (Standard 6); mit --seit nur die Versionen nach dieser (hoechstens --anzahl).
Nur Lesen, aendert nichts. Exit 1, wenn nichts ausgewaehlt wurde oder die Zeile zu lang fuer eine Befehlszeile ist (> 24000 Zeichen).
"""
import argparse
import re
import sys

HEADING = re.compile(r'^##\s+(.+?)\s*$')


def numbers(text):
    return [int(x) for x in re.findall(r'[0-9]+', text)]


def split_name(name):
    t = name.strip().lstrip('vV')
    base, _, rest = t.partition('-')
    return numbers(base), numbers(rest)


def compare(a, b):
    """Wie UpdateLogic.compareVersionNames: erst Zahlen vor dem ersten '-', dann die danach; fehlende Teile = 0."""
    for x, y in zip(split_name(a), split_name(b)):
        for i in range(max(len(x), len(y))):
            d = (x[i] if i < len(x) else 0) - (y[i] if i < len(y) else 0)
            if d:
                return -1 if d < 0 else 1
    return 0


def version_of(heading):
    m = re.search(r'\s+[\u2013\u2014-]\s+', heading)
    return (heading[:m.start()] if m else heading).strip()


def blocks(text):
    """[(version, [zeilen])] in Dateireihenfolge; Text vor der ersten Versionsueberschrift entfaellt."""
    result = []
    for line in text.replace('\r\n', '\n').split('\n'):
        m = HEADING.match(line) if not line.startswith('###') else None
        if m:
            result.append((version_of(m.group(1)), [line.rstrip()]))
        elif result:
            result[-1][1].append(line.rstrip())
    return result


def ascii_form(text):
    out = []
    for ch in text:
        if ch == '\n':
            out.append('\\n')
        elif ch == '"' or ch == "'" or ch == '\\' or ord(ch) > 126 or ord(ch) < 32:
            out.append('\\u%04x' % ord(ch))
        else:
            out.append(ch)
    return ''.join(out)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--datei', default=None, help='Standard: CHANGELOG.de.md bzw. CHANGELOG.md je nach --sprache')
    ap.add_argument('--sprache', choices=['de', 'en'], default='de', help='Standard de (Veroeffentlichungsweg unveraendert)')
    ap.add_argument('--anzahl', type=int, default=6)
    ap.add_argument('--seit', default=None, help='nur Versionen nach dieser (z. B. 2.3.3-fold8.13)')
    a = ap.parse_args()
    if a.datei is None:
        a.datei = 'CHANGELOG.de.md' if a.sprache == 'de' else 'CHANGELOG.md'
    with open(a.datei, encoding='utf-8') as f:
        bl = blocks(f.read())
    if a.seit:
        bl = [b for b in bl if compare(b[0], a.seit) > 0]
    bl.sort(key=lambda b: split_name(b[0]), reverse=True)
    bl = bl[:max(a.anzahl, 1)]
    if not bl:
        sys.exit('keine Version ausgewaehlt')
    text = '\n\n'.join('\n'.join(z).strip('\n') for _, z in bl)
    line = ascii_form(text)
    if len(line) > 24000:
        sys.exit('Zeile zu lang (%d Zeichen): --anzahl verkleinern' % len(line))
    sys.stdout.write(line + '\n')
    print('Versionen: ' + ', '.join(v for v, _ in bl), file=sys.stderr)


if __name__ == '__main__':
    main()
