package at.jku.isse.ecco.gui.view.artifacts;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.service.EccoService;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;

public class CheckoutTask extends Task<Void> {
    private final EccoService service;
    private final LazyCompositionRootNode rootNode;

    protected CheckoutTask(EccoService service, LazyCompositionRootNode rootNode) {
        this.service = service;
        this.rootNode = rootNode;
    }

    @Override
    public Void call() throws EccoException {
        service.checkout(rootNode);
        return null;
    }

    @Override
    public void succeeded() {
        super.succeeded();

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Checkout Successful");
        alert.setHeaderText("Checkout Successful");
        alert.setContentText("Checkout Successful!");

        alert.showAndWait();
    }

    @Override
    public void cancelled() {
        super.cancelled();
    }

    @Override
    public void failed() {
        super.failed();

        ExceptionAlert alert = new ExceptionAlert(this.getException());
        alert.setTitle("Checkout Error");
        alert.setHeaderText("Checkout Error");

        alert.showAndWait();
    }
}
