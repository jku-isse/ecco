# TYPESCRIPT VARIANTS

These are some different examples of TypeScript variants. You can check them in by 
sequentially committing them to the ECCO repository. Each example is split into a series of directories that contain
a `.config` file that is used to define the features of the commit.

## shapes
A simple example of a TypeScript variant that defines a `Shape` class and three subclasses `Square` `Circle` and `Rectangle`.
The classes are checked in individually as variants. You may then mix and match the features by creating a new variant that 
can include all three shapes or just a subset of them.

## shapes2
Similar to the `shapes` example, but with a different implementation that splits the classes into separate files. There is
also a `App` class that uses the `Shape` classes. The `App` class also has a property that
demonstrates the ability of the reader to resolve a switch statement based on the features of the variants.

## AoC1
A simple implementation of the first day of the Advent of Code 2023. First the input is checked in, then the basic file input
and then the solutions to part 1 and 2. You may then check out both features combined into one and execute the script in
TypeScript runtime. The example is set up to use `deno` as the runtime.
