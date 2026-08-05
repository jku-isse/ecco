package at.jku.isse.ecco.web.domain.repository;

import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.service.EccoService;
import at.jku.isse.ecco.web.domain.model.ArtefactGraphModel;
import at.jku.isse.ecco.web.domain.model.ArtefactTreeModel;
import at.jku.isse.ecco.web.domain.model.ArtefactTreeNodeModel;
import at.jku.isse.ecco.web.domain.model.Artefactgraph.ArtefactgraphNode;
import at.jku.isse.ecco.web.domain.model.AssociationModel;
import at.jku.isse.ecco.web.rest.EccoApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ArtefactRepository's real logic is the artifact visualization graph: build a full "backend" graph
 * from the composed artifact tree once, then derive a filtered "frontend" graph that collapses any
 * node with more than maxChildCount children into a single bigger-symbol placeholder, and expand one
 * level at a time on click (getUpdatedFrontendGraphByNodeID). Tested against a real repository (core
 * + two branches gives a tree with actual depth) rather than mocks, since the algorithm is defined in
 * terms of walking a real Node tree.
 */
@Timeout(30)
public class ArtefactRepositoryTest {

    private Path workDir;
    private EccoApplication application;

    @BeforeEach
    public void setUp() throws IOException {
        workDir = Files.createTempDirectory("artefact-repository-test");
        application = new EccoApplication();
        application.init(workDir.toString());

        commitVariant("core", "Core");
        commitVariant("branchA", "Core, BranchA", "a.txt");
        commitVariant("branchB", "Core, BranchB", "b.txt");
    }

    @AfterEach
    public void tearDown() {
        application.close();
    }

    private void commitVariant(String name, String configuration, String... extraFileNames) throws IOException {
        Path variantDir = workDir.resolve(name);
        Files.createDirectories(variantDir);
        Files.writeString(variantDir.resolve("core.txt"), "core\n");
        for (String fileName : extraFileNames) {
            Files.writeString(variantDir.resolve(fileName), fileName + "\n");
        }
        EccoService service = application.getEccoService();
        service.setBaseDir(variantDir);
        service.commit(name, configuration);
    }

    /**
     * Mirrors Trees.countArtifacts()'s exact rule (see base's Trees.java): only nodes with BOTH a
     * non-null artifact AND isUnique()==true count. ArtefactTreeNodeModel carries both pieces of
     * information (artefactData, isUnique()), so this reproduces the same count from the web layer's
     * model instead of the real Node tree.
     */
    private int countArtifacts(ArtefactTreeNodeModel node) {
        int count = (node.getArtefactData() != null && node.isUnique()) ? 1 : 0;
        for (ArtefactTreeNodeModel child : node.getChildNodes()) {
            count += countArtifacts(child);
        }
        return count;
    }

    @Test
    public void getArtifactsByAssociationCoversExactlyTheGivenAssociationsArtifacts() {
        ArtefactRepository artefactRepository = new ArtefactRepository(application);
        Collection<? extends Association> allAssociations = application.getEccoService().getRepository().getAssociations();
        Association firstAssociation = allAssociations.iterator().next();
        AssociationModel[] selection = {new AssociationModel(firstAssociation.getId(), null, null, null)};

        ArtefactTreeModel tree = artefactRepository.getArtifactsByAssociation(selection);

        assertEquals(firstAssociation.getRootNode().countArtifacts(), countArtifacts(tree.getRootNode()));
    }

    @Test
    public void getArtifactsByAssociationOfAnEmptySelectionProducesAnEmptyTree() {
        ArtefactRepository artefactRepository = new ArtefactRepository(application);

        ArtefactTreeModel tree = artefactRepository.getArtifactsByAssociation(new AssociationModel[0]);

        assertEquals(0, countArtifacts(tree.getRootNode()));
        assertTrue(tree.getRootNode().getChildNodes().isEmpty());
    }

    @Test
    public void getArtefactgraphFromAllAssociationsWithALargeMaxChildCountCollapsesNothing() {
        ArtefactRepository artefactRepository = new ArtefactRepository(application);

        ArtefactGraphModel frontend = artefactRepository.getArtefactgraphFromAllAssociations(1000);
        ArtefactGraphModel backend = application.getBackendGraph();

        assertNotNull(backend);
        assertEquals(backend.getArtefactgraphNodeList().size(), frontend.getArtefactgraphNodeList().size());
        assertEquals(backend.getArtefactgraphEdgeList().size(), frontend.getArtefactgraphEdgeList().size());
        for (ArtefactgraphNode node : frontend.getArtefactgraphNodeList()) {
            assertEquals(ArtefactgraphNode.DEFAULT_NODE_SYMBOLSIZE, node.getSymbolSize());
        }
    }

