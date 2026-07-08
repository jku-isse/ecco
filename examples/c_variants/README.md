# C Variants

An evolution history of a small C program (a temperature converter), split into four directories
that each contain a `.config` file defining the features present in that commit. Check them in by
sequentially committing them to an ECCO repository, in order (`V1_core`, `V2_core_kelvin`,
`V3_core_logging`, `V4_core_kelvin_logging`), then check out any combination of features.

## Program

`main.c` always contains a `celsiusToFahrenheit` function and a `main` that uses it - the `CORE`
feature, present in every variant.

* **KELVIN** adds a `celsiusToKelvin` function and prints the Kelvin conversion in `main`.
* **LOGGING** adds a `logConversion` function, called at the start of `main`.

The four variants cover every combination of the two optional features:

| Variant                    | `.config`                    | Features present    |
|-----------------------------|-------------------------------|----------------------|
| `V1_core`                   | `CORE.1`                      | core only            |
| `V2_core_kelvin`             | `CORE.1, KELVIN.1`             | core + Kelvin        |
| `V3_core_logging`            | `CORE.1, LOGGING.1`            | core + logging       |
| `V4_core_kelvin_logging`     | `CORE.1, KELVIN.1, LOGGING.1`   | core + Kelvin + logging |

After committing all four, checking out `CORE.1, KELVIN.1` or `CORE.1, LOGGING.1` reproduces
`V2_core_kelvin`/`V3_core_logging` exactly; checking out `CORE.1` alone reproduces `V1_core`.
