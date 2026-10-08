# LilyPond variants that differ only in spelling

Each directory holds the same piece as `../lilypond_variants/v2_setup_notes` plus the
dynamics of `v6` (`setup.1, notes.1, dynamics.1`), written four ways:

| directory | spelled differently |
|---|---|
| `dynamics` | not at all (ECCO's own checkout of that configuration) |
| `dynamics_indent` | the voices' lines indented by four spaces instead of two |
| `dynamics_durations` | repeated durations left implicit (`gis8 gis cis`) |
| `dynamics_relative` | the soprano's `\relative` anchored an octave higher |

and `lyrics_dynamics` (`setup.1, notes.1, lyrics.1, dynamics.1`) with its copy
`lyrics_dynamics_rewrapped`, each lyric block on one line instead of two.

`lymusic` (lilypond-idea-plugin, `python/verify/lymusic.py`) confirms each holds the same
music as `dynamics`. `LilypondRespelledVariantsTest` commits `v1`-`v3` and one of these,
checks out `setup.1, notes.1, articulation.1, dynamics.1` - which composes the
articulation committed in one spelling with the dynamics committed in another - and
checks the result's music aspect by aspect, and counts the tokens traced to `dynamics.1`:
any beyond the dynamics themselves are a spelling the adapter took for content.

| tokens traced to `dynamics.1` | before | line breaks | + musical tokens |
|---|---|---|---|
| `dynamics` | 30 | 24 | 24 |
| `dynamics_indent` | 86 | 24 | 24 |
| `dynamics_durations` | 40 | 34 | 24 |
| `dynamics_relative` | 33 | 27 | 24 |
| `lyrics_dynamics` (after `v1`-`v4`) | - | 24 | 24 |
| `lyrics_dynamics_rewrapped` | - | 32 | 28 (one line break fewer per block) |

"Line breaks" compare without their indentation (always). "Musical tokens"
(`-Decco.lilypond.musicalTokens=true`, lybar through `LYPYTHON`) compare each note by its
absolute pitch and duration and keep lyric syllables apart. "-": not measured.
