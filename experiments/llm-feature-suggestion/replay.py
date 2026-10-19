"""Replays the scoped commits of a git clone through the Git import's feature-suggestion request.

Ground truth is the scope of a conventional commit message ("fix(css): ..." -> css). The scope is
stripped from the message, so the model judges from the diff. The known features are the true
scopes of the earlier commits, each with the files those commits changed: what a reviewer who
always kept the right feature would have built up (see ImportedFeatureTraces).

The system prompt is read from LlmFeatureSuggestionClient.java, so a replay always tests the
prompt as it is in the code; --edit applies changes to it for trying a variant first.
Writes one JSON object per commit to --out; score.py summarizes.
"""
import argparse
import json
import re
import subprocess
import textwrap
import time
import urllib.request
from pathlib import Path

import evidence as ev

CLIENT = Path(__file__).resolve().parents[2] / 'service/src/main/java/at/jku/isse/ecco/service/llm/LlmFeatureSuggestionClient.java'
FILES_RULE_START = 'Each known feature may be shown with the files'
MAX_DIFF_LINES = 400  # GitHistoryReader.MAX_DIFF_LINES
MAX_FILES_PER_FEATURE = 8  # LlmFeatureSuggestionClient.MAX_FILES_PER_FEATURE
SCOPED = re.compile(r'^(feat|fix|perf|refactor)\(([^)]+)\)!?:\s*(.*)$')


def system_prompt(ref=None):
    """The system prompt as LlmFeatureSuggestionClient.java has it - in the working tree, or at git `ref`."""
    source = CLIENT.read_text() if ref is None else subprocess.run(
        ['git', '-C', str(CLIENT.parent), 'show', f'{ref}:./{CLIENT.name}'], capture_output=True, text=True, check=True).stdout
    block = source.split('SYSTEM_PROMPT = """\n', 1)[1].split('""";', 1)[0]
    return re.sub(r'\\\n', '', textwrap.dedent(block))  # text-block line continuations


def without_files_rule(prompt):
    """The prompt before feature files were added: the paragraph about them removed."""
    start = prompt.index(FILES_RULE_START)
    end = prompt.index('\n\n', start)
    return prompt[:start].rstrip('\n') + prompt[end:]


def git(repo, *args):
    return subprocess.run(['git', '-C', repo, *args], capture_output=True, text=True, errors='replace', check=True).stdout


def scoped_commits(repo, count, ignore_scopes, aliases):
    """The newest `count` scoped feat/fix/perf/refactor commits, oldest first."""
    commits = []
    for line in git(repo, 'log', '--no-merges', '--format=%H%x09%s').splitlines():
        sha, subject = line.split('\t', 1)
        m = SCOPED.match(subject)
        if m and m[2] not in ignore_scopes:
            scopes = [aliases.get(s.strip(), s.strip()) for s in m[2].split(',')]
            commits.append((sha, scopes, m[3]))
            if len(commits) == count:
                break
    return list(reversed(commits))


def diff(repo, sha):
    """As GitHistoryReader.getDiff and LlmFeatureSuggestionClient.stripDiffNoise make it."""
    text = git(repo, 'show', '--format=', '--no-color', sha)
    lines = text.split('\n')
    if len(lines) > MAX_DIFF_LINES:
        text = '\n'.join(lines[:MAX_DIFF_LINES]) + f'\n... (diff truncated, {len(lines) - MAX_DIFF_LINES} more lines)'
    return '\n'.join(l for l in text.split('\n') if not l.startswith('index ')), len(lines)


def changed_files(repo, sha):
    return [l for l in git(repo, 'show', '--format=', '--name-only', sha).splitlines() if l]


