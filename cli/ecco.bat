@ECHO OFF

set CLASSPATH=.
set CLASSPATH=%CLASSPATH%;%0\..\*;

%JAVA_HOME%\bin\java at.jku.isse.ecco.cli.Main %*
