package at.jku.isse.ecco.adapter.designspace;

import at.jku.isse.designspace.sdk.core.model.Workspace;
import at.jku.isse.ecco.adapter.designspace.exception.MultipleWorkspaceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

public class DesignSpaceReaderTest {
    @Test
    public void multipleWorkspacesAreNotSupported() {
        DesignSpaceReader reader = new DesignSpaceReader();
        assertThrows(MultipleWorkspaceException.class, () -> reader.read(new Workspace[2]));
        assertThrows(MultipleWorkspaceException.class, () -> reader.read(mock(Workspace.class), new Workspace[1]));
    }
}
