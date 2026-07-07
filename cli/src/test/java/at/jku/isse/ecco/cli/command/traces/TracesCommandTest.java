package at.jku.isse.ecco.cli.command.traces;

import at.jku.isse.ecco.cli.writer.StringWriter;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.repository.Repository;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.tree.RootNode;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class TracesCommandTest {
    private static Namespace namespaceOf(String id) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(TracesCommand.ID_KEY, id);
        return new Namespace(attrs);
    }

    @Test
    public void listsAllTraces() {
        EccoService service = mock(EccoService.class);
        Repository repository = mock(Repository.class);
        StringWriter stringWriter = new StringWriter();
        TracesCommand command = new TracesCommand(service, stringWriter);

        Association association = mock(Association.class);
        Condition condition = mock(Condition.class);
        when(association.getId()).thenReturn("assoc-1");
        when(association.computeCondition()).thenReturn(condition);
        when(condition.getModuleRevisionConditionString()).thenReturn("featureA.1");
        when(service.getRepository()).thenReturn(repository);
        doReturn(List.of(association)).when(repository).getAssociations();

        command.run(namespaceOf(null));

        verify(service).open();
        verify(service).close();
        assertEquals(1, stringWriter.getLines().size());
        assertTrue(stringWriter.getLines().get(0).contains("assoc-1"));
        assertTrue(stringWriter.getLines().get(0).contains("featureA.1"));
    }

    @Test
    public void showsSingleTraceWithTree() {
        EccoService service = mock(EccoService.class);
        Repository repository = mock(Repository.class);
        StringWriter stringWriter = new StringWriter();
        TracesCommand command = new TracesCommand(service, stringWriter);

        Association association = mock(Association.class);
        Condition condition = mock(Condition.class);
        RootNode rootNode = mock(RootNode.class);
        when(association.getId()).thenReturn("assoc-1");
        when(association.computeCondition()).thenReturn(condition);
        when(condition.getModuleRevisionConditionString()).thenReturn("featureA.1");
        when(association.getRootNode()).thenReturn(rootNode);
        when(rootNode.isAtomic()).thenReturn(true);
        when(service.getRepository()).thenReturn(repository);
        doReturn(List.of(association)).when(repository).getAssociations();

        command.run(namespaceOf("assoc-1"));

        assertEquals(1, stringWriter.getLines().size());
        verify(association).getRootNode();
    }
}
