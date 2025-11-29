package at.jku.isse.ecco.adapter.c.translator;

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
import java.util.logging.Logger;

public class CEccoTranslator {

    private List<FunctionStructure> functionStructures;
    private String[] codeLines;
    private EntityFactory entityFactory;
    private VevosFileConditionContainer fileConditionContainer;
    private Path path;
    private String configuration;
    private String gitCommitHash;
    private int gitCommitIndex;

    public CEccoTranslator(String[] codeLines,
                           EntityFactory entityFactory,
                           VevosFileConditionContainer fileConditionContainer,
                           Path path,
                           String configuration){
        this.codeLines = codeLines;
        this.entityFactory = entityFactory;
        this.functionStructures = new LinkedList<>();
        this.fileConditionContainer = fileConditionContainer;
        this.path = path;
        this.configuration = configuration;
        this.gitCommitHash = null;
        this.gitCommitIndex = -1;
    }

    public void setGitCommitHash(String gitCommitHash) {
        this.gitCommitHash = gitCommitHash;
    }

    public void setGitCommitIndex(int gitCommitIndex) {
        this.gitCommitIndex = gitCommitIndex;
    }

    public String getGitCommitHash() {
        return gitCommitHash;
    }

    public int getGitCommitIndex() {
        return gitCommitIndex;
    }

    public void addFunctionStructure(int start, int end, String functionSignature){
        this.functionStructures.add(new FunctionStructure(start, end, functionSignature));
    }

    public void addChildrenToPluginNode(Node.Op pluginNode){
        this.sortFunctionStructures();
        int startLine = 1;

        for(FunctionStructure functionStructure : this.functionStructures){
            this.addLineNodes(pluginNode, startLine, functionStructure.startLine() - 1);
            Node.Op functionNode = this.createFunctionNode(functionStructure);
            pluginNode.addChild(functionNode);
            startLine = functionStructure.endLine() + 1;
        }

        this.addLineNodes(pluginNode, startLine, this.codeLines.length);
    }

    private void sortFunctionStructures(){
        Comparator<FunctionStructure> compareByStart = Comparator.comparingInt(FunctionStructure::startLine);
        this.functionStructures.sort(compareByStart);
    }

    private void addLineNodes(Node.Op parentNode, int startLine, int endLine){
        Location location = null;
        for(int i = startLine; i <= endLine; i++){
            String codeLine = this.codeLines[i - 1];

            if (codeLine.isEmpty()){
                continue;
            }

            Artifact.Op<LineArtifactData> lineArtifactData = this.entityFactory.createArtifact(new LineArtifactData(codeLine));
            location = new Location(i, i, this.path, this.configuration);
            location.setCommithash(this.gitCommitHash);
            location.setIndexOfCommit(this.gitCommitIndex);

            Node.Op lineNode = this.createNodeWithLocation(lineArtifactData, location);
            this.checkForFeatureTrace(i, lineNode);
            parentNode.addChild(lineNode);
        }
        if(location != null)
            Logger.getAnonymousLogger().info( location.getIndexOfCommit() + " - " +location.getEndLine() + " - " + location.getStartLine() + " - " + location.getCommithash());
    }

    private Node.Op createFunctionNode(FunctionStructure functionStructure){
        Artifact.Op<FunctionArtifactData> data = this.entityFactory.createArtifact(new FunctionArtifactData(functionStructure.functionSignature()));
        Node.Op functionNode = this.entityFactory.createOrderedNode(data);
        this.addLineNodes(functionNode, functionStructure.startLine(), functionStructure.endLine());
        return functionNode;
    }

    private Node.Op createNodeWithLocation(Artifact.Op artifact, Location location){
        Node.Op node = this.entityFactory.createNode(artifact);
        node.putProperty("Location", location);
        return node;
    }

    private void checkForFeatureTrace(int lineNumber, Node.Op node){
        if (this.fileConditionContainer == null){ return; }
        Collection<VevosCondition> matchingConditions = this.fileConditionContainer.getMatchingPresenceConditions(lineNumber, lineNumber);
        for(VevosCondition condition : matchingConditions){
            FeatureTrace nodeTrace = node.getFeatureTrace();
            nodeTrace.buildProactiveConditionConjunction(condition.getConditionString());
        }
    }
}
