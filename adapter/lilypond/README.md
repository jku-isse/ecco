
# ECCO Lilypond Adapter

Artifact adapter plugin for ECCO that provides reader, writer, code viewer and a viewer for compiled (images) [LilyPond](https://lilypond.org) files.

For file parsing the adapter uses the [Parce](https://parce.info)-python module. Install python version >= 3.x and the parce module.
```
pip install parce
```
To use the image viewer (in the graphical interface) one has to set up the path to the lilypond-executable in the `resources\lilypond-config.properties` file.
To turn off the usage, set `use_lilypond=false` in the `gradle.properties` file.