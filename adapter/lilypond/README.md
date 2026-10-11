
# ECCO Lilypond Adapter

Artifact adapter plugin for ECCO that provides reader, writer, code viewer and a viewer for compiled (images) [LilyPond](https://lilypond.org) files.

For file parsing the adapter uses lymodel's tree-sitter parse of LilyPond (the lilypond-idea-plugin's `python/`
directory, whose grammar is the plugin's own). Install python 3 with tree-sitter, py4j and lymodel:
```
pip install tree-sitter py4j
pip install <lilypond-idea-plugin>/python
```
A repository created before ECCO read LilyPond this way (one whose `.ecco/.settings` has no
`lilypond.parser=lybar-1`) was read with parce: its files still check out, but new commits are refused.
To use the image viewer (in the graphical interface) one has to set up the path to the lilypond-executable in the `resources\lilypond-config.properties` file.
To turn off the usage, set `use_lilypond=false` in the `gradle.properties` file.