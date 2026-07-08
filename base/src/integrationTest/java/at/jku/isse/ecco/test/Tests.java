package at.jku.isse.ecco.test;

import at.jku.isse.ecco.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterizes the exact java.nio.file.PathMatcher glob semantics that
 * {@link at.jku.isse.ecco.adapter.dispatch.DispatchReader}'s ignore-pattern matching (see
 * {@code EccoService#open()}, which registers ignore patterns like ".ecco" as globs) depends on -
 * in particular that a glob without "**" only matches an exact relative path, not any path ending
 * with it.
 */
public class Tests {

	@Test
	public void PathMatcher_matchesExactRelativePathOnly() {
		PathMatcher pm = FileSystems.getDefault().getPathMatcher("glob:testfolder/testfile.txt");

		Path path = Paths.get("testfolder/testfile.txt");
		Path path2 = Paths.get("testfoldera/testfolder/testfile.txt");

		assertTrue(pm.matches(path));
		assertFalse(pm.matches(path2));
	}

	@Test
	public void PathMatcher_doubleWildcardMatchesAnyPath() {
		PathMatcher pm = FileSystems.getDefault().getPathMatcher("glob:**");

		Path path = Paths.get("testfile.txt");
		Path path2 = Paths.get("testfoldera/testfolder2/testfile.txt");

		assertTrue(pm.matches(path));
		assertTrue(pm.matches(path2));
	}


	// todo: update outdated tests
	/*
	@Test
	public void Server_Test() {
		boolean shutdown = false;

		try {

			ServerSocketChannel ssChannel = ServerSocketChannel.open();
			ssChannel.configureBlocking(true);
			ssChannel.socket().bind(new InetSocketAddress(12345));

			while (!shutdown) {
				SocketChannel sChannel = ssChannel.accept();

				ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
				ObjectInputStream ois = new ObjectInputStream(sChannel.socket().getInputStream());

				// determine if it is a push (receive data) or a pull (send data)
				String command = (String) ois.readObject();
				System.out.println("COMMAND: " + command);

				if (command.equals("PULL")) { // if pull, send data
					oos.writeObject("PULL-REPLY");
					oos.close();
				} else if (command.equals("PUSH")) { // if push, receive data
					String push_reply = (String) ois.readObject();
					System.out.println(push_reply);
				}

				sChannel.close();
			}

			ssChannel.close();
		} catch (IOException | ClassNotFoundException e) {
			throw new EccoException("Error starting server.", e);
		}
	}

	@Test
	public void Client_Test() throws MalformedURLException, URISyntaxException {
		URI uri = new URI("ecco://localhost");
		System.out.println("URI: " + uri.getQuery());
		URL url = new URL("http://localhost");
		System.out.println("URL: " + url.getPath());
		try {
			{
				SocketChannel sChannel = SocketChannel.open();
				sChannel.configureBlocking(true);
				// PUSH
				if (sChannel.connect(new InetSocketAddress("localhost", 12345))) {

					ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
					ObjectInputStream ois = new ObjectInputStream(sChannel.socket().getInputStream());

					// send command
					oos.writeObject("PUSH");
					oos.writeObject("PUSH-REPLY");
				}
				sChannel.close();
			}

			{
				SocketChannel sChannel = SocketChannel.open();
				sChannel.configureBlocking(true);
				// PULL
				if (sChannel.connect(new InetSocketAddress("localhost", 12345))) {

					ObjectOutputStream oos = new ObjectOutputStream(sChannel.socket().getOutputStream());
					ObjectInputStream ois = new ObjectInputStream(sChannel.socket().getInputStream());

					// send command
					oos.writeObject("PULL");

					String pull_reply = (String) ois.readObject();
					System.out.println(pull_reply);
				}
				sChannel.close();
			}
		} catch (IOException | ClassNotFoundException e) {
//			throw new EccoException("Error starting client.", e);
		}
	}
	*/
}
