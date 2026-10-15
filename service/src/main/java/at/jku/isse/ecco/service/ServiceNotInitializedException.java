package at.jku.isse.ecco.service;

import at.jku.isse.ecco.EccoException;

/**
 * No repository is open (any more). A front end's background work that finds the repository closed
 * meanwhile - a refresh after Close - can tell this from a real failure and end quietly.
 */
public class ServiceNotInitializedException extends EccoException {

	private static final long serialVersionUID = 1L;

	public ServiceNotInitializedException() {
		super("Service is not initialized. No repository is loaded.");
	}
}
