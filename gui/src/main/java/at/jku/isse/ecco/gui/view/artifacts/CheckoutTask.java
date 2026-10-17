package at.jku.isse.ecco.gui.view.artifacts;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.gui.ExceptionAlert;
import at.jku.isse.ecco.gui.Notifications;
import at.jku.isse.ecco.service.EccoService;
import javafx.concurrent.Task;

public class CheckoutTask extends Task<Void> {
    private final EccoService service;
    private final LazyCompositionRootNode rootNode;
    private final java.nio.file.Path directory;

    /** Writes [rootNode]'s artifacts into [directory]; the service's base directory is left as it is. */
    protected CheckoutTask(EccoService service, LazyCompositionRootNode rootNode, java.nio.file.Path directory) {
        this.service = service;
        this.rootNode = rootNode;
        this.directory = directory;
    }

    @Override
    public Void call() throws EccoException {
        service.checkout(rootNode, directory);
        return null;
    }

    @Override
    public void succeeded() {
        super.succeeded();

        Notifications.success("Checkout Successful!");
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
