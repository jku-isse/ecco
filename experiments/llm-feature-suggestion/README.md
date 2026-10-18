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

## Product lines: precision and recall

The Vite truth has one feature per commit, and its scopes are the project's own words. A product
line gives a truth with sets of features, and whose code is known exactly (Nguyen et al., 2026,
"Investigating LLMs for Feature Presence Prediction in Software Products", used the same ones).

- `spl_history.py` composes a FeatureHouse product line from SPL2go
  (<https://spl2go.cs.ovgu.de>, e.g. `data/Elevator_Java.zip`) into a git history: the base
  feature first, then one feature per commit and every third commit two, then two features
  removed and added back. Messages are "Change N", so the model judges from the diff. The truth
  goes to `<clone>.truth.json`. FeatureHouse is the jar of the paper's replication package
  (<https://github.com/gunguyen/LLM_as_code_query_engines-Variability26>, `feature_house/`).
- `replay.py --truth <clone>.truth.json` asks every commit but the first, which is BASE as in the
  import. The known features are the true ones (oracle: a reviewer who always corrects the
  suggestion); with `--online` they are the model's own earlier names, as in an unattended import.
- `score_sets.py` gives precision and recall apart, recall of features known and new, made-up
  names (a new name for no feature the commit adds), "misc"/"documentation" (always wrong here)
  and removals.

```sh
python3 spl_history.py --features ElevatorPL/features --order ElevatorPL/allFeatures.exp \
    --jar FeatureHouse-2011-03-15.jar --out /tmp/spl/elevator
python3 spl_history.py --features CC_GPL-FH-Java/features --order CC_GPL-FH-Java/configs/Test3.config \
    --reverse --jar FeatureHouse-2011-03-15.jar --out /tmp/spl/gpl
python3 replay.py --repo /tmp/spl/elevator --truth /tmp/spl/elevator.truth.json --evidence \
    --model gemma4:31b --out results/spl-elevator-gemma4-31b-oracle.jsonl
python3 score_sets.py results/spl-*.jsonl
```

The truth is strict: a feature counts for the commit that adds or removes its directory. Code
that only interacts with it changes too - FeatureHouse refines methods of features already there,
and Elevator's `weight` directory holds the specifications of `overloaded` and `twothirdsfull` -
so a known feature named for such code counts as wrong, though its name is in the diff.

Results (October 2026, prompt of abbc980f, `--evidence` without `--ecco`; histories with the
defaults above, Elevator, Email and MinePump from `allFeatures.exp`, GPL from `Test3.config`):

| Model | Mode | Commits | Precision | Recall | Recall known / new | Made up | misc/doc | Removals P / R | Empty answers | Server errors |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| gemma4:31b | oracle | 40 | 0.36 | 0.76 | 7/8 / 22/30 | 2 | 5 | 0.78 / 0.88 | 0 | 0 |
| gemma4:31b | online | 40 | 0.35 | 0.76 | 6/7 / 23/31 | 3 | 6 | 0.78 / 0.88 | 0 | 1 |
| gpt-oss:20b | oracle | 40 | 0.82 | 0.71 | 5/8 / 22/30 | 0 | 2 | 0.67 / 0.75 | 5 | 0 |
| gpt-oss:20b | online | 40 | 0.81 | 0.68 | 2/7 / 24/31 | 2 | 1 | 0.56 / 0.62 | 5 | 3 |

gemma4 names nearly every known feature whose identifiers the diff touches (adding `autoresponder`
to Email: `autoresponder, encrypt, sign, addressbook, decrypt, verify`); gpt-oss names few, and
answered nothing five times, so a known feature added back was missed. Per history:
`python3 score_sets.py results/spl-*.jsonl`.

Online, with the model's own names as the known features, both keep their precision and recall:
the names it chose stand in for the true ones about as well. What differs is a feature that comes
back: gemma4 reuses its earlier name (6 of 7), gpt-oss mostly gives it a new one (2 of 7; MinePump's
`low-water-monitoring` returns as `low-water-level`, Elevator's `weight-based-leave` as
`empty-elevator`), so an import would get a second feature for it. The server errors are timeouts
and one HTTP 500 of the shared server, not answers.

Two prompt changes, online, one run each (`--edit`):

- `variants/own-code-only.json` replaces the rule to list a known feature whose shared state or
  files the diff touches with: list a known feature only when the commit changes its own code.
- `variants/returning-feature-same-name.json` adds: a feature removed earlier may come back; give
  it its known name again.

| Model | Prompt | Precision | Recall | Returning feature, old name | Made up | Removals P / R | Empty answers | Server errors |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| gemma4:31b | as in the code | 0.35 | 0.76 | 6 of 7 | 3 | 0.78 / 0.88 | 0 | 1 |
| gemma4:31b | own code only | 0.68 | 0.79 | 7 of 8 | 1 | 0.62 / 0.62 | 2 | 0 |
| gemma4:31b | both | 0.59 | 0.76 | 6 of 8 | 3 | 0.60 / 0.75 | 1 | 0 |
| gpt-oss:20b | as in the code | 0.81 | 0.68 | 2 of 7 | 2 | 0.56 / 0.62 | 5 | 3 |
| gpt-oss:20b | returning name | 0.91 | 0.79 | 5 of 7 | 1 | 0.71 / 0.62 | 6 | 0 |
| gpt-oss:20b | both | 0.79 | 0.68 | 3 of 6 | 2 | 0.67 / 0.50 | 5 | 3 |

The own-code rule nearly doubles gemma4's precision; with both changes the two models fall back
part or all of the way. The answers differ from run to run though temperature is 0 (the shared
server), so a run of 40 commits is noisy: gpt-oss "both" lost two returning features to server
errors. Repeated runs would tell the changes from the noise.

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

## Confidence and review

`confidence.py` gives each replayed suggestion a confidence from how it agrees with where the commit
changed things. `SuggestionConfidence` has the same rules. It then checks whether the least
confident suggestions are the wrong ones:

```sh
python3 confidence.py results/vite120-qwen2.5-coder-32b-evidence.jsonl positions.json
```

`positions.json` holds the commits with their labels and `ecco positions` output (see `nesting.py`).
The rules were written before the first run, not fitted to it. On the 120 Vite commits:

| Run | Reviewing the least confident | Wrong suggestions seen | Missed new features seen |
| --- | --- | --- | --- |
| 32B, evidence prompt | 20% | 37% | 40% |
| | 30% | 54% | 60% |
| 14B, evidence prompt | 20% | 41% | 59% |
| | 30% | 57% | 71% |

Reviewing every Nth commit sees about as many as it reviews. Below 0.5, only 4 of 42 of the 32B's
suggestions were right; at 0.9, 24 of 26. The import stops an unattended commit for review below 0.5.

