package at.jku.isse.ecco.web.domain.model;

public class CloseOperationResponse extends OperationResponse {

    public CloseOperationResponse() {

    }

    public CloseOperationResponse(boolean eccoServiceIsInitialized) {
        this.setEccoServiceIsInitialized(eccoServiceIsInitialized);
    }
}
