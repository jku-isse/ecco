
# Artifact Adapters

This folder contains the artifact adapter projects.

## How to add a new ECCO plugin

First, create a new project folder for your plugin.
This folder's name should represent what the plugin supports.
I.e., the folder containing the plugin that supports Golang source files is simply called "golang".
When you create a new plugin project, make sure to enable Gradle as it's build system.
This step is important if the project is created with an IDE such as IntelliJ.

Create a package `at.jku.isse.ecco.adapter.<your plugin name>` and a resource folder `META-INF/services`.
In the first package, you want to create four files.
A `-Reader` file, that handles parsing whatever artefact types your plugin supports and building an ECCO tree.
A `-Writer` file, that handles parsing an ECCO tree and reconstruction of the previously read artefacts.
A `-Module` file, that registers the files above with the Guice dependency injection framework.
And finally a `-Plugin` file, that stores metadata about your plugin.
Those files should all have a fitting prefix, mentioning what it is that you are supporting.
For example, the plugin supporting Golang artefacts contains the files `GoReader`, `GoWriter`, `GoModule`, and `GoPlugin`.

In the resource folder, create a file named `at.jku.isse.ecco.adapter.ArtifactPlugin`.
The content of this file must be the fully qualified class name of your `-Plugin` file.
For example, the Golang plugin's ArtifactPlugin-file contains the string `at.jku.isse.ecco.adapter.golang.GoPlugin`.

After this step, the folder structure should look similar to the following:
* ``ecco``
  * ``adapter``
    * ``<your plugins project folder>``
      * ``src``
        * ``main``
          * ``java``
            * ``at.jku.isse.ecco.adapter.<your plugin name>``
              * ``<Name>Reader``
              * ``<Name>Writer``
              * ``<Name>Module``
              * ``<Name>Plugin``
          * ``resources``
            * ``META-INF.services``
              * ``at.jku.isse.ecco.adapter.ArtifactPlugin``
      * ``build.gradle`` 

If your IDE didn't create the build.gradle file yet, now is the time to do so.
Then, insert the following text, replacing any other text that might have been created.
```groovy
plugins {
  id 'java'
}

group = 'at.jku.isse.ecco'
ecco.adapter = true

repositories {
  mavenCentral()
}

dependencies {
  // what an adapter is written against: ArtifactPlugin, ArtifactReader/Writer/Viewer, PluginArtifactData,
  // the listeners and the shared code viewer - not the service itself
  implementation project(':ecco-adapter-api')
  // tests build trees with the service's SerEntityFactory, or open a repository with EccoService
  testImplementation project(':ecco-service')
  testImplementation platform('org.junit:junit-bom:5.9.1')
  testImplementation 'org.junit.jupiter:junit-jupiter'
}

test {
  useJUnitPlatform()
}
```

Now, implement the necessary interfaces and extend the necessary classes.
Make your reader class implement ``ArtifactReader<Path, Set<Node.Op>>`` and your writer class ``ArtifactWriter<Set<Node>, Path>`` and implement the respective methods.
For now, it suffices that they return some default value.
After implementing those interfaces extend the module and plugin classes with ``AbstractModule`` and ``ArtifactPlugin`` respectively.
If your IDE cannot resolve those classes, try rebuilding the project using gradle.

In your plugin class, put the following code, making changes as necessary:
```java
public class <Name>Plugin extends ArtifactPlugin {
    public static final String DESCRIPTION = "Adds support for ... artefacts";

    private final <Name>Module module = new <Name>Module();

    @Override
    public String getPluginId() {
        return <Name>Plugin.class.getName();
    }

    @Override
    public Module getModule() {
        return module;
    }

    @Override
    public String getName() {
        return  <Name>Plugin.class.getSimpleName();
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }
}
```

In your module class, put the following code, making changes as necessary.
This code configures the Guice dependency injection framework such that it is aware of your reader and writer classes.
```java
public class <Name>Module extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();

        final Multibinder<ArtifactReader<Path, Set<Node.Op>>> readerMultibinder =
                Multibinder.newSetBinder(
                        binder(),
                        new TypeLiteral<>() {
                        });

        readerMultibinder.addBinding().to(<Name>Reader.class);

        final Multibinder<ArtifactWriter<Set<Node>, Path>> writerMultibinder =
                Multibinder.newSetBinder(
                        binder(),
                        new TypeLiteral<>() {
                        });

        writerMultibinder.addBinding().to(<Name>Writer.class);
    }
}
```

