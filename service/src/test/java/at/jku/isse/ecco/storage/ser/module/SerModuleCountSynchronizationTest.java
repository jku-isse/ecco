package at.jku.isse.ecco.storage.ser.module;

import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.module.Module;
import at.jku.isse.ecco.module.ModuleRevision;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Closes the last piece of association-counter-unsynchronized-race (project memory):
 * {@link SerModule}/{@link SerModuleRevision}'s own plain {@code int count} fields, read via
 * {@code Association.Op#computeLikelyCondition()}/{@code #computeCertainCondition()}'s
 * {@code moduleRevisionCounter.getObject().getCount()} on the FX thread while a commit's background
 * write concurrently calls {@code incCount()} elsewhere. Unlike the counter-tree fix (a collection
 * structural-modification race with a reliably reproducible crash), a lone {@code int} can't tear
 * mid-word, so the only risk here was staleness/visibility, not a crash - {@code synchronized} closes
 * that by Java Memory Model guarantee, not because a test can reliably force a visibility bug to
 * manifest one way or the other. {@link #concurrentReadsAndWritesNeverThrowOrLoseAnIncrement} is
 * therefore a sanity/smoke test (no exception, correct final count under concurrent access), not a
 * reproduction of a specific prior crash the way {@code AssociationCounterTest}'s stress test is.
 */
public class SerModuleCountSynchronizationTest {

	private final EntityFactory ef = new SerEntityFactory();
	private final Repository.Op repository = repository();

	private Repository.Op repository() {
		Repository.Op repository = ef.createRepository();
		repository.setMaxOrder(2);
		return repository;
	}

	@Test
	public void moduleIncCountAccumulatesCorrectly() {
		Feature feature = repository.addFeature(UUID.randomUUID().toString(), "A");
		Module module = repository.addModule(new Feature[]{feature}, new Feature[0]);

		module.incCount(3);
		module.incCount(2);

		assertEquals(5, module.getCount());
	}

	@Test
	public void moduleRevisionIncCountAccumulatesCorrectly() {
		Feature feature = repository.addFeature(UUID.randomUUID().toString(), "A");
		Module module = repository.addModule(new Feature[]{feature}, new Feature[0]);
		FeatureRevision featureRevision = feature.addRevision("1");
		ModuleRevision moduleRevision = module.addRevision(new FeatureRevision[]{featureRevision}, new Feature[0]);

		moduleRevision.incCount(4);
		moduleRevision.incCount(1);

		assertEquals(5, moduleRevision.getCount());
	}

	@Test
	@Timeout(30)
	public void concurrentReadsAndWritesNeverThrowOrLoseAnIncrement() throws InterruptedException {
		Feature feature = repository.addFeature(UUID.randomUUID().toString(), "A");
		Module module = repository.addModule(new Feature[]{feature}, new Feature[0]);
		FeatureRevision featureRevision = feature.addRevision("1");
		ModuleRevision moduleRevision = module.addRevision(new FeatureRevision[]{featureRevision}, new Feature[0]);

		int writerIncrements = 5000;
		Thread writer = new Thread(() -> {
			for (int i = 0; i < writerIncrements; i++) {
				module.incCount();
				moduleRevision.incCount();
			}
		});

		final Throwable[] readerFailure = new Throwable[1];
		Thread reader = new Thread(() -> {
			try {
				// exercises the exact same reads computeLikelyCondition()/computeCertainCondition() do
				while (writer.isAlive()) {
					module.getCount();
					moduleRevision.getCount();
				}
			} catch (Throwable t) {
				readerFailure[0] = t;
			}
		});

		writer.start();
		reader.start();
		writer.join();
		reader.join();

		assertNull(readerFailure[0], "reading getCount() concurrently with incCount() must never throw");
		assertEquals(writerIncrements, module.getCount());
		assertEquals(writerIncrements, moduleRevision.getCount());
	}
}
