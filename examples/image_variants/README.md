
# Person Image Example


## Input Variants

We use the following six out of the nine total variants of the person image is input (`commit` operation) to ECCO.

| Variant 1 | Variant 2 | Variant 3 | Variant 4 | Variant 5 | Variant 9 |
|:---------:|:---------:|:---------:|:---------:|:---------:|:---------:|
| ![Variant 1: person](V1_purpleshirt/person.png "Variant 1: person") | ![Variant 2: person](V2_stripedshirt/person.png "Variant 2: person") | ![Variant 3: person](V3_purpleshirt_jacket/person.png "Variant 3: person") | ![Variant 4: person](V4_purpleshirt_jacket_glasses/person.png "Variant 4: person") | ![Variant 5: person](V5_stripedshirt_jacket_glasses/person.png "Variant 5: person") | ![Variant 9: person](V9_stripedshirt_jacket_hat/person.png "Variant 9: person") |
| `person`, `purple_shirt` | `person`, `striped_shirt` | `person`, `purple_shirt`, `jacket` | `person`, `purple_shirt`, `jacket`, `glasses` | `person`, `striped_shirt`, `jacket`, `glasses` | `person`, `striped_shirt`, `jacket`, `hat` |


## Computed Traces

Given these six variants, ECCO automatically computes the following traces.

| Trace 1 | Trace 2 | Trace 3 | Trace 4 | Trace 5 | Trace 6 |
|:-------:|:-------:|:-------:|:-------:|:-------:|:-------:|
| ![Trace 1: person](computed_traces_from_V1_to_V5_and_V9/person.png "Trace 1: person") | ![Trace 2: purple shirt](computed_traces_from_V1_to_V5_and_V9/purpleshirt_nojacket.png "Trace 2: purple shirt") | ![Trace 3: striped shirt](computed_traces_from_V1_to_V5_and_V9/stripedshirt_nojacket.png "Trace 3: striped shirt") | ![Trace 4: jacket](computed_traces_from_V1_to_V5_and_V9/jacket.png "Trace 4: jacket") | ![Trace 5: glasses](computed_traces_from_V1_to_V5_and_V9/glasses.png "Trace 5: glasses") | ![Trace 6: hat](computed_traces_from_V1_to_V5_and_V9/hat.png "Trace 6: hat") |
| `person` | `purple_shirt` | `striped_shirt` | `jacket` | `glasses` | `hat` |


## Composed Variants

Using these traces, ECCO can compose new variants of the person image with a given set of features (`checkout` operation).

| Composed Variant 1 | Composed Variant 2 |
|:---------:|:---------:|
| ![Composed Variant 1: person, purple shirt, glasses](composed_variants/person_purpleshirt_glasses.png "Composed Variant 1: person, purple shirt, glasses") | ![Composed Variant 1: person, purple shirt, glasses](composed_variants/person_purpleshirt_glasses_hat.png "Composed Variant 1: person, purple shirt, glasses") |
| `person`, `purple_shirt`, `glasses` | `person`, `purple_shirt`, `glasses`, `hat` |

