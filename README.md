
# ECCO

Get the code: `git clone` or download zip file into `<working_dir>`.

Requires:
* JDK 8
* [Gradle](http://gradle.org/ "Gradle") as build system.

Run `gradle tasks` in `<working_dir>` for a list of tasks to execute.

Examples:
* `gradle eclipse` builds eclipse projects.
* `gradle build` builds the projects.
* `gradle packageLinux` or `gradle packageWindows` creates binary distributions as zip file packages for every project. In case of projects that can be run (like CLI or GUI) the contents can be extracted and run.


## IDEs

### IntelliJ

IntelliJ supports Gradle out of the box. Just import the project as a Gradle project.

### Eclipse

Eclipse does not support Gradle by default. There is a Gradle plugin for Eclipse, but I do not recommend it! Instead follow these steps:

Create Eclipse projects from Gradle projects: `gradle eclipse`. This creates the `.project` and `.classpath` files Eclipse needs for every project.

Import the created Eclipse projects into your Eclipse workspace: `File > Import > General > Existing Projects Into Workspace`.
* Select root directory: `<working_dir>`
* Search for nested projects.
* Do *not* copy projects into workspace.

Select all shown projects and import them.


Disable errors for cyclic dependencies in Eclipse: `Window > Preferences > Java > Compiler > Building > Build path problems > Circular dependencies > Warning`.

