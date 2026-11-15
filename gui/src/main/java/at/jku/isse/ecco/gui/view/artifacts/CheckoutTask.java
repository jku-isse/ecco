package at.jku.isse.ecco.gui.view.artifacts;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.composition.LazyCompositionRootNode;
import at.jku.isse.ecco.gui.common.ExceptionAlert;
import at.jku.isse.ecco.gui.common.Notifications;
import at.jku.isse.ecco.service.EccoService;
import javafx.concurrent.Task;

public class CheckoutTask extends Task<Void> {
    private final EccoService service;
    private final java.util.function.Supplier<LazyCompositionRootNode> composition;
    private final java.nio.file.Path directory;

    /** Writes [rootNode]'s artifacts into [directory]; the service's base directory is left as it is. */
    protected CheckoutTask(EccoService service, LazyCompositionRootNode rootNode, java.nio.file.Path directory) {
        this(service, () -> rootNode, directory);
    }

    private CheckoutTask(EccoService service, java.util.function.Supplier<LazyCompositionRootNode> composition, java.nio.file.Path directory) {
        this.service = service;
        this.composition = composition;
        this.directory = directory;
    }

    /**
     * Writes the artifacts of [associations], composed, into [directory]. Their trees are read when the
     * task runs, holding the service's lock - not on the FX thread, where Checkout Selected composed
     * them while a commit could change them (NFR-C3, gap #100).
     */
    static CheckoutTask ofAssociations(EccoService service, java.util.Collection<? extends at.jku.isse.ecco.core.Association> associations, java.nio.file.Path directory) {
        return new CheckoutTask(service, () -> {
            LazyCompositionRootNode rootNode = new LazyCompositionRootNode();
            for (at.jku.isse.ecco.core.Association association : associations)
                rootNode.addOrigNode(association.getRootNode());
            return rootNode;
        }, directory);
    }

    @Override
    public Void call() throws EccoException {
        // composed and written holding the lock, so that no commit changes the trees in between
        synchronized (this.service) {
            this.service.checkout(this.composition.get(), this.directory);
        }
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
