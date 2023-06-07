package at.jku.isse.ecco.adapter.golang;

import static org.junit.jupiter.api.Assertions.*;

import at.jku.isse.ecco.adapter.ArtifactPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GoPluginTest {
    private static final String EXPECTED_PLUGIN_NAME = "at.jku.isse.ecco.adapter.golang.GoPlugin";
    @Test
    public void mustBeInstanceOfArtifactPlugin() {
        assertInstanceOf(ArtifactPlugin.class, new GoPlugin());
    }

    @Test
    public void pluginIdMustReturnClassName() {
        assertEquals(EXPECTED_PLUGIN_NAME, new GoPlugin().getPluginId());
    }

    @Test
    public void moduleMustBeGoModule() {
        assertNotNull(new GoPlugin().getModule());
        assertInstanceOf(GoModule.class, new GoPlugin().getModule());
    }

    @Test
    public void nameMustBeSimpleClassName() {
        assertEquals(GoPlugin.class.getSimpleName(), new GoPlugin().getName());
    }

    @Test
    public void descriptionMustNotBeNullOrEmpty() {
        assertNotNull(new GoPlugin().getDescription());
        assertNotEquals("", new GoPlugin().getDescription());
    }
}
