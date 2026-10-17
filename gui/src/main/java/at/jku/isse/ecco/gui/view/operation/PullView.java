package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.service.EccoService;

import java.util.Collection;
import java.util.Set;

/** Pulls a remote's feature revisions into this repository - see {@link TransferView}. */
public class PullView extends TransferView {

	public PullView(EccoService service) {
		super(service);
	}

	@Override
	protected String verb() {
		return "Pull";
	}

	@Override
	protected String preposition() {
		return "from";
	}

	@Override
	protected void transfer(String remote, String deselection, java.util.function.BooleanSupplier cancelled) {
		this.service.pull(remote, deselection, cancelled);
	}

	/** The remote's as last fetched - shown while they are fetched again. */
	@Override
	protected Collection<? extends Feature> featuresNow(String remote) {
		return this.service.getRemote(remote) == null ? java.util.List.of() : this.service.getRemote(remote).getFeatures();
	}

	/** The remote's, fetched first: a remote that was never fetched has none yet. */
	@Override
	protected Collection<? extends Feature> features(String remote) {
		this.service.fetch(remote);
		return this.service.getRemote(remote).getFeatures();
	}

	@Override
	protected Set<String> revisionsAtTarget(String remote) {
		return revisions(this.service.getRepository().getFeatures());
	}
}
