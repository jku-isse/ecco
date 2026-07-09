# LilyPond Metadata Fields Example

Minimal fixture reproducing a real checkout-correctness bug found via a user's real repository
(originally "nachtwache"): `v1_meta` commits 5 quoted-string metadata fields; `v2_meta_header` adds
4 more, inserted in the *middle* of the existing fields (matching a real edit - adding title/composer/
subtitle/poet between an existing duration field and the rest).

Committing `v1_meta` then, after closing and reopening the repository (not needed in the same
session), committing `v2_meta_header`, then checking out `"meta.1, header.1"` produces a
non-compilable file: one field's quoted value gets appended onto a *different* field's value
(`piecePoet = "Friedrich Ruckert""duration:90"` in the known-bad case) instead of staying on its own
line. See `PartialOrderGraphMetaFieldsReloadRegressionTest` and
`memory/pog-align-duplicate-candidate-ambiguity` for the investigation.
