package at.jku.isse.ecco.web;

import org.glassfish.grizzly.http.server.CLStaticHttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

public class EccoWebServer {

	// Base URI the Grizzly HTTP server will listen on
	public static final String BASE_URI;
	public static final String protocol;
	public static final Optional<String> host;
	public static final String path;
	public static final Optional<String> port;

	static {
		protocol = "http://";
		host = Optional.ofNullable(System.getenv("HOSTNAME"));
		port = Optional.ofNullable(System.getenv("PORT"));
		path = "myapp";
		BASE_URI = protocol + host.orElse("localhost") + ":" + port.orElse("8080") + "/" + path + "/";
	}

	/**
	 * Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
	 *
	 * @return Grizzly HTTP server.
	 */
	public static HttpServer startServer() throws IOException {
		// create a resource config that scans for JAX-RS resources and providers in at.jku.isse.ecco.web package
		final ResourceConfig rc = new ResourceConfig().packages("at.jku.isse.ecco.web");

		// create and start a new instance of grizzly http server exposing the Jersey application at BASE_URI
		HttpServer httpServer = GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), rc);

		//httpServer.getServerConfiguration().addHttpHandler(new CLStaticHttpHandler(new URLClassLoader(new URL[]{new URL("file:///home/username/staticfiles.jar")})), "/www");

		//Main.class.getClassLoader().getResource("templates/static").getPath()

		CLStaticHttpHandler staticHttpHandler = new CLStaticHttpHandler(EccoWebServer.class.getClassLoader(), "/www/");
		httpServer.getServerConfiguration().addHttpHandler(staticHttpHandler);

		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				httpServer.shutdownNow();
			}
		}));

		httpServer.start();

		return httpServer;
	}

	/**
	 * Main method.
	 *
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException, InterruptedException {
		final HttpServer server = startServer();
		System.out.println(String.format("Jersey app started with WADL available at %sapplication.wadl\nHit enter to stop it...", BASE_URI));

		Thread.currentThread().join();

//		System.in.read();
//		server.stop();
	}

}
