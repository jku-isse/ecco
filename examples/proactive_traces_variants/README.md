# Proactive Traces Variants

A tiny C program (a greeter) in two variants, the second with a **proactive feature trace**: a
VEVOS presence-condition file (`pcs.variant.csv`) that says which lines belong to which feature.
Check them in by sequentially committing them to an ECCO repository (`V1_base`, then
`V2_base_log_verbose`), then check out the configurations below.

## Program

Every variant prints a greeting from `main` - the `BASE` feature. Two optional features:

* **LOG** adds `log_message` and a call to it at the start of `main`.
* **VERBOSE** is a build flag: it has no code of its own in `main.c`.

| Variant                | `.config`                  | Features present           |
|------------------------|----------------------------|----------------------------|
| `V1_base`              | `BASE.1`                   | base only                  |
| `V2_base_log_verbose`  | `BASE.1, LOG.1, VERBOSE.1` | base + logging + verbose   |

## What the trace is for

LOG was only ever committed together with VERBOSE, so the history alone cannot tell whether the
log code belongs to LOG or to VERBOSE: ECCO's (retroactive) condition for it is "LOG or VERBOSE".
The trace in `V2_base_log_verbose/pcs.variant.csv` marks the function `log_message` (lines 3-5) as
`LOG` - but not the call to it in `main` (line 8): traces written by hand are often incomplete.

With the default evaluation strategy (`PROACTIVE`), a line's trace decides where it has one, its
retroactive condition where not. **Boosting** (the default main-tree strategy, `BOOST`) gives
every line of an association the condition its traces agree on - here the call gets `LOG` from
the function it calls:

| Checkout                   | with boosting (`BOOST`)          | without (`NO-BOOST`)                                         |
|----------------------------|----------------------------------|--------------------------------------------------------------|
| `BASE.1`                   | `V1_base`                        | `V1_base`                                                    |
| `BASE.1, LOG.1, VERBOSE.1` | `V2_base_log_verbose`            | `V2_base_log_verbose`                                        |
| `BASE.1, LOG.1`            | `V2_base_log_verbose`            | `V2_base_log_verbose`                                        |
| `BASE.1, VERBOSE.1`        | `V1_base` (VERBOSE has no code)  | the call to `log_message` without the function - does not compile |

Both warn that the history is ambiguous here (MISSING: VERBOSE (without LOG)): committing a
variant with VERBOSE but without LOG would settle it without a trace.

Boosting copies the condition only where an association's traces agree; it also reaches lines
of the association a trace was not meant for (here also `pcs.variant.csv` itself, which is
committed like any file unless listed in `.ecco/.ignores`). `ProactiveTracesExampleTest` (service)
commits this example and checks the table.
