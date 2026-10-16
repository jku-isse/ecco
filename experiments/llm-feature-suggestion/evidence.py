"""Deterministic evidence about a commit, for the feature suggestion to weigh (step 1 of the design).

Features are cross-cutting: one feature's changes can be source, tests, documentation, build files.
So the kind of a file is evidence about the change, never a feature by itself; what links a change
to a known feature is where it is - the same files, and better, the same positions (enclosing
function, class or heading) that the feature's earlier commits changed.

- kind of each changed file: source, test, documentation, build, ci, resource, other
- positions: the enclosing function/class/heading of each hunk, from git's built-in diff drivers
- comment-only changes in source files
- message hints: release/version, merge, revert, issue references
- trace overlap: per known feature, the changed files and positions its earlier commits changed too
"""
import re
import subprocess
import tempfile
from collections import defaultdict
from pathlib import Path

# git's built-in function-context patterns, chosen by extension
DRIVERS = {
    'java': ['java'], 'javascript': ['js', 'jsx', 'mjs', 'cjs', 'ts', 'tsx', 'mts', 'cts', 'vue'],
    'cpp': ['c', 'h', 'cc', 'cpp', 'hpp', 'cxx'], 'python': ['py'], 'golang': ['go'], 'rust': ['rs'],
    'csharp': ['cs'], 'kotlin': ['kt'], 'markdown': ['md', 'markdown'], 'css': ['css', 'scss', 'less'],
    'html': ['html', 'htm'], 'ruby': ['rb'], 'php': ['php'],
}
_ATTRIBUTES = tempfile.NamedTemporaryFile('w', suffix='.gitattributes', delete=False)
_ATTRIBUTES.write(''.join(f'*.{ext} diff={driver}\n' for driver, exts in DRIVERS.items() for ext in exts))
_ATTRIBUTES.close()

TEST = re.compile(r'(^|/)(tests?|__tests__|spec|testing|test-?utils?)/|\.(spec|test)\.[^/]+$|(Test|Tests|IT)\.(java|kt)$|_test\.(go|py)$|(^|/)test_[^/]+\.py$', re.I)
DOCS = re.compile(r'(^|/)(docs?|documentation)/|\.(md|markdown|rst|adoc|txt)$|(^|/)(readme|changelog|contributing|authors|notice)[^/]*$', re.I)
CI = re.compile(r'(^|/)(\.github|\.circleci|\.gitlab)/|(^|/)(\.travis\.yml|\.gitlab-ci\.yml|azure-pipelines\.yml|jenkinsfile|appveyor\.yml)$', re.I)
BUILD = re.compile(r'(^|/)(pom\.xml|build\.gradle(\.kts)?|settings\.gradle(\.kts)?|gradle\.properties|gradlew(\.bat)?|package\.json|package-lock\.json|pnpm-lock\.yaml|yarn\.lock|'
                   r'pnpm-workspace\.yaml|tsconfig[^/]*\.json|makefile|cmakelists\.txt|[^/]+\.cmake|setup\.py|setup\.cfg|pyproject\.toml|requirements[^/]*\.txt|'
                   r'cargo\.toml|cargo\.lock|go\.mod|go\.sum|[^/]+\.csproj|license[^/]*|\.gitignore|\.gitattributes|\.editorconfig|\.npmignore|'
                   r'rolldown\.config\.[^/]+|rollup\.config\.[^/]+|vite\.config\.[^/]+|eslint[^/]*|\.prettierrc[^/]*)$', re.I)
SOURCE = re.compile(r'\.(' + '|'.join(e for exts in DRIVERS.values() for e in exts if e not in ('md', 'markdown')) + r'|scala|swift|m|mm|sh|sql|groovy)$', re.I)
RESOURCE = re.compile(r'\.(json|ya?ml|xml|properties|toml|ini|svg|png|jpe?g|gif|ico|woff2?|ttf|csv)$', re.I)
COMMENT = re.compile(r'^\s*(//|/\*|\*|\*/|#(?!include|define|if|endif|else|pragma)|<!--)')


def kind(path):
    """The kind of a changed file. Tests and documentation first: a test's source is a test."""
    for name, pattern in (('test', TEST), ('ci', CI), ('build', BUILD), ('documentation', DOCS), ('source', SOURCE), ('resource', RESOURCE)):
        if pattern.search(path):
            return name
    return 'other'


