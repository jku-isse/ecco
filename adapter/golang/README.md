# Golang Plugin

This plugin adds support for the Golang language.
Currently, it has only a very simple algorithm to merge features together, which duplicates lines with conflicts and
replaces the conflicting tokens.
For example, consider the two variants:

Variant `xml.1`

```go
package main

import "fmt"

func main() {
    fmt.Println("xml");
}
```

Variant `csv.1`

```go
package main

import "fmt"

func main() {
    fmt.Println("csv");
}
```

When the configuration `xml.1,csv.1` is checked out, the tokens `"xml"` and `"csv"` are in conflict, because they occupy the same line and column, but differ in content.
Therefore, the line `fmt.Println()` is duplicated and the `xml` is inserted in the first line, while `csv` is inserted
in the second one.

```go
package main

import "fmt"

func main() {
    fmt.Println("xml");
    fmt.Println("csv");
}
```

This algorithm currently works for simple statements, but fails with more complicated ones such as function
declarations.
Consider the following two variants.

Variant `sum.1`

```
package main

import "fmt"

func main() {
}

func calc(a, b int) {
    return a + b;
}
```

Variant `mul.1`

```
package main

import "fmt"

func main() {
}

func calc(x, y int) {
    return x * y;
}
```

If the variant `sum.1,mul.1` is checked out, the result is as follows.

```
package main

import "fmt"

func main() {
}

func calc(a, b int) {
func calc(x, y int) {
    return a + b;
    return x * y;
}
```

## Build

Build once using Gradle so ANTLR generates the Go-Lexer and -Parser. 