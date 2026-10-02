package at.jku.isse.ecco.rest.authorisation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The demonstration users, with well-known passwords. Used only in development mode
 * ({@link RestSecurity#isDevMode()}); otherwise the users come from {@link RestSecurity#USERS_FILE_ENV}.
 */
public class DummyUserDB {

    private DummyUserDB() {
    }

    static List<User> users() {
        // few iterations: these passwords are public anyway
        return List.of(
                new User("Thomas", PasswordHash.hash("firstUser".toCharArray(), 1_000), Collections.singleton(Role.User)),
                new User("Max", PasswordHash.hash("secondUser".toCharArray(), 1_000), Collections.singleton(Role.User)),
                new User("Tobias", PasswordHash.hash("admin".toCharArray(), 1_000), Arrays.asList(Role.User, Role.Admin)),
                new User("Matthias", PasswordHash.hash("admin".toCharArray(), 1_000), Arrays.asList(Role.User, Role.Admin)),
                new User("Paul", PasswordHash.hash("admin".toCharArray(), 1_000), Arrays.asList(Role.User, Role.Admin)));
    }
}
