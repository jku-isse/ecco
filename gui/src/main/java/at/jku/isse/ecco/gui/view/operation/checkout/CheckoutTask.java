package at.jku.isse.ecco.gui.view.operation.checkout;

import at.jku.isse.ecco.core.Checkout;
import at.jku.isse.ecco.service.EccoService;
import javafx.concurrent.Task;

public class CheckoutTask extends Task<Checkout> {
    private final EccoService service;
    private final CheckoutView parentView;
    private final String configurationString;

    protected CheckoutTask(CheckoutView parentView, EccoService service, String configurationString) {
        this.parentView = parentView;
        this.service = service;
        this.configurationString = configurationString;
    }

    @Override
    public Checkout call() {
        return service.checkout(configurationString);
    }

    @Override
    public void succeeded() {
        super.succeeded();

        parentView.checkoutSucceeded(getValue());
    }

    @Override
    public void cancelled() {
        super.cancelled();

        parentView.checkoutFailed(getException());
    }

    @Override
    public void failed() {
        super.failed();

        parentView.checkoutFailed(getException());
    }
}