Finally, for your plugin to be built and picked up by the ``gui`` project, locate the `settings.gradle` file in the root folder of the ECCO project and the `build.gradle` file in the `gui` project's folder.
In the first one, add the lines `include 'adapter-<name>'` and `project(':adapter-<name>').projectDir = file('adapter/<name>')` to the already existing, similarly looking lines.
In the second file, add the line `runtimeOnly project(':ecco-adapter-<name>')` to the dependency-block. 
Again, there should already exist lines that look similar, just append it there.
If you run `gradle run ecco:gui` and open or initialise a new ECCO-repository, your plugin should show up in status view.
If it does not, review this documentation.

If, after reviewing the documentation, your plugin still does not show up, then this documentation is missing something.
In that case, please create a new GitHub-Issue with a link to your branch or fork, so that it can be investigated.

## How to write an adapter outside this repository

An adapter does not have to live in this repository. It is written against `ecco-adapter-api` alone - `ArtifactPlugin`, `ArtifactReader`, `ArtifactWriter`, `ArtifactViewer`, `PluginArtifactData`, the listeners and the shared code viewer - and ECCO finds it on its class path.

ECCO is not published to a Maven repository yet. Build it once and install it into your local Maven repository (`~/.m2`), from the root of this repository:
```
./gradlew publishToMavenLocal
```
This installs every module as `at.jku.isse.ecco:<module>:<version>`, the version being the one in `gradle.properties` (0.2.0 at the time of writing).

Your adapter's `build.gradle`:
```groovy
plugins {
  id 'java'
}

java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}

repositories {
  mavenLocal()    // ECCO, installed with ./gradlew publishToMavenLocal
  mavenCentral()
}

dependencies {
  implementation 'at.jku.isse.ecco:ecco-adapter-api:0.2.0'
  // tests build trees with SerEntityFactory, or commit and check out with EccoService
  testImplementation 'at.jku.isse.ecco:ecco-service:0.2.0'
  testImplementation 'org.junit.jupiter:junit-jupiter:5.10.1'
  testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test {
  useJUnitPlatform()
}
```
`ecco-adapter-api` brings Guice, Guava and `ecco-base` with it. If your adapter has a viewer (an `ArtifactViewer`), it also needs JavaFX - `ecco-adapter-api` compiles against JavaFX but does not pass it on, as only the GUI loads viewers; the [OpenJFX Gradle plugin](https://github.com/openjfx/javafx-gradle-plugin) adds it, as in this repository's adapters.

The classes are the ones described above - a `-Reader`, `-Writer`, `-Module` and `-Plugin` - in a package of your own, and the plugin is registered the same way: a file `src/main/resources/META-INF/services/at.jku.isse.ecco.adapter.ArtifactPlugin` containing the fully qualified name of your `-Plugin` class.

A test builds an ECCO tree with the entity factory ECCO stores repositories with:
```java
EntityFactory entityFactory = new SerEntityFactory();
RootNode.Op root = entityFactory.createRootNode();
Node.Op file = entityFactory.createNode(new PluginArtifactData(MyPlugin.class.getName(), Path.of("example.txt")));
root.addChild(file);
```

To use the adapter, put its jar on ECCO's class path. With ECCO installed by `./gradlew :ecco-cli:installDist` (into `cli/build/install/ecco`), the command line runs as
```
java -cp "cli/build/install/ecco/lib/*:my-adapter.jar" at.jku.isse.ecco.cli.Main adapters
```
which lists your plugin among the others when it was found (`adapters` needs a repository: run it in one, or `init` one first). The GUI, installed by `./gradlew :ecco-gui:installDist`, starts the same way with its main class:
```
java -cp "gui/build/install/ecco-gui/lib/*:my-adapter.jar" at.jku.isse.ecco.gui.Launcher
```
On Windows, separate the class path entries with `;` instead of `:`.
