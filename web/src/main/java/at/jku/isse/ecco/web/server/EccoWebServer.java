package at.jku.isse.ecco.web.server;

import at.jku.isse.ecco.web.rest.EccoApplication;
import org.glassfish.grizzly.http.server.CLStaticHttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

public class EccoWebServer {

	// Base URI the Grizzly HTTP server will listen on
	private static final URI BASE_URI = URI.create("http://localhost:8080/rest/api");

	public static void main(String[] args) throws IOException, InterruptedException {
		// TODO: cli parameters for path to repo and port (e.g. "ecco-web --port=8080 --repo=/home/user/repo" or "ecco-web -p 8080 -r /home/user/repo")
		String repositoryDir = "/media/marc/UbuntuData/EccoRepoTest/.ecco";

		// create a resource config that scans for JAX-RS resources and providers in at.jku.isse.ecco.web package
		final EccoApplication eccoApplication = new EccoApplication();


		/**
		 * There are two ways to initialize the ecco service:
		 * - here when starting grizzly using cli parameters.
		 * - in the servlet context listener using web.xml parameters.
		 * put this into a reusable (util) method that is used in both places!
		 */

		// create and start a new instance of grizzly http server exposing the Jersey application at BASE_URI
		final HttpServer httpServer = GrizzlyHttpServerFactory.createHttpServer(BASE_URI, eccoApplication, false);

		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				httpServer.shutdownNow();
			}
		}));

		eccoApplication.init(repositoryDir);

		httpServer.start();

		System.out.println(String.format("Jersey app started with WADL available at %s\n", BASE_URI));

		Thread.currentThread().join();

		eccoApplication.destroy();
	}

}
