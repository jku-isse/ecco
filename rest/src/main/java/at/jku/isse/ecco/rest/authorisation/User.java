package at.jku.isse.ecco.rest.authorisation;

import java.util.Collection;

public class User {
    private final String name;
    private final String passwordHash;
    private final Collection<Role> roles;

    /** @param passwordHash written by {@link PasswordHash#hash} */
    public User(final String name, final String passwordHash, final Collection<Role> roles) {
        this.name = name;
        this.passwordHash = passwordHash;
        this.roles = roles;
    }

    public String getName() {
        return name;
    }

    public boolean passwordMatches(String password) {
        return PasswordHash.matches(password.toCharArray(), passwordHash);
    }

    public Collection<String> getRoles() {
        return roles.stream().map(Enum::toString).toList();
    }
}
