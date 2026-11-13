#!/usr/bin/env python3
"""The design standard's mechanical checks, run by CI on every push.

Usage: design_check.py [--update]

Counts, per file, the patterns the standard ("Desktop Kit Tasarım Standardı") forbids or wants
kept down, and compares them with tools/design_baseline.json:

- a count above the baseline fails the build and the new lines are printed (as GitHub annotations);
- a count below it passes, and says the baseline can come down (run with --update and commit it).

The baseline holds what was there when the check came in (2.6.0), so it does not ask for a
rewrite: it keeps new code from adding more. What is checked, and why:

- raw-color      Color(0x…) outside the theme: colours come from DeskTheme.colors (SLP-1, GÖR-5)
- platform-toast Toast.makeText: the kit's ToastState is accessible and in the window (SLP-18)
- dead-handler   onClick = {} and ", {})": a control that does nothing (SLP-17)
- infinite       infiniteRepeatable outside the skeleton's pulse: motion that never ends (HAR, SLP-20)
- off-scale-dp   N.dp literals off the 4 dp grid (hairlines of 1 and 2 dp aside), outside the theme (GÖR-9)
- shouting       a string resource ending in "!" (SLP-32)
- three-dots     "..." in a string resource instead of "…" (SLP-32)
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASELINE = os.path.join(ROOT, 'tools', 'design_baseline.json')
SOURCES = ['kit/src/main', 'sample/src/main']

# the 4 dp grid, and hairlines
SCALE = {0, 1, 2} | set(range(4, 1001, 4))

RULES = {
    'raw-color': (r'Color\(0x[0-9A-Fa-f]{6,8}\)', '.kt', lambda path: '/theme/' not in path),
    'platform-toast': (r'Toast\.makeText', '.kt', lambda path: True),
    'dead-handler': (r'onClick\s*=\s*\{\s*\}|,\s*\{\s*\}\s*\)', '.kt', lambda path: True),
    'infinite': (r'infiniteRepeatable', '.kt', lambda path: not path.endswith('Loading.kt')),
    'off-scale-dp': (r'(?<![\w.])(\d+(?:\.\d+)?)\.dp\b', '.kt', lambda path: '/theme/' not in path),
    'shouting': (r'<string name="[^"]+">[^<]*!</string>', '.xml', lambda path: '/res/values' in path),
    'three-dots': (r'<string name="[^"]+">[^<]*\.\.\.[^<]*</string>', '.xml', lambda path: '/res/values' in path),
}


def hits(rule, path, text):
    pattern, _, _ = RULES[rule]
    found = []
    for number, line in enumerate(text.splitlines(), 1):
        stripped = line.strip()
        # comments describe, they do not draw
        if stripped.startswith('//') or stripped.startswith('*') or stripped.startswith('/*'):
            continue
        for match in re.finditer(pattern, line):
            if rule == 'off-scale-dp' and float(match.group(1)) in SCALE:
                continue
            found.append((number, line.strip()))
    return found


def scan():
    counts, lines = {}, {}
    for source in SOURCES:
        for directory, _, files in os.walk(os.path.join(ROOT, source)):
            for name in sorted(files):
                path = os.path.join(directory, name)
                relative = os.path.relpath(path, ROOT)
                for rule, (_, extension, applies) in RULES.items():
                    if not name.endswith(extension) or not applies(relative):
                        continue
                    with open(path, encoding='utf-8') as handle:
                        found = hits(rule, relative, handle.read())
                    if found:
                        counts.setdefault(rule, {})[relative] = len(found)
                        lines[(rule, relative)] = found
    return counts, lines


def main(update):
    counts, lines = scan()
    if update:
        with open(BASELINE, 'w', encoding='utf-8') as handle:
            json.dump(counts, handle, indent=2, sort_keys=True, ensure_ascii=False)
            handle.write('\n')
        print('baseline written: %s' % os.path.relpath(BASELINE, ROOT))
        return 0
    with open(BASELINE, encoding='utf-8') as handle:
        baseline = json.load(handle)
    failed, lower = False, []
    for rule in RULES:
        for path in sorted(set(counts.get(rule, {})) | set(baseline.get(rule, {}))):
            now = counts.get(rule, {}).get(path, 0)
            before = baseline.get(rule, {}).get(path, 0)
            if now > before:
                failed = True
                for number, line in lines.get((rule, path), []):
                    print('::error file=%s,line=%d,title=Design standard (%s)::%s' % (path, number, rule, line))
                print('%s: %s has %d, the baseline %d' % (rule, path, now, before))
            elif now < before:
                lower.append('%s: %s %d -> %d' % (rule, path, before, now))
    if lower:
        print('Fewer than the baseline (run tools/design_check.py --update and commit it):')
        print('\n'.join('  ' + entry for entry in lower))
    total = sum(sum(per.values()) for per in counts.values())
    print('::notice title=Design standard::%d known findings, %s' % (total, 'new ones above' if failed else 'none new'))
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main('--update' in sys.argv[1:]))
