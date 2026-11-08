# Experiments

`llm-feature-suggestion/` is a Python harness that runs anywhere: it scores the Git import's LLM
feature suggestions against a history whose features are known (see its README).

The Java research harnesses that were here - moved out of the integration tests in October 2026,
running ECCO on the data of case studies, benchmarks and papers that was never checked in, so that
they could not run anywhere else - are on the branch `archive/unbuilt-modules`, at their paths
under `experiments/`, with the list of what each needs in this README there. To run one, take it
from that branch back to the path it had in its module (`<module>/src/integrationTest/...`),
provide its data, adjust the paths it hard-codes and remove its `@Disabled`; expect API drift.
