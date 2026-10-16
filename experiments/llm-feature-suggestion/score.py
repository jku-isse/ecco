"""Summarizes replay.py results: per commit whose feature was seen before, whether the suggestion
named exactly it; per commit starting a new feature, whether it got a new name. Usage:
    python3 score.py results/a.jsonl results/b.jsonl ...
"""
import json
import statistics
import sys

GENERIC = {'misc', 'documentation'}


def score(path):
    records = [json.loads(line) for line in open(path)]
    seen = dict(n=0, exact=0, extra=0, other_known=0, invented=0, generic=0)
    new = dict(n=0, new_name=0, forced=0, generic=0)
    known = []
    for r in records:
        predicted = {p.strip() for p in r['prediction'].split(',') if p.strip() and not p.strip().startswith('-')}
        truth = set(r['truth'])
        if truth <= set(known):
            seen['n'] += 1
            if predicted == truth:
                seen['exact'] += 1
            elif truth <= predicted:
                seen['extra'] += 1
            elif predicted & set(known):
                seen['other_known'] += 1
            elif predicted <= GENERIC:
                seen['generic'] += 1
            else:
                seen['invented'] += 1
        else:
            new['n'] += 1
            if predicted <= GENERIC:
                new['generic'] += 1
            elif predicted - set(known):
                new['new_name'] += 1
            else:
                new['forced'] += 1
        known += [t for t in r['truth'] if t not in known]
    seconds = [r['seconds'] for r in records if r['seconds']]
    names = {p.strip() for r in records for p in r['prediction'].split(',') if p.strip()}
    print(f"== {path}: {len(records)} commits, {len(known)} true features, {len(names)} distinct predicted names")
    print(f"  seen feature ({seen['n']}): exact {seen['exact']}, right plus extra {seen['extra']}, other known {seen['other_known']}, "
          f"made-up name {seen['invented']}, misc/documentation {seen['generic']}")
    print(f"  new feature  ({new['n']}): new name {new['new_name']}, forced into a known one {new['forced']}, misc/documentation {new['generic']}")
    if seconds:
        print(f"  seconds: median {statistics.median(seconds):.1f}, max {max(seconds):.1f}, total {sum(seconds) / 60:.1f} min")


if __name__ == '__main__':
    for path in sys.argv[1:]:
        score(path)
