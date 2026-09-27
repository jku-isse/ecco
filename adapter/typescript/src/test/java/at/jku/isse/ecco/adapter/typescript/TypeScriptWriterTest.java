package at.jku.isse.ecco.adapter.typescript;

import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.typescript.data.LeafArtifactData;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TypeScriptWriter used to write every file to base.resolve(getFileName()) - the bare file name -
 * so a checked-out src/app/main.ts landed at the checkout root as main.ts (while the method still
 * reported src/app/main.ts as written), and two files with the same name in different folders
 * overwrote each other.
 */
public class TypeScriptWriterTest {

    @Test
    public void nestedFilesAreWrittenToTheirRelativePath() throws IOException {
        EntityFactory entityFactory = new SerEntityFactory();
        Path relativePath = Paths.get("src", "app", "main.ts");
        Node.Op fileNode = entityFactory.createOrderedNode(entityFactory.createArtifact(new PluginArtifactData("ts", relativePath)));
        fileNode.addChild(entityFactory.createNode(entityFactory.createArtifact(new LeafArtifactData("let x = 1;"))));

        Path base = Files.createTempDirectory("typescript-writer");
        Path[] written = new TypeScriptWriter().write(base, Set.of(fileNode));

        assertEquals(base.resolve(relativePath), written[0]);
        assertTrue(Files.exists(base.resolve(relativePath)), "the file must be written at its relative path");
        assertFalse(Files.exists(base.resolve("main.ts")), "not at the checkout root");
        assertTrue(Files.readString(base.resolve(relativePath)).contains("let x = 1;"));
    }
}
