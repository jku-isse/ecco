package at.jku.isse.ecco.storage.neo4j.test;

import at.jku.isse.ecco.storage.neo4j.NeoSessionFactory;
import at.jku.isse.ecco.storage.neo4j.test.domain.*;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.ogm.session.LoadStrategy;
import org.neo4j.ogm.session.Session;
import uk.co.jemos.podam.api.PodamFactory;
import uk.co.jemos.podam.api.PodamFactoryImpl;

import java.lang.reflect.Array;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;

public class NeoTests {

    private static final Path repoDir = Paths.get("src\\integrationTest\\data\\.neotest\\");
    static NeoSessionFactory factory;

    /*
    Start this test to create a server accessible by the Neo4J Client application

    Connect URL: bolt://localhost:7687
    User: neo4j
    Pwd: neo4j
     */
    @Test
    @Ignore
    public void startServer() throws InterruptedException {

        NeoSessionFactory nsf = new NeoSessionFactory(repoDir);
        System.out.println("\nServer started..");

        System.out.println("Waiting 15min");
        Thread.sleep(900000);

        System.out.println("Server shutdown");

    }

    /*
    Cleanup database and create new BaseContainer with randomized data
     */
    @BeforeClass
    public static void prepareDatabase() throws InterruptedException {
        factory = new NeoSessionFactory(repoDir);
        System.out.println("Factory created");

        Session session = factory.getNeoSession();

        //cleanup
        session.deleteAll(BaseContainer.class);
        session.deleteAll(SpecializedClass1.class);
        session.deleteAll(SpecializedClass2.class);
        session.deleteAll(SpecializedClass3.class);
        session.deleteAll(TreeRootNode.class);
        session.deleteAll(TreeNode.class);
        session.deleteAll(ArrayClass.class);
        session.deleteAll(SomeClass.class);

        BaseContainer repo = getRandomTestRepo();

        session.save(repo);

        System.out.println("ID: " + repo.getTree().getChildren().get(0).getChildren().get(0).getChildren().get(0).getNeoId());
    }

    /*
    Tests proposed by Lukas:
    - load entity into java object (e.g. person = session.loadpersonwithid(1))
    - change a value in java object (e.g. person.name = "changed") but do NOT yet call session.save
    - load same entity again (e.g. person2 = loadpersonwithid(1))
    - check if person1.name == "changed" (es könnte nämlich sein, dass dieser change dann überschrieben wird, siehe punkt 2 oben)
    - check if person2.name == "changed" oder "original" (wäre interessant zu wissen wie sich der neo4j ogm hier verhält)
     */
    @Test
    public void testSessionFactoryPersistBehaviorSchema() {
        factory.getFactory().setLoadStrategy(LoadStrategy.SCHEMA_LOAD_STRATEGY);
        Session session = factory.getNeoSession();

        // load entity into java object (e.g. person = session.loadpersonwithid(1))
        Collection<BaseContainer> repos = session.loadAll(BaseContainer.class);
        BaseContainer container = repos.iterator().next();

        // change a value in java object (e.g. person.name = "changed") but do NOT yet call session.save
        container.setStringValue("changed");
        container.setIntegerValue(12345);

        // load same entity again (e.g. person2 = loadpersonwithid(1))
        BaseContainer container2 = session.load(BaseContainer.class, container.getNeoId());

        // check if person1.name == "changed" (es könnte nämlich sein, dass dieser change dann überschrieben wird, siehe punkt 2 oben)
        assertEquals(container.getStringValue(), "changed");
        assertEquals(container.getIntegerValue(), 12345);

        // check if person2.name == "changed" oder "original" (wäre interessant zu wissen wie sich der neo4j ogm hier verhält)
        assertEquals(container2.getStringValue(), "changed");
        assertEquals(container2.getIntegerValue(), 12345);
    }

