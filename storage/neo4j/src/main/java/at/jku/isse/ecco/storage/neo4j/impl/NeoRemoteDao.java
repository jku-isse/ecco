package at.jku.isse.ecco.storage.neo4j.impl;

import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.dao.RemoteDao;
import at.jku.isse.ecco.storage.mem.core.MemRemote;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;

import static java.util.Objects.requireNonNull;

public class NeoRemoteDao implements RemoteDao {

	private final NeoTransactionStrategy transactionStrategy;

	@Inject
	public NeoRemoteDao(NeoTransactionStrategy transactionStrategy, final MemEntityFactory entityFactory) {
		this.transactionStrategy = transactionStrategy;
	}

	@Override
	public Collection<Remote> loadAllRemotes() {
		return new ArrayList<>(transactionStrategy.load().getRemoteIndex().values());
	}

	@Override
	public Remote loadRemote(String name) {
		requireNonNull(name);
		assert !name.isEmpty() : "Expected a non-empty name!";

		return transactionStrategy.load().getRemoteIndex().get(name);
	}

	@Override
	public Remote storeRemote(Remote remote) {
		requireNonNull(remote);
		final MemRemote xmlRemote = (MemRemote) remote;
		Object returnVal = transactionStrategy.load().getRemoteIndex().putIfAbsent(xmlRemote.getName(), xmlRemote);
		assert returnVal == null;
		return xmlRemote;
	}

	@Override
	public void removeRemote(String name) {
		requireNonNull(name);
		Object returnVal = transactionStrategy.load().getRemoteIndex().remove(name);
		assert returnVal != null;
	}

	@Override
	public void open() {

	}

	@Override
	public void close() {

	}

	@Override
	public void init() {

	}
}
