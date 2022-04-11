package at.jku.isse.ecco.rest.classes;

public class RepoHeader {
    private final int id;
    private final String name;
    public RepoHeader(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
