# Notes inserted into the middle of a voice

`notes_edit` is `examples/lilypond_variants/v2_setup_notes` with notes inserted mid-bar in three
voices, each bar keeping its length - places surrounded by identical tokens, where an
alignment of one voice's tokens could slip:

| voice | before | after |
|---|---|---|
| soprano | `fis8 fis8` | `fis16 fis16 fis8` |
| tenor | `h8` | `h16 h16` |
| bass | `cis4` | `cis8 cis16 cis16` |

`notes_articulation_edit` is `v3_setup_notes_articulation` with the same edits - what checking
out `setup.1, notes.1, articulation.1, edit.1` must give once `notes_edit` is committed as
`setup.1, notes.1, edit.1` after `v1`-`v3`. `LilypondEditedVariantsTest` checks that by its music
(lymusic), and, with musical tokens, that exactly the 10 edited tokens are traced to `edit.1`
(7 inserted, 3 they replace; plain tokens trace 14).

`notes_leap` is `v2_setup_notes` with a leap in the soprano: `gis8 cis8 fis,8` where `v2` has
`gis8 gis8 fis8`. The `fis` is the same note in both, so with musical tokens it is one token,
and ECCO keeps one spelling of it: committed after `v2`, the leap variant came back as
`gis8 cis8 fis8`, an octave high. The writer spells each note again against the note written
before it; `LilypondEditedVariantsTest` checks the leap variant's music after `v2`.

`notes_moved` is `v2_setup_notes` with the alto's definition moved before the soprano's - the same
music. parce puts every voice's notes in the same context, `LilyPond.musiclist`, so ECCO matched the
voices by their order: it stored `AltoVoice =` with soprano notes, traced 129 tokens to the move, and
a checkout of `setup.1, notes.1, articulation.1, order.1` put the alto's and the soprano's accents on
other notes. With variable contexts (each top-level assignment a context of its name, the default for
a new repository) the move is the soprano taken out and put back: 73 tokens, the soprano at its new
place and at its old one. `notes_articulation_moved` is what that checkout gives - every accent on its
note, except the soprano's, which was committed on the soprano at its old place.
`LilypondMovedVoicesTest` checks both.
