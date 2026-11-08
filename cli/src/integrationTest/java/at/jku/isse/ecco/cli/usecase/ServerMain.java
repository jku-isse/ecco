package at.jku.isse.ecco.cli.usecase;

import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.service.EccoListener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * The sync server of UC-13 in a JVM of its own, for the end-to-end tests: what the GUI's server
 * section does (Preferences), without the GUI - the command line has no server. Serves the
 * repository in {@code args[0]} on the loopback interface, port {@code args[1]} (0 for any).
 * <p>
 * On stdout: {@code PORT <port>} once it listens, {@code EVENT <message>} for each server event (the
 * GUI's server log), {@code STOPPED} at the end. On stdin: {@code start} starts the server again
 * while it runs ({@code REFUSED <message>}, UC-13 SA1); {@code stop} or the end of the input stops it.
 */
public final class ServerMain {

    public static void main(String[] args) throws Exception {
        System.setProperty("ecco.headless", "true");
        Path dir = Path.of(args[0]);
        int port = Integer.parseInt(args[1]);

        try (EccoService service = new EccoService(dir)) {
            service.setRepositoryDir(dir.resolve(EccoService.REPOSITORY_DIR_NAME));
            service.open();
            service.addListener(new EccoListener() {
                @Override
                public void serverEvent(EccoService service, String message) {
                    System.out.println("EVENT " + message);
                }
            });

            Thread server = new Thread(() -> service.startServer(port), "ecco-server");
            server.start();
            while (service.serverPort() < 0) {
                if (!server.isAlive())
                    throw new IllegalStateException("the server did not start");
                Thread.sleep(10);
            }
            System.out.println("PORT " + service.serverPort());

            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            for (String line = in.readLine(); line != null && !line.equals("stop"); line = in.readLine()) {
                if (line.equals("start")) {
                    try {
                        service.startServer(port);
                        System.out.println("STARTED AGAIN");
                    } catch (RuntimeException e) {
                        System.out.println("REFUSED " + e.getMessage());
                    }
                }
            }

            service.stopServer();
            server.join(30_000);
            if (server.isAlive())
                throw new IllegalStateException("the server did not stop");
        }
        System.out.println("STOPPED");
    }
}
