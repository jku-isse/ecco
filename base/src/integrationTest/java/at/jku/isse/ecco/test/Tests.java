package at.jku.isse.ecco.test;

import at.jku.isse.ecco.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

public class Tests {

	@Test
	public void UUID_Test() {
		UUID id = UUID.randomUUID();
		System.out.println("ID: " + id.toString());
		System.out.println("HASH: " + id.hashCode());
	}

	@Test
	public void PathMatcher_Test() throws IOException {
		PathMatcher pm = FileSystems.getDefault().getPathMatcher("glob:testfolder/testfile.txt");

		Path path = Paths.get("testfolder/testfile.txt");
		Path path2 = Paths.get("testfoldera/testfolder/testfile.txt");

		System.out.println(path + ": " + pm.matches(path));
		System.out.println(path2 + ": " + pm.matches(path2));
	}

	@Test
	public void PathMatcher_Test2() throws IOException {
		PathMatcher pm = FileSystems.getDefault().getPathMatcher("glob:**");

		Path path = Paths.get("testfile.txt");
		Path path2 = Paths.get("testfoldera/testfolder2/testfile.txt");

		System.out.println(path + ": " + pm.matches(path));
		System.out.println(path2 + ": " + pm.matches(path2));
	}


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


}
