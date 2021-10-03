package at.jku.isse.ecco.web.domain.model;

public abstract class OperationResponse {

    public OperationResponse(boolean eccoServiceIsInitialized) {
        this.eccoServiceIsInitialized = eccoServiceIsInitialized;
    }

    public OperationResponse() {

    }

    private boolean eccoServiceIsInitialized;

    public boolean isEccoServiceIsInitialized() {
        return eccoServiceIsInitialized;
    }

    public void setEccoServiceIsInitialized(boolean eccoServiceIsInitialized) {
        this.eccoServiceIsInitialized = eccoServiceIsInitialized;
    }
}