    /*
    Equal test, different load strategy
     */
    @Test
    public void testSessionFactoryPersistBehaviorPath() {
        factory.getFactory().setLoadStrategy(LoadStrategy.PATH_LOAD_STRATEGY);
        Session session = factory.getNeoSession();

        // load entity into java object (e.g. person = session.loadpersonwithid(1))
        Collection<BaseContainer> repos = session.loadAll(BaseContainer.class);
        BaseContainer container = repos.iterator().next();

        // change a value in java object (e.g. person.name = "changed") but do NOT yet call session.save
        container.setStringValue("changed");
        container.setIntegerValue(12345);

        // load same entity again (e.g. person2 = loadpersonwithid(1))
        BaseContainer container2 = session.load(BaseContainer.class, container.getNeoId());

        // check if person1.name == "changed" (es könnte nämlich sein, dass dieser change dann überschrieben wird, siehe punkt 2 oben)
        assertEquals(container.getStringValue(), "changed");
        assertEquals(container.getIntegerValue(), 12345);

        // check if person2.name == "changed" oder "original" (wäre interessant zu wissen wie sich der neo4j ogm hier verhält)
        assertEquals(container2.getStringValue(), "changed");
        assertEquals(container2.getIntegerValue(), 12345);
    }

    /*
    Test lazy loading
     */
    @Test
    public void testLazyLoadSchemaStrategy() {
        factory.getFactory().setLoadStrategy(LoadStrategy.SCHEMA_LOAD_STRATEGY);
        Session session = factory.getNeoSession();

        Collection<BaseContainer> repos = session.loadAll(BaseContainer.class);
        BaseContainer container = repos.iterator().next();

        TreeRootNode treeRoot = container.getTree();
        List<TreeNode> children = treeRoot.getChildren();
        assertTrue(children.size() > 0);
    }

    /*
    Test hydration from Lukas
     */
    @Test
    public void testLoadLukas() {
        //factory.getFactory().setLoadStrategy(LoadStrategy.PATH_LOAD_STRATEGY);
        factory.getFactory().setLoadStrategy(LoadStrategy.SCHEMA_LOAD_STRATEGY);
        Session session = factory.getNeoSession();

        Collection<BaseContainer> repos = session.loadAll(BaseContainer.class, 1);
        BaseContainer container = repos.iterator().next();

//        repos = session.loadAll(BaseContainer.class, 100);
//        container = repos.iterator().next();
//
//        TreeNode testNode1 = session.load(TreeNode.class, container.getTree().getChildren().get(0).getChildren().get(0).getChildren().get(0).getNeoId());

        TreeNode testNode1 = session.load(TreeRootNode.class, container.getTree().getNeoId(), 1);
        TreeNode testNode2 = session.load(TreeNode.class, testNode1.getChildren().get(0).getNeoId());
        TreeNode testNode3 = session.load(TreeNode.class, testNode2.getChildren().get(0).getNeoId());

        repos = session.loadAll(BaseContainer.class, -1);
        container = repos.iterator().next();

        TreeRootNode treeRoot = container.getTree();
        List<TreeNode> children = treeRoot.getChildren();
        assertTrue(children.size() > 0);
    }

    /*
    Test hydration with path strategy depth infinity
     */
    @Test
    public void testLoadPathStrategyInfiniteDepth() {
        factory.getFactory().setLoadStrategy(LoadStrategy.PATH_LOAD_STRATEGY);
        Session session = factory.getNeoSession();

        Collection<BaseContainer> repos = session.loadAll(BaseContainer.class, -1);
        BaseContainer container = repos.iterator().next();

        TreeRootNode treeRoot = container.getTree();
        List<TreeNode> children = treeRoot.getChildren();
        assertTrue(children.size() > 0);

        // recursion depth should be 9, look at least 6 iterations into it
        for(int i = 0; i < 5; i++) {
            children = children.get(0).getChildren();
            assertTrue(children.size() > 0);
        }
    }




    private static BaseContainer getRandomTestRepo() {
        PodamFactory factory = new PodamFactoryImpl();
        BaseContainer repo = factory.manufacturePojo(BaseContainer.class);

        // podam does not support deep recursion, make it manually
        TreeNode deeperNode = factory.manufacturePojo(TreeNode.class);
        TreeNode evenDeeperNode = factory.manufacturePojo(TreeNode.class);

        deeperNode.getChildren().get(0).getChildren().get(0).addChild(evenDeeperNode);
        evenDeeperNode.addChild(deeperNode); // add loop
        repo.getTree().getChildren().get(0).getChildren().get(0).getChildren().get(0).addChild(deeperNode);

        ArrayClass aClass = factory.manufacturePojo(ArrayClass.class);
        repo.setReferenceToArrayClass(aClass);
        return repo;
    }
}
