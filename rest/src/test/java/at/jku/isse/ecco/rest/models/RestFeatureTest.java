package at.jku.isse.ecco.rest.models;

import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.feature.FeatureRevision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class RestFeatureTest {

    @Test
    public void delegatesSimpleGettersToTheWrappedFeature() {
        Feature feature = mock(Feature.class);
        when(feature.getId()).thenReturn("feature-id");
        when(feature.getName()).thenReturn("Core");
        when(feature.getDescription()).thenReturn("The core feature");

        RestFeature restFeature = new RestFeature(feature);

        assertEquals("feature-id", restFeature.getId());
        assertEquals("Core", restFeature.getName());
        assertEquals("The core feature", restFeature.getDescription());
    }

    @Test
    public void wrapsEachFeatureRevisionIndividually() {
        Feature feature = mock(Feature.class);
        FeatureRevision revisionA = mock(FeatureRevision.class);
        FeatureRevision revisionB = mock(FeatureRevision.class);
        when(revisionA.getId()).thenReturn("rev-a");
        when(revisionB.getId()).thenReturn("rev-b");
        doReturn(List.of(revisionA, revisionB)).when(feature).getRevisions();

        RestFeature restFeature = new RestFeature(feature);

        List<String> ids = restFeature.getRevisions().stream().map(RestFeatureRevision::getId).toList();
        assertEquals(List.of("rev-a", "rev-b"), ids);
    }

    @Test
    public void getRevisionsOfFeatureWithNoRevisionsIsEmpty() {
        Feature feature = mock(Feature.class);
        doReturn(List.of()).when(feature).getRevisions();

        RestFeature restFeature = new RestFeature(feature);

        assertTrue(restFeature.getRevisions().isEmpty());
    }
}