def _git(repo, *args):
    return subprocess.run(['git', '-C', repo, '-c', f'core.attributesFile={_ATTRIBUTES.name}', *args],
                          capture_output=True, text=True, errors='replace', check=True).stdout


def _position(context):
    """A hunk header's function context, normalized: 'export function cssPostPlugin(config: ...) {' -> 'cssPostPlugin'."""
    text = context.strip()
    if re.match(r'(import|package|using|from|export \{|export \* from|#include|require\()\b', text):
        return None  # the file's head, no position
    if text.startswith('#'):
        return text.lstrip('#').strip()[:60]  # a Markdown heading
    m = re.search(r'([A-Za-z_$][\w$]*)\s*(?:<[^>]*>)?\s*\(', text)  # the name before the first '('
    if m and m[1] not in ('if', 'for', 'while', 'switch', 'catch', 'return', 'function', 'test', 'it', 'describe'):
        return m[1]
    m = re.search(r'\b(?:class|interface|enum|struct|module|namespace|object|trait|impl|type)\s+([A-Za-z_$][\w$]*)', text)
    if m:
        return m[1]
    m = re.search(r"\b(?:test|it|describe)\s*\(\s*['\"`]([^'\"`]{1,60})", text)  # a test's name
    if m:
        return m[1]
    return text[:60] or None


def with_positions(evidence, files):
    """`evidence` with each file's positions and line counts replaced by those of `ecco positions`
    ({path: {"added", "removed", "positions"}}), as the import gets them; a file it leaves out has none."""
    result = {}
    for path, f in evidence.items():
        e = files.get(path, {})
        result[path] = dict(f, positions=list(e.get('positions', [])), added=e.get('added', f['added']), removed=e.get('removed', f['removed']))
    return result


def ecco_positions(ecco, clone, commits):
    """{commit: {path: {"added", "removed", "positions"}}} from `ecco positions`, run in a scratch ECCO repository."""
    import json
    home = tempfile.mkdtemp(prefix='ecco-positions-')
    subprocess.run([ecco, 'init'], cwd=home, capture_output=True, check=True)
    result = {}
    for i in range(0, len(commits), 50):
        out = subprocess.run([ecco, 'positions', str(Path(clone).resolve()), *commits[i:i + 50]], cwd=home,
                             capture_output=True, text=True, errors='replace', check=True).stdout
        for line in out.splitlines():
            if line.startswith('{'):
                entry = json.loads(line)
                result[entry['commit']] = entry['files']
    return result


def commit_evidence(repo, range_args):
    """Per changed file: kind, lines added/removed, positions, comment-only. `range_args` as for git diff."""
    files = {}
    current = None
    for line in _git(repo, 'diff', '--no-color', '-U0', '--no-renames', *range_args).split('\n'):
        if line.startswith('diff --git '):
            m = re.search(r' "?b/(.+?)"?$', line)
            current = m[1] if m else line
            files[current] = dict(kind=kind(current), added=0, removed=0, positions=[], commentOnly=True)
        elif current is None:
            continue
        elif line.startswith('@@'):
            context = line.split('@@', 2)[2] if line.count('@@') >= 2 else ''
            position = _position(context) if context.strip() else None
            if position and position not in files[current]['positions']:
                files[current]['positions'].append(position)
        elif line[:1] in ('+', '-') and not line.startswith(('+++', '---')):
            files[current]['added' if line[0] == '+' else 'removed'] += 1
            if line[1:].strip() and not COMMENT.match(line[1:]):
                files[current]['commentOnly'] = False
    for f in files.values():
        if f['kind'] != 'source' or f['added'] + f['removed'] == 0:
            f['commentOnly'] = False
    return files


MESSAGE_HINTS = [
    ('release or version', re.compile(r'\b(release|v?\d+\.\d+(\.\d+)?|version|bump)\b', re.I)),
    ('merge', re.compile(r'^merge\b', re.I)),
    ('revert', re.compile(r'^revert\b', re.I)),
    ('issue reference', re.compile(r'(#\d+|\bissue\b|\bfix(es|ed)? #?\d+)', re.I)),
]


def message_hints(message):
    return [name for name, pattern in MESSAGE_HINTS if pattern.search(message)]


NESTING = (' > ', '::', '.')


