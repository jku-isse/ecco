# C Variants

An evolution history of a small C program (a temperature converter), split across three files
(`converter.h`, `converter.c`, `main.c`) and eight directories that each contain a `.config` file
defining the features present in that commit. Check them in by sequentially committing them to an
ECCO repository, in variant order (`V1_core` through `V8_core_kelvin_logging_validation`), then
check out any combination of features.

## Program

Every variant declares `celsiusToFahrenheit` in `converter.h`, implements it in `converter.c`, and
calls it from `main` in `main.c` - the `CORE` feature, present in every variant. Three optional
features each touch all three files:

* **KELVIN** adds `celsiusToKelvin` (declaration, implementation, and a call in `main` that prints
  the Kelvin conversion).
* **LOGGING** adds `logConversion` (declaration, implementation, and a call at the start of `main`).
* **VALIDATION** adds `isValidTemperature` (declaration, implementation, and a check at the start
  of `main` that rejects temperatures below absolute zero).

The eight variants cover every combination of the three optional features:

| Variant                                   | `.config`                                    | Features present                |
|---------------------------------------------|-------------------------------------------------|----------------------------------|
| `V1_core`                                   | `CORE.1`                                         | core only                        |
| `V2_core_kelvin`                             | `CORE.1, KELVIN.1`                                | core + Kelvin                    |
| `V3_core_logging`                            | `CORE.1, LOGGING.1`                               | core + logging                   |
| `V4_core_validation`                         | `CORE.1, VALIDATION.1`                            | core + validation                |
| `V5_core_kelvin_logging`                     | `CORE.1, KELVIN.1, LOGGING.1`                      | core + Kelvin + logging          |
| `V6_core_kelvin_validation`                  | `CORE.1, KELVIN.1, VALIDATION.1`                   | core + Kelvin + validation       |
| `V7_core_logging_validation`                 | `CORE.1, LOGGING.1, VALIDATION.1`                  | core + logging + validation      |
| `V8_core_kelvin_logging_validation`          | `CORE.1, KELVIN.1, LOGGING.1, VALIDATION.1`         | core + Kelvin + logging + validation |

Every variant directory's code compiles cleanly (`gcc -Wall -Wextra -std=c99`) and produces the
expected output. After committing all eight, checking out any of the eight configuration strings
above reproduces the corresponding variant's `converter.h`, `converter.c`, and `main.c` exactly.
