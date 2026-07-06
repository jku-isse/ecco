package at.jku.isse.ecco.gui;

/**
 * A class that does not extend {@link javafx.application.Application} is required as the jar's
 * main class so that packaged (jpackage) app images can launch it via the plain classpath; a
 * main class extending Application directly only works when launched via the module path.
 */
public class Launcher {

	public static void main(String[] args) {
		EccoGui.main(args);
	}

}
