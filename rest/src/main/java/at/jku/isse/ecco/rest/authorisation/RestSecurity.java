package at.jku.isse.ecco.rest.authorisation;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Who may log in, and the check that the server is not run with the demonstration setup.
 * <p>
 * Outside development mode the server refuses to start unless {@code JWT_GENERATOR_SIGNATURE_SECRET}
 * holds a secret of at least 32 characters other than the published default (anyone knowing the
 * default can sign tokens for any user), and {@value #USERS_FILE_ENV} names a users file. Development
 * mode ({@value #DEV_ENV}=true, or the system property {@value #DEV_PROPERTY}) - which
 * {@code gradlew :ecco-rest:run} and the tests use - keeps the demonstration users and the default
 * secret, with a warning.
 * <p>
 * Users file: one user per line, {@code name:password-hash:Role[,Role]} ({@code Admin}, {@code User});
 * blank lines and lines starting with # are ignored. A hash is printed by
 * {@code ecco-rest --hash-password}.
 */
@Context
@Singleton
public class RestSecurity {

    public static final String DEV_ENV = "ECCO_REST_DEV";
    public static final String DEV_PROPERTY = "ecco.rest.dev";
    public static final String USERS_FILE_ENV = "ECCO_REST_USERS_FILE";
    static final String DEFAULT_SECRET = "pleaseChangeThisSecretForANewOne";
    static final int MIN_SECRET_LENGTH = 32;

    private final Map<String, User> users = new HashMap<>();

    public RestSecurity(@Value("${micronaut.security.token.jwt.signatures.secret.generator.secret:}") String secret) {
        this(secret, isDevMode(), Optional.ofNullable(System.getenv(USERS_FILE_ENV)).filter(s -> !s.isBlank()).map(Path::of));
    }

    RestSecurity(String secret, boolean devMode, Optional<Path> usersFile) {
        List<String> problems = new ArrayList<>();
        if (secret == null || secret.isBlank())
            problems.add("no JWT signing secret is configured (set JWT_GENERATOR_SIGNATURE_SECRET)");
        else if (secret.equals(DEFAULT_SECRET))
            problems.add("JWT_GENERATOR_SIGNATURE_SECRET is the published default, so anyone can sign tokens");
        else if (secret.length() < MIN_SECRET_LENGTH)
            problems.add("JWT_GENERATOR_SIGNATURE_SECRET is shorter than " + MIN_SECRET_LENGTH + " characters");
        if (usersFile.isEmpty())
            problems.add("no users file is configured (set " + USERS_FILE_ENV + ")");

        if (!problems.isEmpty() && !devMode)
            throw new IllegalStateException("Refusing to start the ECCO REST server: " + String.join("; ", problems)
                    + ". For local development only, set " + DEV_ENV + "=true.");

        if (usersFile.isPresent()) {
            for (User user : readUsers(usersFile.get()))
                users.put(user.getName(), user);
        } else {
            for (User user : DummyUserDB.users())
                users.put(user.getName(), user);
        }
        if (devMode && !problems.isEmpty())
            System.err.println("WARNING: ECCO REST server in development mode - " + String.join("; ", problems)
                    + (usersFile.isEmpty() ? ". The demonstration users with their published passwords can log in." : "."));
    }

    public static boolean isDevMode() {
        return Boolean.getBoolean(DEV_PROPERTY) || "true".equalsIgnoreCase(System.getenv(DEV_ENV));
    }

    Optional<User> findUser(String name) {
        return Optional.ofNullable(users.get(name));
    }

    static List<User> readUsers(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the users file " + file + ": " + e.getMessage(), e);
        }
        List<User> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#"))
                continue;
            String[] parts = line.split(":", 3);
            try {
                if (parts.length != 3 || parts[0].isBlank())
                    throw new IllegalArgumentException("expected name:password-hash:roles");
                PasswordHash.matches(new char[0], parts[1]); // validates the format
                List<Role> roles = Arrays.stream(parts[2].split(",")).map(String::trim).map(Role::valueOf).toList();
                result.add(new User(parts[0], parts[1], roles));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Line " + (i + 1) + " of the users file " + file + " is invalid: " + e.getMessage(), e);
            }
        }
        if (result.isEmpty())
            throw new IllegalStateException("The users file " + file + " defines no users.");
        return result;
    }
}
