package at.jku.isse.ecco.rest.authorisation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

enum Role {
    Admin, User
}

/** Dummy User DB
 * replace by preexisting DB or change Users in this DB
 * Change usage also in @class AuthenticationProviderUserPassword
 */
public class DummyUserDB {


    List<User> users = new ArrayList<>();

    public DummyUserDB() {
        users.add(new User("Thomas", "firstUser", Collections.singleton(Role.User)));
        users.add(new User("Max", "secondUser", Collections.singleton(Role.User)));
        users.add(new User("Tobias", "admin", Arrays.asList(Role.User, Role.Admin)) );
        users.add(new User("Matthias", "admin", Arrays.asList(Role.User, Role.Admin)));
        users.add(new User("Paul", "admin", Arrays.asList(Role.User, Role.Admin)));
    }

    public User findUser(String name) {
        return users.stream().filter(x -> x.getName().equals(name)).findFirst().orElse(null);
    }


}
