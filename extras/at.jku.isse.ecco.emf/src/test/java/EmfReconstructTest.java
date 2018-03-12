import at.jku.isse.ecco.dao.PerstEntityFactory;
import at.jku.isse.ecco.plugin.emf.EmfReader;
import at.jku.isse.ecco.plugin.emf.EmfReconstruct;
import at.jku.isse.ecco.tree.Node;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static org.testng.AssertJUnit.assertTrue;

/**
 * Created by hhoyos on 22/05/2017.
 */
public class EmfReconstructTest {

    Node.Op root;
    private EmfReader reader;


    @Before
    public void createTree() throws URISyntaxException {
        reader = new EmfReader(new PerstEntityFactory(), new ResourceSetImpl());
        ClassLoader classLoader = getClass().getClassLoader();
        //URI uri = URI.createURI(classLoader.getResource("Library.xmi").toString());
        Path path;
        path = Paths.get(classLoader.getResource("Library.xmi").toURI());
        Set<Node.Op> nodes = reader.read(new Path[]{path});
        root = nodes.iterator().next();
    }

    @After
    public void tearDown() {
        if (reader != null) {
            reader.getResourceSet().getResources().clear();
        }
        root = null;
    }

    @Test
    public void testReconstructResource() {
        EmfReconstruct rc = new EmfReconstruct();
        Resource r = rc.reconstructResource(root, reader.getResourceSet());
        assertTrue(r.getContents().size() > 0);
        // Maybe create another reader and compare?

    }
}
