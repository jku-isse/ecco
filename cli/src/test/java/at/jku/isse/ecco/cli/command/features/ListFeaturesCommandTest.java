package at.jku.isse.ecco.cli.command.features;

import at.jku.isse.ecco.cli.writer.StringWriter;
import at.jku.isse.ecco.feature.Feature;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.service.EccoService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class ListFeaturesCommandTest {
    @Test
    public void printsFeatures() {
        EccoService service = mock(EccoService.class);
        Repository repository = mock(Repository.class);
        StringWriter stringWriter = new StringWriter();
        final List<Feature> features =  new ArrayList<>(){{
            add(new TestFeature("feature 1"));
            add(new TestFeature("feature 2"));
            add(new TestFeature("feature 3"));
        }};
        ListFeaturesCommand listFeaturesAction = new ListFeaturesCommand(service, stringWriter);

        when(service.getRepository()).thenReturn(repository);
        // thenReturn does not like generics
        doReturn(features).when(repository).getFeatures();

        listFeaturesAction.run();

        verify(service).open();
        verify(service).getRepository();
        verify(repository).getFeatures();
        verify(service).close();

        assertEquals(3, stringWriter.getLines().size());
        assertTrue(stringWriter.getLines().get(0).contains("feature 1"));
        assertTrue(stringWriter.getLines().get(1).contains("feature 2"));
        assertTrue(stringWriter.getLines().get(2).contains("feature 3"));
    }
}
