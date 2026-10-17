package at.jku.isse.ecco.gui.view.operation;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.service.EccoService;

import java.util.Collection;
import java.util.Set;

/** Pushes this repository's feature revisions to a remote - see {@link TransferView}. */
public class PushView extends TransferView {

	public PushView(EccoService service) {
		super(service);
	}

	@Override
	protected String verb() {
		return "Push";
	}

	@Override
	protected String preposition() {
		return "to";
	}

	@Override
	protected void transfer(String remote, String deselection, java.util.function.BooleanSupplier cancelled) {
		this.service.push(remote, deselection, cancelled);
	}

	/** This repository's own. */
	@Override
	protected Collection<? extends Feature> features(String remote) {
		return this.service.getRepository().getFeatures();
	}

	/** The remote's, as a fetch reads them now. */
	@Override
	protected Set<String> revisionsAtTarget(String remote) {
		this.service.fetch(remote);
		return revisions(this.service.getRemote(remote).getFeatures());
	}
}
