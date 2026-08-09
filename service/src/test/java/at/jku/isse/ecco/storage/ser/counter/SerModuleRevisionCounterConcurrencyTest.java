package at.jku.isse.ecco.storage.ser.counter;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.storage.ser.feature.SerFeature;
import at.jku.isse.ecco.storage.ser.module.SerModule;
import at.jku.isse.ecco.storage.ser.module.SerModuleRevision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SerModuleRevisionCounter.count used to be a plain, unsynchronized int, unlike its parent
 * SerModuleCounter's count/children (see that class's javadoc, and
 * association-counter-unsynchronized-race in project memory for the earlier, already-fixed instance
 * of this exact race one level up the same counter tree). Association.Op#addObservation() calls
 * incCount() during commit while Association.Op#computeLikelyCondition()/#computeCertainCondition()
 * call getCount() concurrently from e.g. a GUI background thread - concurrent unsynchronized
 * increments lose updates.
 */
public class SerModuleRevisionCounterConcurrencyTest {

	@Test
	@Timeout(30)
	public void incCountUnderConcurrentAccessLosesNoUpdates() throws InterruptedException {
		SerFeature feature = new SerFeature("id", "Feature");
		FeatureRevision revision = feature.addRevision("rev1");
		SerModule module = new SerModule(new Feature[]{feature}, new Feature[0]);
		SerModuleRevision moduleRevision = new SerModuleRevision(module, new FeatureRevision[]{revision}, new Feature[0]);
		SerModuleRevisionCounter counter = new SerModuleRevisionCounter(moduleRevision);

		int threadCount = 20;
		int incrementsPerThread = 5000;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		for (int i = 0; i < threadCount; i++) {
			executor.submit(() -> {
				for (int j = 0; j < incrementsPerThread; j++) {
					counter.incCount();
				}
			});
		}
		executor.shutdown();
		assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS), "executor did not terminate in time");

		assertEquals(threadCount * incrementsPerThread, counter.getCount(),
				"concurrent incCount() calls must not lose updates");
	}
}
