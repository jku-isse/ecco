
# ECCO Java AST Adapter

Based on [JavaParser](https://github.com/javaparser/javaparser).

Decomposes Java source at AST granularity (package/type/field/method/statement,
including if/switch/try) rather than the block/line granularity of `adapter/java`.
Experimental: disabled by default (see `AdapterPreferences.DEFAULT_DISABLED_PLUGIN_IDS`),
no GUI viewer, and the writer does not yet handle every AST shape (falls back to an
`UnsupportedOperationException` on unrecognized statement/expression forms) or preserve
comments.
