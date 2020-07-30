package at.jku.isse.ecco.web.domain.model;

public class OperationContainer {

    private String repositoryOperation;
    private String baseDirectory;

    public String getBaseDirectory() {
        return baseDirectory;
    }

    public void setBaseDirectory(String baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    @Override
    public String toString() {
        return "ApplicationContainer.repositoryDirectory: (" + this.baseDirectory + ");";
    }

    public String getRepositoryOperation() {
        return repositoryOperation;
    }

    public void setRepositoryOperation(String repositoryOperation) {
        this.repositoryOperation = repositoryOperation;
    }
}
