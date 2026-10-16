# LLM feature suggestion replay

Scores the feature suggestions of the Git import (`LlmFeatureSuggestionClient`) against a history
whose features are known, so a change to the prompt or the model can be measured before it goes in.

Unlike the other harnesses here, this one runs anywhere: Python 3 (no packages), `git`, and an
OpenAI-compatible server such as Ollama.

## Method

The ground truth is the scope of conventional commit messages: `fix(css): ...` belongs to `css`.
`replay.py` takes the newest N `feat`/`fix`/`perf`/`refactor` commits with a scope, oldest first,
and sends each one as the import would:

- the system prompt is read from `LlmFeatureSuggestionClient.java`, so a replay tests the prompt as
  it is in the code;
- the message is sent without its `type(scope):` prefix, so the model has to judge from the diff;
- the diff is cut after 400 lines and loses its `index` lines, as in the import;
- the known features are the true scopes of the earlier commits, each with the files those commits
  changed, and the overlap with this commit's files. That is what `ImportedFeatureTraces` builds up
  for a reviewer who always kept the right feature.
- with `--evidence`, the prompt is the import's current one (`evidence.py` builds the same text as
  `LlmFeatureSuggestionClient.evidence`). Each changed file comes with its kind, lines added and
  removed, and positions, i.e. classes, methods and sections. Each known feature comes with the
  files and positions its earlier commits changed, plus the overlap at three levels: same position,
  same enclosing position, same file.
- `--ecco cli/build/install/ecco/bin/ecco` takes the positions from ECCO's adapters
  (`ecco positions`, after `./gradlew :ecco-cli:installDist`), as the import does. Without it they
  come from git's built-in diff drivers, which only approximate them.

`score.py` sorts each answer into one of these categories:

| Commit's feature | Category | Meaning |
| --- | --- | --- |
| seen before | exact | the right feature, alone |
| | right plus extra | the right feature and others |
| | other known | a wrong known feature |
| | made-up name | a new name for a known feature |
| new | new name | a name not known yet |
| | forced into a known one | a known feature instead of a new one |

In an import, only new names change the committed configuration, because a known feature is
already in every later configuration. So a made-up name adds a bogus feature and a feature forced
into a known one is lost. An extra or wrong known feature only blurs the files that
`ImportedFeatureTraces` attributes to each feature.

## Running it

```sh
git clone --depth 600 https://github.com/vitejs/vite.git /tmp/vite
python3 replay.py --repo /tmp/vite --model qwen2.5-coder:32b --endpoint http://localhost:11434 \
    --commits 120 --alias bundledDev=bundled-dev --out results/mine.jsonl
python3 score.py results/vite120-qwen2.5-coder-32b-files.jsonl results/mine.jsonl
```

- `--names-only` replays the prompt as it was before feature files: no files and no rule about them.
- `--baseline-out FILE --baseline-ref HEAD` also asks the prompt as committed in `HEAD`, right after the
  variant on each commit. Both then meet the same server, which matters on a shared one.
- `--edit variants/x.json` applies `[{"old": ..., "new": ...}]` changes to the system prompt, to try
  a variant before changing the Java code. The variants in `variants/` were written against the
  prompt of commit 87cf1a45, and do not apply to the current one.
- `replay_log.py` replays the commits of an import the GUI logged (`~/.ecco/llm-suggestions.jsonl`)
  with the known features and traces that import had. It has no ground truth.
- A local Ollama loads a model with a 4,096-token context unless told otherwise. Longer prompts lose
  their beginning, the system prompt, without notice, so prompts of 5,000–9,000 tokens need a model
  variant with a larger context: `FROM qwen2.5-coder:14b` and `PARAMETER num_ctx 16384` in a
  Modelfile, then `ollama create`.
- Results are deterministic only up to the server: two runs of the same prompt at temperature 0
  agreed on 37 of 40 answers. Treat differences of about ±2 per 40 commits as noise.
- On a server that holds one model at a time, keep other models off it. Each swap reloads the
  model, which took 15–45 s per request instead of 1–2 s.

## Results (Vite, October 2026)

| Run | Commits | Seen: exact / extra / other known / made-up | New: new name / forced |
| --- | --- | --- | --- |
| qwen2.5-coder:32b, names only | 40 | 17 / 2 / 7 / 1 | 2 / 11 |
| gemma4:31b, names only | 40 | 11 / 9 / 3 / 4 | 8 / 5 |
| qwen2.5-coder:32b, with feature files | 40 | 17 / 4 / 1 / 5 | 5 / 5 |
| qwen2.5-coder:32b, with feature files | 120 | 41 / 29 / 16 / 4 | 13 / 11 |
| … plus `variants/feature-as-a-whole.json` | 120 | 41 / 29 / 17 / 3 | 13 / 9 |
| … plus `variants/no-shared-feature-rule.json` | 120 | 53 / 11 / 20 / 6 | 13 / 10 |
| qwen2.5-coder:32b, files prompt (HEAD), paired | 120 | 37 / 28 / 20 / 6 | 14 / 10 |
| qwen2.5-coder:32b, evidence + adapter positions, paired | 120 | 48 / 18 / 19 / 6 | 14 / 12 |
| qwen2.5-coder:14b (local, 16k context), evidence + adapter positions | 120 | 58 / 8 / 19 / 6 | 11 / 16 |

Feature files went in (commit 87cf1a45); neither variant did. The evidence prompt with adapter
positions went in next. Asked on the same commits, it was right 16 times where the files prompt was
wrong, and the files prompt 6 times where it was wrong. Most of the gain is fewer extra features; new
features are named as often as before. The local 14B is the most exact on known features, but forces
16 of 28 new features into known ones: an import loses each of those. Notes on reading the table:

- The runs with feature files listed each feature's files by how often they changed, not
  alphabetically as the import does, so they are close to the import, not identical.
- Some "wrong" answers are noise in Vite's own labels: `optimizer` and `optimize-deps` are the same
  thing.
- The gap that remains is spotting new features: 13 of 28, with every prompt tried.