def user_content(sha, message, diff_text, known, feature_files, changed, with_files, evidence_text=None):
    """As LlmFeatureSuggestionClient.buildUserContent makes it - or, with `evidence_text`, with that
    in place of the file overlap (evidence.describe)."""
    if evidence_text is not None:  # feature_files are evidence.Traces then
        text = 'Known feature names, each with the more general feature it was built on top of, when known:\n'
        text += '\n'.join(ev.known_lines(known, feature_files)) or '(none yet)'
        return text + '\n\n' + evidence_text + f'\n\nCommit:\nid: {sha[:7]}\nsummary: {message}\nchange:\n{diff_text}'
    lines = []
    for name in known:
        lines.append('- ' + name)
        files = sorted(feature_files.get(name, ())) if with_files else []
        if files:
            shown = ', '.join(files[:MAX_FILES_PER_FEATURE])
            more = f' (+{len(files) - MAX_FILES_PER_FEATURE} more)' if len(files) > MAX_FILES_PER_FEATURE else ''
            lines.append('  files: ' + shown + more)
    text = 'Known feature names, each with the more general feature it was built on top of, when known:\n'
    text += '\n'.join(lines) if lines else '(none yet)'
    if with_files and any(feature_files.get(n) for n in known) and changed:
        overlap = [(n, sum(1 for p in changed if p in feature_files.get(n, ()))) for n in known]
        overlap = sorted([o for o in overlap if o[1]], key=lambda o: -o[1])
        text += '\n\nKnown features whose files this commit changes:\n'
        text += '\n'.join(f'- {n}: {c} of {len(changed)} changed files' for n, c in overlap) \
            if overlap else '(none - no changed file belongs to a known feature)'
    return text + f'\n\nCommit:\nid: {sha[:7]}\nsummary: {message}\nchange:\n{diff_text}'


BASE = 'BASE'  # LlmFeatureSuggestionClient.BASE
EMPTY_TREE = '4b825dc642cb6eb9a060e54bf8d69288fbee4904'  # git's, to diff a first commit against


def predicted_names(prediction):
    """The features a suggestion adds, without new-revision marks; not its removals ("-name") or a failed answer."""
    if not isinstance(prediction, str) or prediction.startswith('<'):
        return []
    names = [t.strip().rstrip("'").strip() for t in prediction.split(',')]
    return [n for n in names if n and not n.startswith('-') and n != BASE]


