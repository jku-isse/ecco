package at.jku.isse.ecco.adapter.designspace.GenrealV2;

import at.jku.isse.designspace.core.model.Folder;
import at.jku.isse.designspace.core.model.Workspace;
import at.jku.isse.designspace.core.model.WorkspaceElement;
import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.designspace.DesignSpacePlugin;
import at.jku.isse.ecco.adapter.designspace.GenrealV2.artefacts.WorkspaceElementArtefact;
import at.jku.isse.ecco.adapter.designspace.GenrealV2.refFixUp.RefFixUpInterFace;
import at.jku.isse.ecco.adapter.designspace.util.DesignSpaceInfo;
import at.jku.isse.ecco.adapter.designspace.util.WriterTypeManager;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;

import java.nio.file.Path;
import java.util.*;

import static at.jku.isse.ecco.adapter.designspace.DesignSpaceModule.generalAdpaterV2String;
import static at.jku.isse.ecco.adapter.designspace.DesignSpaceModule.javaAdpaterString;

public class DesignspaceWriter implements ArtifactWriter<Set<Node>, DesignSpaceInfo> {
    private final List<WriteListener> listeners = new ArrayList<>();
    public Workspace workspace;
    public Folder checkoutFolder;
    public WriterTypeManager writerTypeManager;

    public Set<RefFixUpInterFace> fixups = new HashSet<>();

    public Set< WorkspaceElement> createdElements = new HashSet<>();

    @Override
    public String getPluginId() {
        return new DesignSpacePlugin().getPluginId();
    }

    @Override
    public DesignSpaceInfo[] write(DesignSpaceInfo info, Set<Node> input) {

        fixups.clear();
        createdElements.clear();
        TreeLogger.reset();
        info.checkIfInfoValid();
        info.checkIfFolderIsReadyForCheckout();

        if (info.debugOptions().javaConsole()) System.out.println("java writer checkout");
        workspace = info.workspace();
        checkoutFolder = info.folder(); // workspace.its();
        TreeLogger.debugOptions = info.debugOptions();
        writerTypeManager = new WriterTypeManager(workspace);
        if (input.size() > 1) {
            System.err.println("checkout received multiple PluginNodes");
        }
        Node pluginNode = input.stream().findFirst().orElse(null);
        if (pluginNode == null) throw new EccoException("the Workspace writer received an empty Node set");
        try {


            for (Node node : pluginNode.getChildren()) {
                if (node.getArtifact().getData() instanceof WorkspaceElementArtefact starterElements) {
                   System.out.println("handeling starter element " + starterElements.name);
                    starterElements.build(node, this);

                }
            }

            fixups.forEach(fixup -> {fixup.fixUp(workspace,createdElements);});



            workspace.acceptAllChanges();
            workspace.conclude();
            writerTypeManager.newToOriginalId.forEach((newId, OldId) -> info.idMapper().putIds(newId, OldId));
        } catch (Exception e) {
            e.printStackTrace();

            //throw new RuntimeException(e);
        } finally {
            TreeLogger.reset();

        }


        listeners.forEach(listener -> listener.fileWriteEvent(Path.of(checkoutFolder.getQualifiedName()), this));
        return new DesignSpaceInfo[0];
    }


    @Override
    public DesignSpaceInfo[] write(Set<Node> input) {
        throw new RuntimeException("write(Set<Node> input) is not implemented ");
        //return new Pair[0];
    }

    @Override
    public void addListener(WriteListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(WriteListener listener) {
        listeners.remove(listener);
    }

    @Override
    public String toString() {
        return generalAdpaterV2String;
    }

}
