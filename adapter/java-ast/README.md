
# ECCO Java AST Adapter

Based on [JavaParser](https://github.com/javaparser/javaparser).

Decomposes Java source at AST granularity (package/type/field/method/statement,
including if/switch/try) rather than the block/line granularity of `adapter/java`.
Enabled by default and mapped to `*.java` ahead of the text adapter, for Java up to 21 (including
pattern-matching `switch` and record patterns). Checked-out files keep their comments, also those
inside lambdas, expressions, call arguments and parameters, if conditions, field initializers, method
headers and between case labels (none of the 12,288 comments in ECCO's own Java files is lost), but are
laid out by JavaParser's printer, not as originally written - a comment behind code may come before it.