def _parent(position):
    cut = max(position.rfind(sep) for sep in NESTING)
    return position[:cut] if cut > 0 else None


def shared_enclosing(a, b):
    """As ImportedFeatureTraces.sharedEnclosing."""
    pa, pb = _parent(a), _parent(b)
    if pa is not None and (pa == pb or pa == b):
        return pa
    if pb is not None and pb == a:
        return a
    return None


class Traces:
    """As ImportedFeatureTraces: per feature, per file, the positions its earlier commits changed."""

    def __init__(self):
        self.traces = defaultdict(dict)

    def add(self, features, evidence):
        for feature in features:
            for path, f in evidence.items():
                self.traces[feature].setdefault(path, set()).update(f['positions'])

    def files(self, feature):
        return sorted(self.traces.get(feature, {}))

    def positions(self, feature):
        """Names in the order of ImportedFeatureTraces.positions(): sorted by "file#name", de-duplicated."""
        names = []
        for key in sorted(f'{path}#{name}' for path, ns in self.traces.get(feature, {}).items() for name in ns):
            name = key[key.index('#') + 1:]
            if name not in names:
                names.append(name)
        return names

    def overlap(self, evidence):
        """As ImportedFeatureTraces.overlap: (feature, same positions, same enclosing, same files), strongest first."""
        result = []
        for feature in sorted(self.traces):
            trace = self.traces[feature]
            same, enclosing, files = [], [], 0
            for path, f in evidence.items():
                if path not in trace:
                    continue
                files += 1
                for position in f['positions']:
                    if position in trace[path]:
                        same.append(position)
                        continue
                    for other in sorted(trace[path]):
                        shared = shared_enclosing(position, other)
                        if shared is not None and shared not in enclosing:
                            enclosing.append(shared)
            if same or enclosing or files:
                result.append((feature, same, enclosing, files))
        return sorted(result, key=lambda r: (-len(r[1]), -len(r[2]), -r[3]))


def _limited(items, n):
    items = list(items)
    return ', '.join(items[:n]) + (f' (+{len(items) - n} more)' if len(items) > n else '')


def known_lines(known, traces, max_files=8, max_positions=4):
    """The known features, as LlmFeatureSuggestionClient lists them with their traces."""
    lines = []
    for name in known:
        lines.append('- ' + name)
        files = traces.files(name)
        if files:
            lines.append('  files: ' + _limited(files, max_files))
            positions = traces.positions(name)
            if positions:
                lines.append('  positions: ' + _limited(positions, max_positions))
    return lines


def describe(known, traces, evidence, message, max_positions=4, max_changed=30, positions=True):
    """The evidence section, as LlmFeatureSuggestionClient.evidence makes it; without positions,
    files only (to measure what positions add)."""
    if not positions:
        evidence = {path: dict(f, positions=[]) for path, f in evidence.items()}
    lines = ['Changed files (kind, lines added/removed, where in the file):']
    kinds = {}
    for i, (path, f) in enumerate(evidence.items()):
        kinds[f['kind']] = kinds.get(f['kind'], 0) + 1
        if i >= max_changed:
            continue
        line = f'- {path} ({f["kind"]}, +{f["added"]} -{f["removed"]})'
        if f['positions']:
            line += ' in: ' + _limited(f['positions'], max_positions)
        lines.append(line)
    if len(evidence) > max_changed:
        lines.append(f'- ... {len(evidence) - max_changed} more files')
    lines.append('Kinds: ' + ', '.join(f'{n} {k}' for k, n in sorted(kinds.items(), key=lambda kv: -kv[1])))
    lines.append('')
    lines.append('Known features whose earlier commits changed the same places:')
    overlap = [o for o in traces.overlap(evidence) if o[0] in known]
    if not overlap:
        lines.append('(none - this commit changes no place a known feature changed before)')
    for feature, same, enclosing, files in overlap:
        parts = []
        if same:
            parts.append('same positions ' + _limited(same, max_positions))
        if enclosing:
            parts.append('in the same ' + _limited(enclosing, max_positions))
        parts.append(f'{files} of {len(evidence)} changed files')
        lines.append(f'- {feature}: ' + '; '.join(parts))
    hints = message_hints(message)
    if hints:
        lines += ['', 'Commit message hints: ' + ', '.join(hints)]
    return '\n'.join(lines)
