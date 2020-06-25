package at.jku.isse.ecco.web.domain.model;

public class OperationContainer {

    private String repositoryOperation;
    private String repositoryDirectory;

    public String getRepositoryDirectory() {
        return repositoryDirectory;
    }

    public void setRepositoryDirectory(String repositoryDirectory) {
        this.repositoryDirectory = repositoryDirectory;
    }

    @Override
    public String toString() {
        return "ApplicationContainer.repositoryDirectory: (" + this.repositoryDirectory + ");";
    }

    public String getRepositoryOperation() {
        return repositoryOperation;
    }

    public void setRepositoryOperation(String repositoryOperation) {
        this.repositoryOperation = repositoryOperation;
    }
}
