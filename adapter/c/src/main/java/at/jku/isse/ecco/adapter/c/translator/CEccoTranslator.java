package at.jku.isse.ecco.adapter.c.translator;

import at.jku.isse.ecco.adapter.c.data.AbstractArtifactData;
import at.jku.isse.ecco.adapter.c.data.FunctionArtifactData;
import at.jku.isse.ecco.adapter.c.data.LineArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.featuretrace.FeatureTrace;
import at.jku.isse.ecco.featuretrace.parser.VevosCondition;
import at.jku.isse.ecco.featuretrace.parser.VevosFileConditionContainer;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.util.Location;

import java.nio.file.Path;
import java.util.*;

public class CEccoTranslator {

    private List<FunctionStructure> functionStructures;
    private String[] codeLines;
    private EntityFactory entityFactory;
    private VevosFileConditionContainer fileConditionContainer;
    private Path path;

    public CEccoTranslator(String[] codeLines,
                           EntityFactory entityFactory,
                           VevosFileConditionContainer fileConditionContainer,
                           Path path){
        this.codeLines = codeLines;
        this.entityFactory = entityFactory;
        this.functionStructures = new LinkedList<>();
        this.fileConditionContainer = fileConditionContainer;
        this.path = path;
    }

    public void addFunctionStructure(int start, int end, String functionSignature){
        this.functionStructures.add(new FunctionStructure(start, end, functionSignature));
    }

    public Node.Op createProgramNode(){
        this.sortFunctionStructures();

        // this node is necessary to make the functions and lines ordered
        AbstractArtifactData artifactData = new AbstractArtifactData("Ordering Artifact");
        Node.Op orderingNode = this.entityFactory.createOrderedNode(artifactData);


        int startLine = 1;
        for(FunctionStructure functionStructure : this.functionStructures){
            this.addLineNodes(orderingNode, startLine, functionStructure.startLine() - 1);
            Node.Op functionNode = this.createFunctionNode(functionStructure);
            orderingNode.addChild(functionNode);
            startLine = functionStructure.endLine() + 1;
        }

        if (this.functionStructures.size() > 0) {
            this.addLineNodes(orderingNode, startLine, this.codeLines.length);
        }

        return orderingNode;
    }

    private void sortFunctionStructures(){
        Comparator<FunctionStructure> compareByStart = Comparator.comparingInt(FunctionStructure::startLine);
        this.functionStructures.sort(compareByStart);
    }

    private void addLineNodes(Node.Op parentNode, int startLine, int endLine){
        for(int i = startLine; i <= endLine; i++){
            String codeLine = this.codeLines[i - 1];

            if (codeLine.isEmpty()){
                continue;
            }

            Artifact.Op<LineArtifactData> lineArtifactData = this.entityFactory.createArtifact(new LineArtifactData(codeLine));

            // TODO: implement location as node property
            Location location = new Location(i, i, this.path);
            Node.Op lineNode = this.createNodeWithLocation((Artifact.Op) lineArtifactData, location);
            //Node.Op lineNode = this.entityFactory.createNode(lineArtifactData);

            this.checkForFeatureTrace(i, lineNode);
            parentNode.addChild(lineNode);
        }
    }

    private Node.Op createFunctionNode(FunctionStructure functionStructure){
        Artifact.Op<FunctionArtifactData> data = this.entityFactory.createArtifact(new FunctionArtifactData(functionStructure.functionSignature()));

        //Location location = new Location(functionStructure.startLine(), functionStructure.endLine(), this.path);
        //Node.Op functionNode = this.createOrderedNodeWithLocation((Artifact.Op) data, location);
        Node.Op functionNode = this.entityFactory.createOrderedNode(data);

        this.addLineNodes(functionNode, functionStructure.startLine(), functionStructure.endLine());
        //this.checkForFeatureTrace(functionStructure, functionNode);
        return functionNode;
    }

    private Node.Op createNodeWithLocation(Artifact.Op artifact, Location location){
        Node.Op node = this.entityFactory.createNode(artifact);
        node.setLocation(location);
        return node;
    }

    private Node.Op createOrderedNodeWithLocation(Artifact.Op artifact, Location location){
        Node.Op node = this.entityFactory.createOrderedNode(artifact);
        node.setLocation(location);
        return node;
    }

    private void checkForFeatureTrace(FunctionStructure functionStructure, Node.Op node){
        if (this.fileConditionContainer == null){ return; }
        Collection<VevosCondition> matchingConditions = this.fileConditionContainer.getMatchingPresenceConditions(
                functionStructure.startLine(), functionStructure.endLine());
        for(VevosCondition condition : matchingConditions){
            FeatureTrace nodeTrace = node.getFeatureTrace();
            nodeTrace.buildUserConditionConjunction(condition.getConditionString());
        }
    }

    private void checkForFeatureTrace(int lineNumber, Node.Op node){
        if (this.fileConditionContainer == null){ return; }
        Collection<VevosCondition> matchingConditions = this.fileConditionContainer.getMatchingPresenceConditions(lineNumber, lineNumber);
        for(VevosCondition condition : matchingConditions){
            FeatureTrace nodeTrace = node.getFeatureTrace();
            nodeTrace.buildUserConditionConjunction(condition.getConditionString());
        }
    }
}
