# DesignSpace Plugin

This plugin adds support for DesignSpace operation histories.
It assumes that it doesn't operate on files but reads DesignSpace Workspaces and writes to an operation map instead. 
Since this plugin depends on the DesignSpace Java SDK to be available in a local Maven repository, it is not appended to the main Gradle build file.
The SDK is a private project, therefore, automated build pipelines would fail due to the SDK being missing.

If the SDK is locally available, the following lines can be added to the root settings.gradle.
```
include 'adapter-designspace'
project(':adapter-designspace').projectDir = file('adapter/designspace')
```
