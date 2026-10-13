
# ECCO Image Adapter

Artifact adapter plugin for ECCO that provides reader, writer and viewer for images.

Every pixel is an artifact, so a feature is traced to the pixels it changes. That costs time, memory and space with every commit of an image, in proportion to its pixels: one 4-megapixel PNG took 230 s and 9 GB of memory to commit and 1 GB in the repository, against 0.6 s and 11.6 MB with the file adapter. Images that features do not change are better kept whole: see [Images](../../README.md#images) for the `.ecco/.adapters` lines that map them to the file adapter.

The writer writes the pixels again: PNG and BMP come back with the same colours, JPEG lossy, not the same bytes.
