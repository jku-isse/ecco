"""How far a suggestion can be trusted, from whether it agrees with the evidence - and whether the
least trusted suggestions are the wrong ones: reviewing those instead of every Nth commit.

For each replayed commit: the overlap of its changed places with the known features' earlier ones
(the three levels of ImportedFeatureTraces), the suggestion, and a confidence from how they agree.
Then: reviewing the k% least confident commits, how many of the wrong suggestions - and of the new
features a suggestion missed, the costly mistake - would a reviewer see?

Usage:
    python3 confidence.py results/vite120-qwen2.5-coder-32b-evidence.jsonl positions.json
positions.json: [{"commit", "labels", "files": {path: {"positions": [...]}}}] (see nesting.py).
"""
import json
import sys

import evidence as ev

GENERIC = {'misc', 'documentation'}


def tokens(text):
    return [t.strip() for t in (text or '').split(',') if t.strip() and not t.strip().startswith('-')]


def confidence(predicted, known, overlap):
    """0..1: how far the suggestion agrees with where the commit changed things.

    overlap: [(feature, same positions, same enclosing, same files)], strongest first."""
    if not known:
        return 0.5  # nothing to compare with
    strength = {f: (3 if same else 2 if enclosing else 1) for f, same, enclosing, files in overlap}
    top = max(strength.values(), default=0)
    parts = []
    for p in predicted or ['']:
        if p in GENERIC or p == '':
            parts.append(0.3 if top < 3 else 0.15)
        elif p in known:
            s = strength.get(p, 0)
            parts.append({3: 0.9, 2: 0.75, 1: 0.6, 0: 0.3}[s] - (0.15 if s < top else 0))
        else:  # a new name
            parts.append({0: 0.8, 1: 0.6, 2: 0.4, 3: 0.2}[top])
    return min(parts) - (0.05 if len(predicted) > 1 else 0)


def main():
    records = [json.loads(l) for l in open(sys.argv[1])]
    history = {c['commit'][:7]: c for c in json.load(open(sys.argv[2]))}
    traces = ev.Traces()
    known = []
    rows = []
    for r in records:
        commit = history[r['commit']]
        changed = {p: {'positions': (f['positions'] if isinstance(f, dict) else f)} for p, f in commit['files'].items()}
        overlap = [o for o in traces.overlap(changed) if o[0] in known]
        predicted = tokens(r['prediction'])
        truth = set(r['truth'])
        new = not truth <= set(known)
        right = (bool(set(predicted) - set(known) - GENERIC) if new else set(predicted) == truth)
        rows.append(dict(commit=r['commit'], new=new, right=right, confidence=confidence(predicted, known, overlap)))
        traces.add(r['truth'], changed)
        known += [t for t in r['truth'] if t not in known]

    wrong = [x for x in rows if not x['right']]
    missed = [x for x in rows if x['new'] and not x['right']]
    ranked = sorted(rows, key=lambda x: x['confidence'])
    print(f'{len(rows)} commits: {len(wrong)} wrong, of them {len(missed)} new features missed')
    print(f'{"reviewed":>9} {"wrong seen":>11} {"missed new seen":>16}   (every Nth commit would see about the share reviewed)')
    for share in (0.1, 0.2, 0.3, 0.5):
        k = round(len(rows) * share)
        seen = ranked[:k]
        w = sum(1 for x in seen if not x['right'])
        m = sum(1 for x in seen if x['new'] and not x['right'])
        print(f'{share:>8.0%} {w:>5}/{len(wrong)} ({w / len(wrong):.0%}) {m:>8}/{len(missed)} ({m / max(1, len(missed)):.0%})')
    by = {}
    for x in rows:
        by.setdefault(round(x['confidence'], 2), []).append(x['right'])
    print('confidence: right/all  ' + '  '.join(f'{c}: {sum(v)}/{len(v)}' for c, v in sorted(by.items())))


if __name__ == '__main__':
    main()
