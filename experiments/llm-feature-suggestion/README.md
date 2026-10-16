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
  changed, and the overlap with this commit's files. That is what `ImportedFeatureFiles` builds up
  for a reviewer who always kept the right feature.

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
`ImportedFeatureFiles` attributes to each feature.

## Running it

```sh
git clone --depth 600 https://github.com/vitejs/vite.git /tmp/vite
python3 replay.py --repo /tmp/vite --model qwen2.5-coder:32b --endpoint http://localhost:11434 \
    --commits 120 --alias bundledDev=bundled-dev --out results/mine.jsonl
python3 score.py results/vite120-qwen2.5-coder-32b-files.jsonl results/mine.jsonl
```

- `--names-only` replays the prompt as it was before feature files: no files and no rule about them.
- `--edit variants/x.json` applies `[{"old": ..., "new": ...}]` changes to the system prompt, to try
  a variant before changing the Java code. The variants tried so far are in `variants/`.
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

Feature files went in (commit 87cf1a45); neither variant did. Notes on reading the table:

- The runs with feature files listed each feature's files by how often they changed, not
  alphabetically as the import does, so they are close to the import, not identical.
- Some "wrong" answers are noise in Vite's own labels: `optimizer` and `optimize-deps` are the same
  thing.
- The gap that remains is spotting new features: 13 of 28, with every prompt tried.
