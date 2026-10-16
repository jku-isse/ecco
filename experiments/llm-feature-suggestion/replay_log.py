"""Replays the commits of one logged Git import (~/.ecco/llm-suggestions.jsonl) through the
feature-suggestion request, with the known features and files that import had at each commit.

There is no ground truth here; what is measured is how "documentation" is used, which can be
checked from the commit itself: given while nothing the commit changes is documentation (no docs
file, no comment line), or missed on a commit that changes nothing but documentation. Compare a
prompt variant (--edit) with the prompt as it is (--baseline-out, asked right after the variant on
each commit, so both meet the same server), not with the log: the log's import also showed each
feature's parent, which this replay does not. --documentation-only asks only about the commits
whose logged suggestion had "documentation" or that change only documentation; the known features
and files are still built up from every commit.
"""
import argparse
import json
import re
from pathlib import Path

import replay

DOCS_FILE = re.compile(r'(^|/)(readme[^/]*|docs?/.*|[^/]+\.(md|markdown|adoc|rst|txt))$', re.IGNORECASE)
SOURCE_FILE = re.compile(r'\.(java|kt|c|h|cc|cpp|hpp|cs|js|ts|py|go|rs|swift|scala)$')
COMMENT_LINE = re.compile(r'^\s*(//|/\*|\*|\*/|#)')


def first_parent_range(repo, sha):
    """As GitHistoryReader diffs: against the first parent, or the empty tree for a root commit."""
    parents = replay.git(repo, 'rev-list', '--parents', '-n', '1', sha).split()[1:]
    return [parents[0], sha] if parents else ['4b825dc642cb6eb9a060e54bf8d69288fbee4904', sha]


def diff(repo, sha):
    text = replay.git(repo, 'diff', '--no-color', *first_parent_range(repo, sha))
    lines = text.split('\n')
    if len(lines) > replay.MAX_DIFF_LINES:
        text = '\n'.join(lines[:replay.MAX_DIFF_LINES]) + f'\n... (diff truncated, {len(lines) - replay.MAX_DIFF_LINES} more lines)'
    return '\n'.join(l for l in text.split('\n') if not l.startswith('index ')), len(lines)


def changed_files(repo, sha):
    return [l for l in replay.git(repo, 'diff', '--name-only', *first_parent_range(repo, sha)).splitlines() if l]


def documentation(repo, sha):
    """(some documentation changed, only documentation changed): docs files and comment lines in source files."""
    text = replay.git(repo, 'diff', '--no-color', '-U0', *first_parent_range(repo, sha))
    some, only, current = False, True, None
    for line in text.split('\n'):
        if line.startswith('diff --git '):
            m = re.search(r' "?b/(.+?)"?$', line)
            current = m[1] if m else line
            if DOCS_FILE.search(current):
                some = True
            elif not SOURCE_FILE.search(current):
                only = False
        elif current and not DOCS_FILE.search(current) and SOURCE_FILE.search(current) \
                and line[:1] in '+-' and not line.startswith(('+++', '---')):
            body = line[1:]
            if not body.strip():
                continue
            if COMMENT_LINE.match(body):
                some = True
            else:
                only = False
    return some, only and some


def tokens(text):
    return [t.strip() for t in (text or '').split(',') if t.strip()]


def labels(before, suggestion, committed):
    """ImportedFeatureTraces.labels."""
    committed_features = tokens(committed)
    result = [f for f in committed_features if f not in tokens(before)]
    result += [t for t in tokens(suggestion) if not t.startswith('-') and t in committed_features and t not in result]
    return result


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--log', default=str(Path.home() / '.ecco' / 'llm-suggestions.jsonl'))
    p.add_argument('--ecco-repository', required=True, help='which import of the log, as its eccoRepository field says')
    p.add_argument('--model', required=True)
    p.add_argument('--endpoint', default='http://localhost:11434')
    p.add_argument('--out', required=True)
    p.add_argument('--edit', action='append', default=[])
    p.add_argument('--baseline-out', help='also ask with the prompt as it is, results here')
    p.add_argument('--documentation-only', action='store_true')
    args = p.parse_args()

    entries = [json.loads(line) for line in open(args.log)]
    entries = [e for e in entries if e.get('eccoRepository') == args.ecco_repository and e.get('outcome') != 'skipped']
    if not entries:
        raise SystemExit('no imported commits of ' + args.ecco_repository + ' in ' + args.log)
    baseline = replay.system_prompt()
    system = baseline
    for edit_file in args.edit:
        for edit in json.loads(Path(edit_file).read_text()):
            if edit['old'] not in system:
                raise SystemExit(f'{edit_file}: not in the prompt: {edit["old"][:60]}...')
            system = system.replace(edit['old'], edit['new'])

    feature_files = {}
    runs = [(system, open(args.out, 'w'))] + ([(baseline, open(args.baseline_out, 'w'))] if args.baseline_out else [])
    for e in entries:
        repo, sha = e['gitRepository'], e['commit']
        some_docs, only_docs = documentation(repo, sha)
        changed = changed_files(repo, sha)
        if not args.documentation_only or only_docs or 'documentation' in tokens(e.get('suggestion')):
            diff_text, diff_lines = diff(repo, sha)
            user = replay.user_content(sha, e['message'], diff_text, tokens(e['before']), feature_files, changed, True)
            for prompt, out in runs:
                try:
                    prediction, usage, seconds = replay.ask(args.endpoint, args.model, prompt, user)
                except Exception as ex:  # noqa: BLE001 - recorded, the replay goes on
                    prediction, usage, seconds = f'<error> {ex}', {}, 0
                record = dict(commit=sha[:7], message=e['message'], logged=e.get('suggestion'), prediction=prediction,
                              someDocumentation=some_docs, onlyDocumentation=only_docs, files=changed[:5],
                              promptTokens=usage.get('prompt_tokens'), seconds=round(seconds, 1))
                out.write(json.dumps(record) + '\n')
                out.flush()
                print(f"{sha[:7]} {'docs' if only_docs else 'some' if some_docs else '    '} -> {prediction}  ({seconds:.1f} s)", flush=True)
        for label in labels(e['before'], e.get('suggestion'), e['committed']):
            feature_files.setdefault(label, set()).update(changed)
    for _, out in runs:
        out.close()


if __name__ == '__main__':
    main()
