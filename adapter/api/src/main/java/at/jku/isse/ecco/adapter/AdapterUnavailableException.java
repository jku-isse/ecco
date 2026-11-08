package at.jku.isse.ecco.adapter;

import at.jku.isse.ecco.EccoException;

/**
 * An adapter cannot read any file here, whatever the file: it needs a Python it does not find, or
 * this repository was created for another version of it. Unlike a file it cannot read, which the
 * dispatcher reads by another adapter, this fails the commit - another adapter would store the
 * files otherwise than their history and every later commit.
 */
public class AdapterUnavailableException extends EccoException {

	private static final long serialVersionUID = 1L;

	public AdapterUnavailableException(String message) {
		super(message);
	}

	public AdapterUnavailableException(String message, Exception cause) {
		super(message, cause);
	}
}
