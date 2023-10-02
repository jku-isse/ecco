package at.jku.isse.ecco.rest.authorisation;

import java.util.Collection;

public class User {
    private final String name;
    private final String password;
    private final Collection<Role> roles;

    public User(final String name, final String password, final Collection<Role> roles) {
        this.name = name;
        this.password = password;
        this.roles = roles;
    }

    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    public Collection<String> getRoles() {
        return roles.stream().map(Enum::toString).toList();
    }
}
