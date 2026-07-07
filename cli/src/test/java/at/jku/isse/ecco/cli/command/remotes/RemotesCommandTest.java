package at.jku.isse.ecco.cli.command.remotes;

import at.jku.isse.ecco.cli.writer.StringWriter;
import at.jku.isse.ecco.core.Remote;
import at.jku.isse.ecco.service.EccoService;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class RemotesCommandTest {
    private static Namespace namespaceOf(Map<String, Object> overrides) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(RemotesCommand.NAME_KEY, null);
        attrs.put(RemotesCommand.ADD_KEY, null);
        attrs.put(RemotesCommand.REMOVE_KEY, null);
        attrs.put(RemotesCommand.TYPE_KEY, "REMOTE");
        attrs.putAll(overrides);
        return new Namespace(attrs);
    }

    @Test
    public void listsAllRemotes() {
        EccoService service = mock(EccoService.class);
        StringWriter stringWriter = new StringWriter();
        RemotesCommand command = new RemotesCommand(service, stringWriter);

        Remote remote = mock(Remote.class);
        when(remote.getName()).thenReturn("origin");
        when(remote.getAddress()).thenReturn("host:1234");
        when(remote.getType()).thenReturn(Remote.Type.REMOTE);
        doReturn(List.of(remote)).when(service).getRemotes();

        command.run(namespaceOf(Map.of()));

        verify(service).open();
        verify(service).close();
        assertEquals(1, stringWriter.getLines().size());
        assertEquals("origin: host:1234 [REMOTE]", stringWriter.getLines().get(0));
    }

    @Test
    public void addsRemote() {
        EccoService service = mock(EccoService.class);
        StringWriter stringWriter = new StringWriter();
        RemotesCommand command = new RemotesCommand(service, stringWriter);

        Remote remote = mock(Remote.class);
        when(remote.getName()).thenReturn("upstream");
        when(remote.getAddress()).thenReturn("/some/path");
        when(remote.getType()).thenReturn(Remote.Type.LOCAL);
        when(service.addRemote("upstream", "/some/path", Remote.Type.LOCAL)).thenReturn(remote);

        Map<String, Object> attrs = new HashMap<>();
        attrs.put(RemotesCommand.ADD_KEY, List.of("upstream", "/some/path"));
        attrs.put(RemotesCommand.TYPE_KEY, "LOCAL");
        command.run(namespaceOf(attrs));

        verify(service).addRemote("upstream", "/some/path", Remote.Type.LOCAL);
        assertEquals(1, stringWriter.getLines().size());
    }

    @Test
    public void removesRemote() {
        EccoService service = mock(EccoService.class);
        StringWriter stringWriter = new StringWriter();
        RemotesCommand command = new RemotesCommand(service, stringWriter);

        command.run(namespaceOf(Map.of(RemotesCommand.REMOVE_KEY, "origin")));

        verify(service).removeRemote("origin");
    }

    @Test
    public void showsSingleRemote() {
        EccoService service = mock(EccoService.class);
        StringWriter stringWriter = new StringWriter();
        RemotesCommand command = new RemotesCommand(service, stringWriter);

        Remote remote = mock(Remote.class);
        when(remote.getName()).thenReturn("origin");
        when(remote.getAddress()).thenReturn("host:1234");
        when(remote.getType()).thenReturn(Remote.Type.REMOTE);
        when(service.getRemote("origin")).thenReturn(remote);

        command.run(namespaceOf(Map.of(RemotesCommand.NAME_KEY, "origin")));

        assertEquals(1, stringWriter.getLines().size());
        assertEquals("origin: host:1234 [REMOTE]", stringWriter.getLines().get(0));
    }

    @Test
    public void reportsMissingRemote() {
        EccoService service = mock(EccoService.class);
        StringWriter stringWriter = new StringWriter();
        RemotesCommand command = new RemotesCommand(service, stringWriter);

        when(service.getRemote("missing")).thenReturn(null);

        command.run(namespaceOf(Map.of(RemotesCommand.NAME_KEY, "missing")));

        assertEquals(1, stringWriter.getLines().size());
        assertEquals("Remote missing does not exist.", stringWriter.getLines().get(0));
    }
}