def ask(endpoint, model, system, user):
    body = {'model': model, 'stream': False, 'temperature': 0, 'response_format': {'type': 'json_object'},
            'messages': [{'role': 'system', 'content': system}, {'role': 'user', 'content': user}]}
    start = time.time()
    request = urllib.request.Request(endpoint.rstrip('/') + '/v1/chat/completions', json.dumps(body).encode(),
                                     {'Content-Type': 'application/json'})
    for attempt in (1, 2):  # a busy shared server fails now and then (timeouts, HTTP 500): once more
        try:
            reply = json.load(urllib.request.urlopen(request, timeout=600))
            break
        except (OSError, ValueError):  # URLError, HTTPError, TimeoutError; a cut-off reply
            if attempt == 2:
                raise
            time.sleep(10)
    content = reply['choices'][0]['message']['content']
    try:
        prediction = json.loads(content).get('configuration', '')
    except ValueError:
        prediction = '<unparseable> ' + content[:100]
    return prediction, reply.get('usage', {}), time.time() - start


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--repo', required=True, help='a local git clone using scoped conventional commits')
    p.add_argument('--model', required=True)
    p.add_argument('--endpoint', default='http://localhost:11434', help='an OpenAI-compatible server (default: local Ollama)')
    p.add_argument('--commits', type=int, default=40)
    p.add_argument('--out', required=True)
    p.add_argument('--names-only', action='store_true', help='known features without their files, and the prompt without the files rule')
    p.add_argument('--edit', action='append', default=[], help='a JSON file of [{"old": ..., "new": ...}] changes to the system prompt')
    p.add_argument('--evidence', action='store_true', help='the evidence section (evidence.py) in place of the file overlap')
    p.add_argument('--no-positions', action='store_true', help='with --evidence: files only, no positions in them')
    p.add_argument('--ecco', help="with --evidence: the ecco command (cli/build/install/ecco/bin/ecco); positions from ECCO's adapters, as the import gets them, instead of git's diff drivers")
    p.add_argument('--baseline-out', help='also ask the prompt as it is in the code, without --edit and --evidence, right after each question; results here')
    p.add_argument('--baseline-ref', help='with --baseline-out: the prompt as it is in this git commit of the code (e.g. HEAD), not the working tree')
    p.add_argument('--ignore-scope', action='append', default=['deps'])
    p.add_argument('--alias', action='append', default=[], help='OLD=NEW: a scope spelled two ways (Vite: bundledDev=bundled-dev)')
    p.add_argument('--truth', help="the ground truth of spl_history.py (<clone>.truth.json): every commit of it, the first as the import's BASE; "
                                   'removals are "-name" - in place of conventional commit scopes')
    p.add_argument('--online', action='store_true', help="with --truth: the known features are the model's own earlier names, as in an import, "
                                                          'not the true ones; score_sets.py matches them to the true features')
    args = p.parse_args()

    system = system_prompt()
    baseline = system_prompt(args.baseline_ref) if args.baseline_ref else system
    if args.names_only:
        system = without_files_rule(system)
    for edit_file in args.edit:
        for edit in json.loads(Path(edit_file).read_text()):
            if edit['old'] not in system:
                raise SystemExit(f'{edit_file}: not in the prompt: {edit["old"][:60]}...')
            system = system.replace(edit['old'], edit['new'])

    if args.truth:
        steps = json.loads(Path(args.truth).read_text())
        commits = [(s['sha'], s['added'] + ['-' + r for r in s['removed']], git(args.repo, 'log', '-1', '--format=%s', s['sha']).strip())
                   for s in steps]
    else:
        commits = scoped_commits(args.repo, args.commits, set(args.ignore_scope), dict(a.split('=', 1) for a in args.alias))
    adapter_positions = ev.ecco_positions(args.ecco, args.repo, [c[0] for c in commits]) if args.ecco else None
    known, feature_files, traces = [], {}, ev.Traces()
    runs = [('variant', system, open(args.out, 'w'))]
    if args.baseline_out:
        runs.append(('baseline', baseline, open(args.baseline_out, 'w')))
    for index, (sha, truth, message) in enumerate(commits):
        if args.truth and index == 0:
            # the import's first commit: BASE, the model is not asked (ImportGitView.processNextCommit)
            known.append(BASE)
            feature_files[BASE] = set(changed_files(args.repo, sha))
            traces.add([BASE], ev.commit_evidence(args.repo, [EMPTY_TREE, sha]))
            for _, _, out in runs:
                out.write(json.dumps(dict(commit=sha[:7], message=message, base=truth[0], truth=[BASE])) + '\n')
            continue
        diff_text, diff_lines = diff(args.repo, sha)
        changed = changed_files(args.repo, sha)
        evidence = ev.commit_evidence(args.repo, [sha + '^', sha])
        if adapter_positions is not None:
            evidence = ev.with_positions(evidence, adapter_positions.get(sha, {}))
        for name, prompt, out in runs:
            if name == 'variant':
                evidence_text = ev.describe(known, traces, evidence, message, positions=not args.no_positions) if args.evidence else None
                user = user_content(sha, message, diff_text, known, traces if args.evidence else feature_files, changed, not args.names_only, evidence_text)
            else:
                user = user_content(sha, message, diff_text, known, feature_files, changed, True)
            try:
                prediction, usage, seconds = ask(args.endpoint, args.model, prompt, user)
            except Exception as e:  # noqa: BLE001 - recorded, the replay goes on
                prediction, usage, seconds = f'<error> {e}', {}, 0
            if name == 'variant':
                last_prediction = prediction
            record = dict(commit=sha[:7], message=message, truth=truth, seen=[t for t in truth if t in known],
                          known=len(known), knownNames=list(known), mode='online' if args.online else 'oracle', prediction=prediction, diffLines=diff_lines,
                          promptTokens=usage.get('prompt_tokens'), seconds=round(seconds, 1))
            out.write(json.dumps(record) + '\n')
            out.flush()
            print(f"{name:8} {sha[:7]} {','.join(truth):15} {'seen' if record['seen'] else 'NEW '} -> {prediction}  ({seconds:.1f} s)", flush=True)
        # what the import learns from the commit: the true features (a reviewer who always corrects
        # the suggestion), or with --online the suggested ones of the first run (one left unreviewed)
        learned = [t for t in truth if not t.startswith('-')] if not args.online else predicted_names(last_prediction)
        for t in learned:
            if t not in known:
                known.append(t)
            feature_files.setdefault(t, set()).update(changed)
        traces.add(learned, evidence)
    for _, _, out in runs:
        out.close()


if __name__ == '__main__':
    main()
