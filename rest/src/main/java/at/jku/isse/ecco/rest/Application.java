package at.jku.isse.ecco.rest;

import at.jku.isse.ecco.rest.authorisation.PasswordHash;
import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.info.*;

import java.io.Console;
import java.util.Scanner;

@OpenAPIDefinition(
        info = @Info(
                title = "ECCO RESTService",
                version = "0.0.1",
                description = "Provides HTTP endpoints to an ECCO instance"
        )
)
public class Application {

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--hash-password")) {
            printPasswordHash();
            return;
        }
        // no GUI here: adapters leave out their JavaFX viewers (this distribution has no JavaFX)
        System.setProperty("ecco.headless", "true");
        Micronaut.run(Application.class, args);
    }

    /** Reads a password (without echo when there is a console) and prints its hash for a users file line. */
    private static void printPasswordHash() {
        char[] password;
        Console console = System.console();
        if (console != null) {
            password = console.readPassword("Password: ");
        } else {
            String line = new Scanner(System.in).nextLine();
            password = line.toCharArray();
        }
        if (password == null || password.length == 0) {
            System.err.println("No password given.");
            System.exit(2);
        }
        System.out.println(PasswordHash.hash(password));
    }
}
