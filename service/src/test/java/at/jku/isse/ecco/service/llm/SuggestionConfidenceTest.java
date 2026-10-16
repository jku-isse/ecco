package at.jku.isse.ecco.service.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SuggestionConfidenceTest {

	private static final List<String> KNOWN = List.of("extract", "server");

	private static ImportedFeatureTraces.Overlap same(String feature) {
		return new ImportedFeatureTraces.Overlap(feature, List.of("Extract.run"), List.of(), 1);
	}

	private static ImportedFeatureTraces.Overlap fileOnly(String feature) {
		return new ImportedFeatureTraces.Overlap(feature, List.of(), List.of(), 1);
	}

	@Test
	public void aKnownFeatureThatChangedTheSamePositionsIsTrusted() {
		SuggestionConfidence c = SuggestionConfidence.of("extract", KNOWN, List.of(same("extract")));
		assertEquals(0.9, c.value());
		assertFalse(c.uncertain());
		assertEquals("extract changed these positions before", c.reason());
	}

	@Test
	public void aKnownFeatureThatChangedNoneOfThesePlacesIsUncertain() {
		SuggestionConfidence c = SuggestionConfidence.of("server", KNOWN, List.of(same("extract")));
		assertEquals(0.15, c.value());
		assertTrue(c.uncertain());
		assertEquals("server changed none of these places before, but extract changed them more closely", c.reason());
	}

	@Test
	public void aNewNameWhereNoKnownFeatureChangedAnythingIsTrusted() {
		SuggestionConfidence c = SuggestionConfidence.of("zip-slip", KNOWN, List.of());
		assertEquals(0.8, c.value());
		assertEquals("new feature zip-slip: no known feature changed these places", c.reason());
	}

	@Test
	public void aNewNameWhereAKnownFeatureChangedExactlyThesePositionsIsUncertain() {
		SuggestionConfidence c = SuggestionConfidence.of("zip-slip", KNOWN, List.of(same("extract")));
		assertEquals(0.2, c.value());
		assertTrue(c.uncertain());
		assertEquals("new feature zip-slip, but extract changed exactly these positions before", c.reason());
	}

	@Test
	public void miscIsUncertain() {
		assertTrue(SuggestionConfidence.of("misc", KNOWN, List.of(fileOnly("server"))).uncertain());
		assertTrue(SuggestionConfidence.of("", KNOWN, List.of()).uncertain());
	}

	@Test
	public void theWeakestPartCountsAndSeveralFeaturesCostALittle() {
		SuggestionConfidence c = SuggestionConfidence.of("extract, server", KNOWN, List.of(same("extract"), fileOnly("server")));
		assertEquals(0.4, c.value(), "server: 0.6 - 0.15 (extract is closer), minus 0.05 for two features");
		assertEquals("server changed these files before, but extract changed them more closely", c.reason());
	}

	@Test
	public void withoutKnownFeaturesThereIsNothingToCompare() {
		SuggestionConfidence c = SuggestionConfidence.of("anything", List.of(), List.of());
		assertEquals(0.5, c.value());
		assertFalse(c.uncertain());
	}

	@Test
	public void removalsDoNotCount() {
		assertEquals(0.9, SuggestionConfidence.of("extract, -server", KNOWN, List.of(same("extract"))).value());
	}
}
