"""Makes a git history with known features from a FeatureHouse product line (SPL2go), for replay.py --truth.

Each commit is a variant of the product line, composed by FeatureHouse: the first has the base
feature only, each later one adds one or two features (in composition order), and at the end some
are removed and added back. The messages say nothing about the features ("Change 3"), so a model
has to judge from the diff. Writes the clone and `truth.json` next to it: for each commit its sha,
the features it adds and removes, and its configuration.

    python3 spl_history.py --features ElevatorPL/features --order ElevatorPL/allFeatures.exp \
        --jar FeatureHouse-2011-03-15.jar --out /tmp/spl/elevator
"""
import argparse
import json
import random
import shutil
import subprocess
import tempfile
from pathlib import Path


def git(repo, *args):
    return subprocess.run(['git', '-C', str(repo), *args], capture_output=True, text=True, check=True).stdout


def compose(jar, features_dir, configuration, into):
    """The variant of [configuration] (features in composition order), its files put into [into]."""
    with tempfile.TemporaryDirectory() as tmp:
        expression = Path(tmp) / 'variant.exp'
        expression.write_text('\n'.join(configuration) + '\n')
        subprocess.run(['java', '-jar', str(jar), '--expression', str(expression), '--base-directory', str(features_dir),
                        '--output-directory', tmp], capture_output=True, text=True, check=True)
        composed = Path(tmp) / 'variant'
        if not composed.is_dir():
            raise SystemExit(f'FeatureHouse composed nothing for {configuration}')
        for old in into.iterdir():
            if old.name != '.git':
                shutil.rmtree(old) if old.is_dir() else old.unlink()
        for path in composed.rglob('*'):
            if path.is_file():
                target = into / path.relative_to(composed)
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(path, target)


def plan(order, removals, pair_every, seed):
    """The steps: (added, removed) - the base first, then one feature at a time, every
    [pair_every]th step two, then [removals] features removed one at a time and added back."""
    rng = random.Random(seed)
    steps = [([order[0]], [])]
    rest = order[1:]
    i = step = 0
    while i < len(rest):
        step += 1
        take = 2 if pair_every and step % pair_every == 0 and i + 1 < len(rest) else 1
        steps.append((rest[i:i + take], []))
        i += take
    for feature in rng.sample(rest, min(removals, len(rest))):
        steps.append(([], [feature]))
        steps.append(([feature], []))
    return steps


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--features', required=True, help="the product line's feature directories")
    p.add_argument('--order', required=True, help='the features in composition order, one per line (allFeatures.exp, a .config); the first is the base')
    p.add_argument('--jar', required=True, help='FeatureHouse')
    p.add_argument('--out', required=True, help='the clone to make (replaced); truth.json goes next to it')
    p.add_argument('--reverse', action='store_true', help='the order file lists the base last (GPL\'s .config files)')
    p.add_argument('--removals', type=int, default=2)
    p.add_argument('--pair-every', type=int, default=3, help='every Nth step adds two features (0: never)')
    p.add_argument('--seed', type=int, default=1)
    args = p.parse_args()

    order = [l.strip() for l in Path(args.order).read_text().split() if l.strip()]
    if args.reverse:
        order.reverse()
    out = Path(args.out)
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    git(out, 'init', '-q', '-b', 'main')
    git(out, 'config', 'user.name', 'spl_history')
    git(out, 'config', 'user.email', 'spl_history@example.invalid')

    configuration, truth = [], []
    for n, (added, removed) in enumerate(plan(order, args.removals, args.pair_every, args.seed)):
        chosen = (set(configuration) | set(added)) - set(removed)
        configuration = [f for f in order if f in chosen]
        compose(Path(args.jar).resolve(), Path(args.features).resolve(), configuration, out)
        git(out, 'add', '-A')
        git(out, 'commit', '-q', '--allow-empty', '-m', f'Change {n}')
        sha = git(out, 'rev-parse', 'HEAD').strip()
        truth.append(dict(sha=sha, added=added, removed=removed, configuration=configuration))
        print(f'{sha[:7]} +{",".join(added) or "-"} -{",".join(removed) or "-"}', flush=True)
    (out.parent / f'{out.name}.truth.json').write_text(json.dumps(truth, indent=1) + '\n')


if __name__ == '__main__':
    main()
