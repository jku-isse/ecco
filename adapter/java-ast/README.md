
# ECCO Java AST Adapter

Based on [JavaParser](https://github.com/javaparser/javaparser).

Decomposes Java source at AST granularity (package/type/field/method/statement,
including if/switch/try) rather than the block/line granularity of `adapter/java`.
Enabled by default and mapped to `*.java` ahead of the text adapter, for Java up to 21 (including
pattern-matching `switch` and record patterns). Checked-out files keep their comments, also those
inside lambdas, expressions, call arguments and parameters, if conditions, field initializers, method
headers and between case labels (of 12,238 comments in ECCO's own Java files, 5 are lost: inside
expressions, where JavaParser's printer drops them), but are laid out by JavaParser's printer, not as
originally written.
