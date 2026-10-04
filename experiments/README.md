# Experiments

Research harnesses moved out of the integration tests (October 2026). They ran ECCO on the data of
case studies, benchmarks and papers that was never checked in, so they cannot run anywhere else and
were always skipped. Gradle does not build this directory.

Each file keeps the path it had in its module (`experiments/<module>/src/integrationTest/...`). To
run one, move it back to that path, provide its data, adjust the paths it hard-codes, and remove its
`@Disabled`. Expect API drift: they have not been compiled since they were moved.

| Harness | Needs |
| --- | --- |
| `service/.../FeatureRevisionLocationTest` | PhD case-study variant histories (Curl, Marlin, LibSSH, SQLite, Bison, Irssi) |
| `service/.../RuntimeTest` | dynamic feature location data (BTrace instrumentation output) |
| `service/.../ServiceTest`, `ServiceTest2` | local repositories and input data of their authors |
| `service/.../PaperTest` | the input of the illustrations of the version-control paper |
| `service/.../BugzillaTest` | a Bugzilla variant checkout and Selenium test output |
| `service/.../FileLockTest` | a real multi-process file-lock setup (hard-coded Windows paths) |
| `adapter/cpp/.../AdapterTest` | PhD case-study data (Marlin and others) |
| `adapter/cpp/.../FeatureRevisionLocationTest` | SPLC 2020 case-study data (SQLite, Marlin, LibSSH) |
| `adapter/challenge/.../ChallengeTest` | the ArgoUML SPLC challenge benchmark |
| `adapter/runtime/.../RuntimeModuleTest` (with `FileUtils`, `MetricsCalculation`, `data/`) | the ArgoUML SPL benchmark |
| `adapter/java/.../AdapterTest` | a local input directory of its author |
| `adapter/typescript/.../AdapterTest` (with `resources/data/`) | `node_modules/typescript` in the working directory |
| `gui/.../GuiTest` | a headless UI test harness (it starts the GUI, which blocks) |
| `adapter/lilypond/.../thesis/PerformanceAndCorrectnessTest` (with `JavaFxLauncher`, `DieuFeatureCode`, `ConfigurationCodeBuilder`) | the student-thesis benchmark corpus; ignored at class level with TestNG's `@Ignore`, so it was not even reported as skipped |
