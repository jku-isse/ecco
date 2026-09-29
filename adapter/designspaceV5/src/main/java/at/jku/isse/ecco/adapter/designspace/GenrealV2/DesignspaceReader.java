package at.jku.isse.ecco.adapter.designspace.GenrealV2;

import at.jku.isse.designspace.commons.Cardinality;
import at.jku.isse.designspace.commons.Key;
import at.jku.isse.designspace.core.model.*;
import at.jku.isse.designspace.domains.Java8;
import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.designspace.DesignSpacePlugin;
import at.jku.isse.ecco.adapter.designspace.GenrealV2.artefacts.ReferenceArtefact;
import at.jku.isse.ecco.adapter.designspace.GenrealV2.artefacts.WorkspaceElementArtefact;
import at.jku.isse.ecco.adapter.designspace.GenrealV2.artefacts.PropTypeArtefact;
import at.jku.isse.ecco.adapter.designspace.util.Filter;
import at.jku.isse.ecco.adapter.designspace.artifact.StringArtefact;
import at.jku.isse.ecco.adapter.designspace.artifact.value.SimpleValueArtifact;
import at.jku.isse.ecco.adapter.designspace.util.DesignSpaceInfo;
import at.jku.isse.ecco.adapter.designspace.util.Logger;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static at.jku.isse.ecco.adapter.designspace.DesignSpaceModule.generalAdpaterV2String;

public class DesignspaceReader implements ArtifactReader<DesignSpaceInfo, Set<Node.Op>> {
    private final EntityFactory entityFactory;
    private final List<ReadListener> listeners = new ArrayList<>();
    private final Set<at.jku.isse.designspace.core.model.WorkspaceElement> processedElements = new HashSet<>();

    @Inject
    public DesignspaceReader(EntityFactory entityFactory) {
        this.entityFactory = entityFactory;

    }

    @Override
    public String getPluginId() {
        return new DesignSpacePlugin().getPluginId();
    }

    @Override
    public Map<Integer, String[]> getPrioritizedPatterns() {
        return Map.of();
    }

    private Workspace workspace;


    @Override
    public Set<Node.Op> read(DesignSpaceInfo info, DesignSpaceInfo[] input) {

        processedElements.clear();
        initStarterTypes();
        info.checkIfInfoValid();

        Node.Op pluginNode;
        try {
            if (info.debugOptions().javaConsole()) System.out.println("javareader commit");
            workspace = info.workspace();
            Folder commitFolder = info.folder();
            TreeLogger.debugOptions = info.debugOptions();

            pluginNode = entityFactory.createOrderedNode(new StringArtefact("plugin Node Designspace"));
            handleProject(commitFolder, pluginNode);

            if (info.debugOptions().javaConsole()) {
                System.out.println("\n=== GENERATED TREE ===");
                printNodeTreeToConsole(pluginNode, 10);
                printNodeTreeToFile(pluginNode, "CommitData.txt");
                System.out.println("==========================\n");
            }

            if (info.debugOptions().javaLogFile()) {
                printNodeTreeToFile(pluginNode, "CommitData.txt");
            }


        } finally {
            processedElements.clear();
        }


        return Set.of(pluginNode);
    }

    @Override
    public Set<Node.Op> read(DesignSpaceInfo[] input) {
        return read(input[0], input);
    }

    @Override
    public void addListener(ReadListener listener) {

    }

    @Override
    public void removeListener(ReadListener listener) {

    }



    public Set<WorkspaceElementType> starterTyps = new HashSet<>();
    public void initStarterTypes(){
        starterTyps.clear();
        starterTyps.add(Java8.JAVA_PROJECT);
    }
    private  Set<WorkspaceElement> getStarterElements(Workspace workspace,Folder folder) {
        return folder.getWorkspaceElementContents(workspace).stream().filter(workspaceElement -> starterTyps.contains(workspaceElement.getInstanceOf())).collect(Collectors.toSet());

    }

    private void handleProject(Folder folder, Node.Op pluginNode) {
        Set<at.jku.isse.designspace.core.model.WorkspaceElement> starterElements = getStarterElements(workspace, folder);
        for (at.jku.isse.designspace.core.model.WorkspaceElement project : starterElements) {
            Node.Op projectNode = handleWorkspaceElement(project);
            pluginNode.addChild(projectNode);
        }
        Set<WorkspaceElement> remainingElements = new HashSet<>(folder.getWorkspaceElementContents(workspace));
        remainingElements.removeAll(processedElements);
        // other  elements that have not been translated yet
        for(WorkspaceElement remaining : remainingElements ){

            if (processedElements.contains(remaining)) {
                continue;
            }
            Node.Op projectNode = handleWorkspaceElement(remaining);
            //remainingElements.removeAll(processedElements);
            pluginNode.addChild(projectNode);
        }


    }


