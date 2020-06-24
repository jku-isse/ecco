package at.jku.isse.ecco.web.domain.model;

public class ApplicationInitialization {

    private String repositoryDirectory;
    private String repositoryOperation;

    public String getRepositoryDirectory() {
        return repositoryDirectory;
    }

    public void setRepositoryDirectory(String repositoryDirectory) {
        this.repositoryDirectory = repositoryDirectory;
    }

    @Override
    public String toString() {
        return "ApplicationInitialization.repositoryDirectory: (" + this.repositoryDirectory + ");";
    }

    public String getRepositoryOperation() {
        return repositoryOperation;
    }

    public void setRepositoryOperation(String repositoryOperation) {
        this.repositoryOperation = repositoryOperation;
    }
}
