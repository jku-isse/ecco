# Draw Product Line 2 (DPL2) Example

A modernized companion to the [Draw Product Line (DPL)](../dpl_variants) example, built specifically
to exercise `adapter/java-ast`'s support for post-Java-11 language features (the ones
`adapter/java`, the older block/line-granularity Java adapter, never has to deal with, since it
doesn't decompose down to statement/expression level).

Same overall shape as DPL: a small drawing-shapes product line, incrementally adding features across
five self-contained variants, each with its own `.config` (comma-separated `FEATURE.VERSION`
tokens). Unlike the original DPL, everything lives in a real `dpl2` package with a `module-info.java`
(the default/unnamed package a real module can't export), and there's no Swing UI - each variant is
a plain `Main.main()` you can `javac --release 21` and run directly.

| Variant | Adds | Language features exercised |
|---|---|---|
| V1 | `CIRCLE` | records, a record compact constructor (validation), sealed interface + `permits`, pattern-matching `instanceof`, a text block, a switch expression (arrow syntax), `module-info.java` |
| V2 | `RECTANGLE` | a second sealed permitted type |
| V3 | `TRIANGLE` | a third sealed permitted type, `var`, enhanced-for |
| V4 | `COLOR` | an enum, a record wrapping another record (`ColoredShape`) |
| V5 | `PATTERNSWITCH` | Java 21 pattern-matching `switch` over the sealed `Shape` type - **known to currently fail on import**, see below |

V1-V4 round-trip cleanly through `adapter/java-ast` (verified against the real reader/writer, not
just `javac`). V5 is a deliberate boundary case: `JavaParser` 3.25.8 (this adapter's dependency) has
no grammar support for pattern-matching `switch` even at its highest non-preview language level
(`JAVA_18`), so committing/importing V5 fails loudly with a `ParseProblemException` pointing at
`ShapeDescriber.describeViaPatternSwitch()` - see `adapter/java-ast`'s
`JavaASTLanguageLevelTest.patternMatchingSwitchFailsLoudlyInsteadOfSilentlyTruncatingTheFile()`. Kept
in so this example can also be used to confirm that failure is still loud (not silent data loss)
rather than removing the only case that currently doesn't work.
