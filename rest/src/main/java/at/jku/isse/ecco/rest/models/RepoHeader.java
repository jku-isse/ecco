package at.jku.isse.ecco.rest.models;

public class RepoHeader {
    private final int id;
    private final String repositoryName;
    public RepoHeader(int id, String repositoryName) {
        this.id = id;
        this.repositoryName = repositoryName;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return repositoryName;
    }
}
