package at.jku.isse.ecco.rest.models;

public class RepoHeader {
    private final int repositoryHandlerId;
    private final String repositoryName;
    public RepoHeader(int repositoryHandlerId, String repositoryName) {
        this.repositoryHandlerId = repositoryHandlerId;
        this.repositoryName = repositoryName;
    }

    public int getRepositoryHandlerId() {
        return repositoryHandlerId;
    }

    public String getName() {
        return repositoryName;
    }
}
