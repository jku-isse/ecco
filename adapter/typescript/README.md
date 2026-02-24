# ECCO TypeScript Adapter

## Overview
This ECCO adapter is able to parse a select subset of TypeScript language features.

It uses the JavaScript engine Javet to run the Microsoft TypeScript compiler in a Node
environment. This generates an AST in a JSON format which is exported to Java as a collection of Maps and ArrayLists.
An Ecco tree is then built from that AST information.

## Usage 

Since parsing relies on the TypeScript compiler this needs to be installed.
For this a Node.js and npm installation is needed. 

``
typescript/src/main/resources/script
``
includes a package.json that only defines a dependency for the npm typescript package 
which is the Microsoft TypeScript compiler. Either run
``
npm install
`` in that directory or run the gradle
``
npmInstall
``
task.

## Features

The TypeScript reader can resolve the following language features:

- While loops
- Do While loops
- For loops
- For of loops
- For in loops
- Class Declarations
- Method Declarations
- Enums
- Switch Statements
- If Statements
- Functions
- Arrow functions assigned to variables
- Arrow functions as properties of objects

Any other statement is treated atomically and ends up as a leaf in the tree.

## Details

The TypeScriptParser uses a short JavaScript to invoke the TypeScript compiler. This script also maps the kinds of the 
visited node from an integer to a string. That "kind" information string is then used in the Java part to distinguish 
between the various language constructs. We need to do this because the AST data is structured differently for each kind.
The text containing relevant semantic information is used as a basis for the hash code of the nodes. White space and comments
are not included in the hash code but stored as separate information. The writer simply pastes the leading trivia, semantic
information and trailing trivia together to form the original source code for each node in the ECCO tree.

## Limitations
- The Reader is depended on the TypeScript compiler producing an AST. If the compiler fails to parse the input file the reader will fail as well.
- White space and comments follow the logic of the TypeScript compiler and are associated with the node directly after it.
If features are added or removed this may interfere with the formatting of the document for that reason.
- Features that are hidden in some lambda that that is passed to a function won't be resolved.
- Newer versions of the typescript compiler might need adjustments in the parsing of the kinds.