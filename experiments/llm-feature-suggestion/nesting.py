"""Feature levels from change positions: a feature whose changes lie inside places another feature
created likely refines it - a parent candidate for the feature model.

A place (a file, or a class/method/section in it) is owned by the features of the commit that
created the file - inside a new file every position is first changed by its creator - or, after
that, first changed it. For each change of feature B, the nearest enclosing place of the changed
position votes for its owner; B's parent candidate is the owner with most votes, if it is not B.
Only files created within the history count: in a history that does not start at the beginning, a
file's first change in it is no creation; and the commit a shallow clone was cut at creates
nothing - it only looks as if it added every file. A renamed file keeps its owners (git's rename
detection); without it a restructuring commit took over every file it moved. The features of the
first commit that creates files are the base: everything later nests in them, so each feature's
best parent besides the base is reported too.

Input: a JSON list of commits, oldest first: {"commit", "labels": [...], "files": {path: {"positions": [...]}}},
and the git clone (for which files a commit added). Usage:
    python3 nesting.py history.json /path/to/clone
"""
import json
import subprocess
import sys
from collections import Counter, defaultdict

SEPARATORS = (' > ', '::', '.')


def ancestors(name):
    """'Zip.Entry.read' -> ['Zip.Entry', 'Zip'], nearest first."""
    result = []
    while True:
        cut = max(name.rfind(s) for s in SEPARATORS)
        if cut <= 0:
            return result
        name = name[:cut]
        result.append(name)


def shallow_boundary(repo):
    """The commits a shallow clone was cut at: they look as if they added every file."""
    path = subprocess.run(['git', '-C', repo, 'rev-parse', '--git-path', 'shallow'], capture_output=True, text=True).stdout.strip()
    try:
        return set(open(path if path.startswith('/') else f'{repo}/{path}').read().split())
    except OSError:
        return set()


def added_and_renamed(repo, sha, boundary=frozenset()):
    """The files the commit added, and {new path: old path} of those it renamed (a rename is no creation)."""
    if sha in boundary:
        return set(), {}
    out = subprocess.run(['git', '-C', repo, 'show', '--format=', '--name-status', '-M', sha],
                         capture_output=True, text=True, errors='replace').stdout
    added, renamed = set(), {}
    for line in out.splitlines():
        parts = line.split('\t')
        if parts[0] == 'A':
            added.add(parts[1])
        elif parts[0].startswith('R') and len(parts) == 3:
            renamed[parts[2]] = parts[1]
    return added, renamed


def nesting(history, repo):
    owner = {}  # (path, area) -> labels of the commit that first touched it; area '' is the file
    created = set()
    votes = defaultdict(Counter)  # feature -> owner feature -> votes
    base = None
    boundary = shallow_boundary(repo)
    for commit in history:
        labels = commit['labels']
        if not labels:
            continue
        new, renamed = added_and_renamed(repo, commit['commit'], boundary)
        if new and base is None:
            base = set(labels)
        created |= new
        # a renamed file keeps its owners
        for to, frm in renamed.items():
            if frm in created:
                created.add(to)
                for (path, area), o in list(owner.items()):
                    if path == frm:
                        owner.setdefault((to, area), o)
        for path, f in commit['files'].items():
            positions = f['positions'] if isinstance(f, dict) else f
            if path not in created:
                continue
            # this commit's changes vote for the owners of their nearest enclosing places, as they were before it
            for position in positions:
                nearest = next(((path, a) for a in ancestors(position) if (path, a) in owner), (path, ''))
                if nearest in owner and path not in new:
                    for label in labels:
                        for o in owner[nearest]:
                            votes[label][o] += 1
            # then it owns what it touched first
            owner.setdefault((path, ''), labels)
            for position in positions:
                for area in [position] + ancestors(position):
                    owner.setdefault((path, area), labels)
    return votes, base or set()


def main():
    history = json.load(open(sys.argv[1]))
    repo = sys.argv[2]
    votes, base = nesting(history, repo)
    print(f'base: {", ".join(sorted(base))}')
    # lift: how much more often a feature's changes lie in an owner's places than everyone's do -
    # the base and architectural hubs own much of the code, so raw votes favour them
    all_votes = Counter()
    for feature, v in votes.items():
        all_votes.update({o: n for o, n in v.items() if o != feature})
    grand = sum(all_votes.values())
    print(f'{"feature":26} {"by lift (share of its changes, lift)":46}')
    for feature in sorted(votes, key=lambda f: -sum(votes[f].values())):
        others = {o: n for o, n in votes[feature].items() if o != feature}
        total = sum(others.values())
        if total < 10 or feature in base:
            continue
        lifts = sorted(((n / total) / (all_votes[o] / grand), o, n / total) for o, n in others.items() if n >= 3)
        best = [f'{o} ({share:.0%}, x{lift:.1f})' for lift, o, share in reversed(lifts[-2:])]
        print(f'{feature:26} {"; ".join(best)}')
    print()
    print(f'{"feature":26} {"own":>9}  {"parent besides the base":32} all candidates (votes)')
    for feature in sorted(votes, key=lambda f: -sum(votes[f].values())):
        total = sum(votes[feature].values())
        own = votes[feature][feature]
        others = [(o, n) for o, n in votes[feature].most_common() if o != feature]
        besides = [(o, n) for o, n in others if o not in base]
        share = f'{besides[0][0]} ({besides[0][1] / total:.0%})' if besides else '-'
        print(f'{feature:26} {own:>4}/{total:<4}  {share:32} ' + ', '.join(f'{o} {n}' for o, n in others[:4]))


if __name__ == '__main__':
    main()
