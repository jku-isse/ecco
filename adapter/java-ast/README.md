
# ECCO Java AST Adapter

Based on [JavaParser](https://github.com/javaparser/javaparser).

Decomposes Java source at AST granularity (package/type/field/method/statement,
including if/switch/try) rather than the block/line granularity of `adapter/java`.
Enabled by default and mapped to `*.java` ahead of the text adapter. Checked-out files keep
every comment but are laid out by JavaParser's printer, not as originally written. Java 21
pattern-matching `switch` and record patterns are not supported yet and fail loudly.
