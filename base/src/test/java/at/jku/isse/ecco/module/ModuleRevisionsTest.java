package at.jku.isse.ecco.module;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import at.jku.isse.ecco.storage.ser.feature.SerFeature;
import at.jku.isse.ecco.storage.ser.feature.SerFeatureRevision;
import at.jku.isse.ecco.storage.ser.module.SerModule;
import at.jku.isse.ecco.storage.ser.module.SerModuleRevision;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ModuleRevisions} resolves the "trim set of missing modules" TODO in
 * {@link at.jku.isse.ecco.repository.Repository.Op#compose} and replaces the raw, hash-suffixed
 * {@link ModuleRevision#toString()} used to render MISSING checkout diagnostics.
 */
public class ModuleRevisionsTest {

	private final SerFeature featureA = new SerFeature("fa", "FeatureA");
	private final SerFeature featureB = new SerFeature("fb", "FeatureB");
	private final SerFeature featureC = new SerFeature("fc", "FeatureC");
	private final SerFeatureRevision revA = featureA.addRevision("r1");
	private final SerFeatureRevision revB = featureB.addRevision("r1");
	private final SerFeatureRevision revC = featureC.addRevision("r1");

	private ModuleRevision moduleRevision(FeatureRevision[] pos, Feature[] neg) {
		SerModule module = new SerModule(Arrays.stream(pos).map(FeatureRevision::getFeature).toArray(Feature[]::new), neg);
		return new SerModuleRevision(module, pos, neg);
	}

	@Test
	public void trimRedundant_dropsHigherOrderCombinationCoveredByALowerOrderMissingSubCombination() {
		ModuleRevision mrAB = moduleRevision(new FeatureRevision[]{revA, revB}, new at.jku.isse.ecco.feature.Feature[0]);
		ModuleRevision mrABC = moduleRevision(new FeatureRevision[]{revA, revB, revC}, new at.jku.isse.ecco.feature.Feature[0]);

		Set<ModuleRevision> missing = new HashSet<>(Arrays.asList(mrAB, mrABC));
		Set<ModuleRevision> trimmed = ModuleRevisions.trimRedundant(missing);

		assertEquals(Set.of(mrAB), trimmed);
	}

	@Test
	public void trimRedundant_keepsAMissingCombinationWhoseSubCombinationsAreNotMissing() {
		ModuleRevision mrAB = moduleRevision(new FeatureRevision[]{revA, revB}, new at.jku.isse.ecco.feature.Feature[0]);

		Set<ModuleRevision> missing = new HashSet<>(Set.of(mrAB));
		Set<ModuleRevision> trimmed = ModuleRevisions.trimRedundant(missing);

		assertEquals(Set.of(mrAB), trimmed);
	}

	@Test
	public void describe_rendersFeatureNamesNotRawToString() {
		ModuleRevision mrAB = moduleRevision(new FeatureRevision[]{revA, revB}, new at.jku.isse.ecco.feature.Feature[0]);

		assertEquals("FeatureA + FeatureB", ModuleRevisions.describe(mrAB));
	}

	@Test
	public void describe_rendersNegativeFeaturesAsWithoutClause() {
		ModuleRevision mrA_notC = moduleRevision(new FeatureRevision[]{revA}, new Feature[]{featureC});

		assertEquals("FeatureA (without FeatureC)", ModuleRevisions.describe(mrA_notC));
	}

	@Test
	public void relevanceOrder_sortsLowerOrderBeforeHigherOrder() {
		ModuleRevision mrA = moduleRevision(new FeatureRevision[]{revA}, new at.jku.isse.ecco.feature.Feature[0]);
		ModuleRevision mrAB = moduleRevision(new FeatureRevision[]{revA, revB}, new at.jku.isse.ecco.feature.Feature[0]);

		assertTrue(ModuleRevisions.RELEVANCE_ORDER.compare(mrA, mrAB) < 0);
	}

	@Test
	public void relevanceOrder_breaksTiesDeterministicallyByDescription() {
		ModuleRevision mrA = moduleRevision(new FeatureRevision[]{revA}, new at.jku.isse.ecco.feature.Feature[0]);
		ModuleRevision mrB = moduleRevision(new FeatureRevision[]{revB}, new at.jku.isse.ecco.feature.Feature[0]);

		assertTrue(ModuleRevisions.RELEVANCE_ORDER.compare(mrA, mrB) < 0);
		assertEquals(0, ModuleRevisions.RELEVANCE_ORDER.compare(mrA, mrA));
	}

}
