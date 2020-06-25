package at.jku.isse.ecco.web.domain.model;

public abstract class OperationResponse {

    public OperationResponse(boolean operationSuccessful, boolean eccoServiceIsInitialized) {
        this.eccoServiceIsInitialized = eccoServiceIsInitialized;
        this.operationSuccessful = operationSuccessful;
    }

    public OperationResponse() {

    }

    private boolean operationSuccessful;
    private boolean eccoServiceIsInitialized;

    public boolean isOperationSuccessful() {
        return operationSuccessful;
    }

    public void setOperationSuccessful(boolean operationSuccessful) {
        this.operationSuccessful = operationSuccessful;
    }

    public boolean isEccoServiceIsInitialized() {
        return eccoServiceIsInitialized;
    }

    public void setEccoServiceIsInitialized(boolean eccoServiceIsInitialized) {
        this.eccoServiceIsInitialized = eccoServiceIsInitialized;
    }
}
