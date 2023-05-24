package at.jku.isse.ecco.adapter.golang;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class GoWriterTest {
    @Test
    public void pluginIdIsEqualToGoPluginId() {
        assertEquals(new GoPlugin().getPluginId(), new GoWriter().getPluginId());
    }
}