    @Test
    public void getArtefactgraphFromAllAssociationsComputesTheBackendGraphOnceAndReusesItAcrossCalls() {
        ArtefactRepository artefactRepository = new ArtefactRepository(application);

        artefactRepository.getArtefactgraphFromAllAssociations(1000);
        ArtefactGraphModel backendAfterFirstCall = application.getBackendGraph();

        artefactRepository.getArtefactgraphFromAllAssociations(0);
        ArtefactGraphModel backendAfterSecondCall = application.getBackendGraph();

        assertSame(backendAfterFirstCall, backendAfterSecondCall,
                "backendGraph is only built when application.getBackendGraph() is still null - later calls must reuse it, not recompute");
    }

    /**
     * With maxChildCount=0, every node with at least one child qualifies for collapse - and since
     * collapsing a node removes its ENTIRE descendant subtree (not just its direct children), and the
     * composition root itself always has children for a repository with more than a trivial single
     * file, the whole tree collapses down to just that one root node.
     */
    @Test
    public void getArtefactgraphFromAllAssociationsWithZeroMaxChildCountCollapsesTheWholeTreeToTheRoot() {
        ArtefactRepository artefactRepository = new ArtefactRepository(application);

        ArtefactGraphModel backend = null;
        ArtefactGraphModel frontend = artefactRepository.getArtefactgraphFromAllAssociations(0);
        backend = application.getBackendGraph();

        assertTrue(backend.getArtefactgraphNodeList().size() > 1, "sanity check: the real tree has more than just a root node");
        assertEquals(1, frontend.getArtefactgraphNodeList().size());
        assertEquals(0, frontend.getArtefactgraphEdgeList().size());
        assertEquals(ArtefactgraphNode.DEFAULT_NODE_SYMBOLSIZE * ArtefactgraphNode.SYMBOLSIZE_MULTIPLIER,
                frontend.getArtefactgraphNodeList().get(0).getSymbolSize());
    }

    @Test
    public void getUpdatedFrontendGraphByNodeIDExpandsOneLevelWhenTheClickedNodeWasCollapsed() {
        ArtefactRepository artefactRepository = new ArtefactRepository(application);
        artefactRepository.getArtefactgraphFromAllAssociations(0);
        ArtefactGraphModel backend = application.getBackendGraph();
        String rootId = application.getFrontendGraph().getArtefactgraphNodeList().get(0).getId();

        ArtefactGraphModel expanded = artefactRepository.getUpdatedFrontendGraphByNodeID(rootId, 0);

        ArtefactgraphNode rootAfterExpand = expanded.getArtefactgraphNodeList().stream()
                .filter(n -> n.getId().equals(rootId)).findFirst().orElseThrow();
        assertEquals(ArtefactgraphNode.DEFAULT_NODE_SYMBOLSIZE, rootAfterExpand.getSymbolSize(),
                "clicking an already-collapsed node resets its own size back to default");

        long rootDirectChildrenInBackend = backend.getArtefactgraphEdgeList().stream()
                .filter(e -> e.getSource().equals(rootId)).count();
        assertEquals(1 + rootDirectChildrenInBackend, expanded.getArtefactgraphNodeList().size(),
                "the root plus exactly its direct backend children should now be shown");
        assertEquals(rootDirectChildrenInBackend, expanded.getArtefactgraphEdgeList().size());
    }

    /**
     * Real gap found by reading the code, confirmed here rather than assumed: getUpdatedFrontendGraphByNodeID
     * accepts a maxChildCount parameter but never reads it anywhere in its body (only
     * generateFrontendGraphWithFilter, called from the OTHER method, actually uses a maxChildCount) -
     * expanding a node always adds back exactly its direct children, regardless of what's passed here.
     * Not fixed, since the intended behavior (should expansion re-apply the collapse threshold to the
     * newly revealed children?) isn't specified anywhere and would be a guess.
     */
    @Test
    public void getUpdatedFrontendGraphByNodeIDIgnoresItsMaxChildCountParameter() {
        ArtefactRepository artefactRepository = new ArtefactRepository(application);
        artefactRepository.getArtefactgraphFromAllAssociations(0);
        String rootId = application.getFrontendGraph().getArtefactgraphNodeList().get(0).getId();

        ArtefactGraphModel expandedWithZero = artefactRepository.getUpdatedFrontendGraphByNodeID(rootId, 0);
        List<Integer> sizesWithZero = expandedWithZero.getArtefactgraphNodeList().stream()
                .map(ArtefactgraphNode::getSymbolSize).sorted().collect(Collectors.toList());

        // reset back to the fully-collapsed starting point and repeat with a wildly different maxChildCount
        artefactRepository.getArtefactgraphFromAllAssociations(0);
        ArtefactGraphModel expandedWithLarge = artefactRepository.getUpdatedFrontendGraphByNodeID(rootId, 999999);
        List<Integer> sizesWithLarge = expandedWithLarge.getArtefactgraphNodeList().stream()
                .map(ArtefactgraphNode::getSymbolSize).sorted().collect(Collectors.toList());

        assertEquals(expandedWithZero.getArtefactgraphNodeList().size(), expandedWithLarge.getArtefactgraphNodeList().size());
        assertEquals(sizesWithZero, sizesWithLarge,
                "maxChildCount=0 and maxChildCount=999999 must produce identical results, since the parameter is dead");
    }
}
