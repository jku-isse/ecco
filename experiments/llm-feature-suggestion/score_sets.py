"""Precision and recall of a replay against the ground truth of spl_history.py (replay.py --truth).

A commit's truth is the features it adds and removes ("-name"). A suggested name counts for the
true feature it stands for:
- a known name for itself - the true feature's name, or with --online the model's own name the
  feature got when it first came;
- a new name for one of the true features that are new in the commit (not known yet), paired by
  how alike the names are. A new name left over - for a feature known already, or for none the
  commit adds - is "made up": an import would get a bogus feature for it, so it counts as wrong.

Precision and recall are reported apart, as in Nguyen et al. (2026): one rises at the cost of the
other. "misc" and "documentation" are wrong here - every change of these histories is a feature's.

    python3 score_sets.py results/spl-elevator-gemma4-31b.jsonl [more.jsonl ...]
"""
import difflib
import json
import re
import sys

BASE = 'BASE'
GENERIC = {'misc', 'documentation'}  # LlmFeatureSuggestionClient.GENERIC


def tokens(prediction):
    """(added, removed) names of a suggestion; ([], []) for a failed one."""
    if not isinstance(prediction, str) or prediction.startswith('<'):
        return [], []
    added, removed = [], []
    for t in prediction.split(','):
        t = t.strip().rstrip("'").strip()
        if not t or t == BASE:
            continue
        (removed if t.startswith('-') else added).append(t.lstrip('-').strip())
    return added, removed


def alike(a, b):
    norm = lambda s: re.sub(r'[^a-z0-9]', '', s.lower())
    return difflib.SequenceMatcher(None, norm(a), norm(b)).ratio()


def pair(new_names, new_features):
    """New names to the new true features they stand for, most alike first; the rest to None."""
    pairs, names, features = {}, list(new_names), list(new_features)
    for score, name, feature in sorted(((alike(n, f), n, f) for n in names for f in features), reverse=True):
        if name in names and feature in features:
            pairs[name] = feature
            names.remove(name)
            features.remove(feature)
    return pairs | {n: None for n in names}


def score(path):
    records = [json.loads(l) for l in open(path)]
    base = next((r.get('base') for r in records if 'base' in r), None)
    online = any(r.get('mode') == 'online' for r in records)
    owner = {BASE: base}  # a name the model uses -> the true feature it stands for
    introduced = {base}   # true features some name stands for already
    c = dict(commits=0, tp=0, fp=0, fn=0, rtp=0, rfp=0, rfn=0, seen=0, seenHit=0, new=0, newHit=0, madeUp=0, generic=0, errors=0)
    for r in records:
        if 'prediction' not in r:
            continue
        c['commits'] += 1
        known = set(r.get('knownNames', []))
        truth_added = {t for t in r['truth'] if not t.startswith('-')}
        truth_removed = {t[1:] for t in r['truth'] if t.startswith('-')}
        if not online:  # the known names are the true ones
            owner.update({k: k for k in known if k != BASE})
            introduced |= {k for k in known if k != BASE}
        if r['prediction'].startswith('<'):
            c['errors'] += 1
        added, removed = tokens(r['prediction'])
        new_names = [n for n in added if n not in owner and n not in GENERIC]
        meant = pair(new_names, truth_added - introduced)
        c['madeUp'] += sum(1 for n in new_names if meant[n] is None)
        stands = {owner.get(n, meant.get(n)) for n in added}
        c['generic'] += sum(1 for n in added if n in GENERIC)
        hits = stands & truth_added
        c['tp'] += len(hits)
        c['fp'] += sum(1 for n in added if owner.get(n, meant.get(n)) not in truth_added)
        c['fn'] += len(truth_added - hits)
        for t in truth_added:
            if t in introduced:
                c['seen'] += 1
                c['seenHit'] += t in hits
            else:
                c['new'] += 1
                c['newHit'] += t in hits
        removed_stands = {owner.get(n) for n in removed}
        c['rtp'] += len(removed_stands & truth_removed)
        c['rfp'] += sum(1 for n in removed if owner.get(n) not in truth_removed)
        c['rfn'] += len(truth_removed - removed_stands)
        if online:  # what the import keeps: the new names, standing for what they were paired with
            for n, f in meant.items():
                owner[n] = f
        introduced |= {f for f in meant.values() if f}
    return c, online


def ratio(a, b):
    return f'{a / b:.2f}' if b else '-'


def main():
    print('| Run | Mode | Commits | Precision | Recall | Recall seen / new | Made up | misc/doc | Removals P / R | Errors |')
    print('| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |')
    for path in sys.argv[1:]:
        c, online = score(path)
        print(f"| {path.rsplit('/', 1)[-1].removesuffix('.jsonl')} | {'online' if online else 'oracle'} | {c['commits']} "
              f"| {ratio(c['tp'], c['tp'] + c['fp'])} | {ratio(c['tp'], c['tp'] + c['fn'])} "
              f"| {ratio(c['seenHit'], c['seen'])} / {ratio(c['newHit'], c['new'])} | {c['madeUp']} | {c['generic']} "
              f"| {ratio(c['rtp'], c['rtp'] + c['rfp'])} / {ratio(c['rtp'], c['rtp'] + c['rfn'])} | {c['errors']} |")


if __name__ == '__main__':
    main()