    private Node.Op handleWorkspaceElement(at.jku.isse.designspace.core.model.WorkspaceElement element) {
        if (element == null) {
            System.err.println(" during the Read process for java elements one child element is null");
            return null;
        }



        if (processedElements.contains(element) ) {
            return entityFactory.createNode(new ReferenceArtefact(element.getName(), element.getInstanceOf().getQualifiedName()));
        }
        Node.Op ElementNode;
        ElementNode = entityFactory.createOrderedNode(new WorkspaceElementArtefact(element.getName(), element.getInstanceOf().getQualifiedName()));
        processedElements.add(element);

        for (WorkspacePropertyType propType : element.getInstanceOf().getAllPropertyTypes()) {

            //System.out.println("for element " + element.getName() + "processing property " + propType.getName() + " isopposed" + propType.isOpposed() + " iscontained" + propType.isContained() +" iscontainer" + propType.isContainer());


            if (!Filter.shouldProcessProperty(propType)) {
                continue;
            }

            if (element.getOrCreateProperty(propType) == null) continue;
            if (propType.isContained()) continue;
            if (element.getOrCreateProperty(propType).getRaw() == null) continue; // skip empty

            Node.Op propTypeNode = entityFactory.createOrderedNode(new PropTypeArtefact(propType.getQualifiedName(), propType.getCardinality()));

            if (propType.getCardinality().equals(Cardinality.SINGLE)) {
                addValueNode(propTypeNode, element.getOrCreateProperty(propType).get());
            } else if (propType.getCardinality().equals(Cardinality.MAP)) {

                element.getMap(propType).forEach((key, value) -> {
                    Node.Op keyNode = entityFactory.createNode(new StringArtefact(((Key) key).getName()));
                    addValueNode(keyNode, value);
                    propTypeNode.addChild(keyNode);
                });

            } else {
                Collection<Object> values = element.getCollection(propType);
                for (Object value : values) {
                    addValueNode(propTypeNode, value);
                }

            }
            ElementNode.addChild(propTypeNode);
        }

        return ElementNode;
    }


    void addValueNode(Node.Op propertyNode, Object value) {
        if (value == null) return;
        if (value instanceof at.jku.isse.designspace.core.model.WorkspaceElement instanceValue) {
            Node.Op element = handleWorkspaceElement(instanceValue);
            if (element != null) {
                propertyNode.addChild(element);
            }
            Logger.log("Debug Reader AddValueNode : Value= Warning original id was null for " + instanceValue.getId());
        } else {
            propertyNode.addChild(entityFactory.createNode(new SimpleValueArtifact<>(value)));
            Logger.log("Debug Reader AddValueNode :  Value= " + value.toString());

        }

    }


    public void printNodeTreeToFile(Node node, String filePath) {
        Path path = Paths.get(filePath);

        if (node == null) {
            deleteLogFile(path);
            return;
        }
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("┌── " + formatNodeLabel(node));
            List<? extends Node> children = node.getChildren();

            for (int i = 0; i < children.size(); i++) {
                printNodeTreeRecursive(children.get(i), "", i == children.size() - 1, writer, 1, Integer.MAX_VALUE);
            }

            if (writer.checkError()) {
                throw new IOException("Stream error encountered while writing tree output.");
            }

            System.out.println("AST Tree saved successfully to: " + filePath);

        } catch (Exception e) {
            System.err.println("Failed to generate AST tree (" + e.getMessage() + "). Cleaning up old/incomplete log file...");
            deleteLogFile(path);
        }
    }

    private void deleteLogFile(Path path) {
        try {
            boolean deleted = Files.deleteIfExists(path);
            if (deleted) {
                System.out.println("Deleted invalid/previous log file: " + path.getFileName());
            }
        } catch (IOException e) {
            System.err.println("Could not delete log file: " + e.getMessage());
        }
    }

    public void printNodeTreeToConsole(Node node, int maxDepth) {
        if (node == null) return;
        PrintWriter writer = new PrintWriter(System.out);
        writer.println("┌── " + formatNodeLabel(node));
        List<? extends Node> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            printNodeTreeRecursive(children.get(i), "", i == children.size() - 1, writer, 1, maxDepth);
        }
        writer.flush(); // Ensure console output is flushed
    }

    private void printNodeTreeRecursive(Node node, String indent, boolean isLast, PrintWriter writer, int currentDepth, int maxDepth) {
        String marker = isLast ? "└── " : "├── ";

        writer.println(indent + marker + formatNodeLabel(node));

        String childIndent = indent + (isLast ? "    " : "│   ");
        List<? extends Node> children = node.getChildren();

        if (currentDepth >= maxDepth && !children.isEmpty()) {
            writer.println(childIndent + "└── ... [" + children.size() + " children hidden]");
            return;
        }

        for (int i = 0; i < children.size(); i++) {
            printNodeTreeRecursive(children.get(i), childIndent, i == children.size() - 1, writer, currentDepth + 1, maxDepth);
        }
    }

    private String formatNodeLabel(Node node) {
        Object artifact = node.getArtifact().getData();

        if (artifact instanceof WorkspaceElementArtefact workspaceElementArtefact) {
            return "WorkSpaceElement [" + workspaceElementArtefact.typeName + "] name: '" + workspaceElementArtefact.name + "'";
        } else if (artifact instanceof PropTypeArtefact propTypeArtefact) {
            return "Property: " + propTypeArtefact.qualifiedName + " (" + propTypeArtefact.cardinality + ")";
        } else if (artifact instanceof StringArtefact stringArtefact) {
            return "StringArtefact: '" + stringArtefact.getValue() + "'";
        } else if (artifact instanceof SimpleValueArtifact<?> simpleValue) {
            return "Value: " + simpleValue.getValue();
        } else if (artifact != null) {
            return artifact.getClass().getSimpleName() + ": " + artifact.toString();
        }

        return "NullArtifact";
    }

    @Override
    public String toString() {
        return generalAdpaterV2String;
    }
}
