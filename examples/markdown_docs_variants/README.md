# Markdown Docs Variants

An evolution history of `docs.md` - fictional documentation for a small CLI tool ("Glean") - across
twelve directories that each contain a `.config` file defining the features present in that commit.
Check them in by sequentially committing them to an ECCO repository (in variant order, `V01` through
`V12`), then check out any combination of features. Exercises every block type
`adapter-markdown` supports: nested headings/sections, bullet and ordered lists, fenced code blocks,
blockquotes, and a GFM table with feature-conditional rows.

## Document

Every variant declares a `# Glean` title, an intro paragraph, and an `## Installation` section with a
base install script - the `CORE` content, present in every variant (no separate `CORE` feature token;
it's just what's left once every optional feature is turned off, same as `V01`). Nine optional features
each add a block of their own:

* **WINDOWS** / **DOCKER** each add a `### Windows` / `### Docker` subsection *nested inside*
  Installation, with their own install command.
* **API** adds an `## API Reference` section with a GFM table header; **API_SEARCH** and
  **API_EXPORT** each add one row to that table (needs `API` to have anywhere to add a row to).
* **ENTERPRISE** adds an `## Enterprise Features` section: a blockquote callout plus a nested bullet
  list.
* **CHANGELOG** adds an `## Changelog` ordered list.
* **FAQ** adds an `## FAQ` section with its own nested `###` sub-questions.
* **TROUBLESHOOTING** adds a section with a fenced (unlabeled) code block showing sample error output
  plus a blockquote tip.

## Variants

V01-V10 build up cumulatively towards a full "Enterprise Edition"; V11-V12 are two differently-curated
editions (not supersets of one another) showing the same feature set can compose in more than one
direction.

| Variant | `.config`                                                                          | Edition                                 |
|---------|-------------------------------------------------------------------------------------|------------------------------------------|
| `V01`   | *(empty)*                                                                            | bare minimum docs                        |
| `V02`   | `WINDOWS.1`                                                                          | + Windows install                        |
| `V03`   | `DOCKER.1`                                                                           | + Docker install                         |
| `V04`   | `WINDOWS.1, DOCKER.1`                                                                | + Windows + Docker install               |
| `V05`   | `WINDOWS.1, DOCKER.1, API.1, API_SEARCH.1`                                           | + API reference (search only)            |
| `V06`   | `WINDOWS.1, DOCKER.1, API.1, API_SEARCH.1, API_EXPORT.1`                             | + API reference (search + export)        |
| `V07`   | `..., FAQ.1`                                                                         | + FAQ                                    |
| `V08`   | `..., FAQ.1, CHANGELOG.1`                                                            | + Changelog                              |
| `V09`   | `..., CHANGELOG.1, TROUBLESHOOTING.1`                                                | + Troubleshooting                        |
| `V10`   | `..., TROUBLESHOOTING.1, ENTERPRISE.1`                                               | **Enterprise Edition** (everything)      |
| `V11`   | `DOCKER.1, TROUBLESHOOTING.1`                                                        | Docker-only minimal edition              |
| `V12`   | `WINDOWS.1, API.1, API_SEARCH.1`                                                     | Windows + basic API edition (no Docker, no export) |

Every variant's `docs.md` round-trips byte-exact through `adapter-markdown`'s reader/writer (verified
directly against the real `MarkdownReader`/`MarkdownFileWriter` classes while building this example).
After committing all twelve, checking out any of the configuration strings above reproduces the
corresponding variant's `docs.md` exactly - including checking out combinations that were never
committed as their own variant (e.g. `DOCKER.1, API.1, API_SEARCH.1, ENTERPRISE.1`), which is the
actual point of composing at the section/row level instead of the whole-file level.
