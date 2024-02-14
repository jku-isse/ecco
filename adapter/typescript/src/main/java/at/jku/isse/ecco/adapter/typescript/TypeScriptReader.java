package at.jku.isse.ecco.adapter.typescript;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.typescript.data.*;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

public class TypeScriptReader implements ArtifactReader<Path, Set<Node.Op>> {

    private final EntityFactory entityFactory;
    private static final Logger LOGGER = Logger.getLogger(TypeScriptReader.class.getName());

    @Inject
    public TypeScriptReader(EntityFactory entityFactory) {
        checkNotNull(entityFactory);

        this.entityFactory = entityFactory;
    }

    @Override
    public String getPluginId() {
        return TypeScriptPlugin.class.getName();
    }

    private static final Map<Integer, String[]> prioritizedPatterns;

    static {
        prioritizedPatterns = new HashMap<>();
        prioritizedPatterns.put(1, new String[]{"**.ts"});
    }

    @Override
    public Map<Integer, String[]> getPrioritizedPatterns() {
        return Collections.unmodifiableMap(prioritizedPatterns);
    }

    @Override
    public Set<Node.Op> read(Path[] input) {
        return this.read(Paths.get("."), input);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<Node.Op> read(Path base, Path[] input) {
        Set<Node.Op> nodes = new HashSet<>();
        for (Path path : input) {
            Path resolvedPath = base.resolve(path);
            Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
            Node.Op pluginNode = this.entityFactory.createOrderedNode(pluginArtifact);
            nodes.add(pluginNode);
            try {
                HashMap<String, Object> br = new TypeScriptParser().parse(resolvedPath);
                ArrayList<HashMap<String, Object>> stat = (ArrayList<HashMap<String, Object>>) br.get("statements");
                for (HashMap<String, Object> stringObjectHashMap : stat) {
                    pluginNode.addChild(makeNode(stringObjectHashMap));
                }
                Map<String, Object> eofToken = (Map<String, Object>) br.get("endOfFileToken");
                String trailingComment = (String) eofToken.get("fullText");
                Artifact.Op<LeafArtifactData> endComment = this.entityFactory.createArtifact(new LeafArtifactData(trailingComment));
                pluginNode.addChild(this.entityFactory.createNode(endComment));
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE,"Problem reading parsed TypeScript : " + e.getMessage());
                throw new RuntimeException(e);
            }

        }
        return nodes;
    }

    @SuppressWarnings("unchecked")
    private Node.Op makeNode(HashMap<String, Object> currNode) {
        Node.Op node;
        String kind = (String) currNode.get("kind");
        String text, endText;
        switch (kind) {
            case "FirstStatement":
                HashMap<String,Object> declarationList = (HashMap<String, Object>) currNode.get("declarationList");
                ArrayList<HashMap<String,Object>> decls = (ArrayList<HashMap<String, Object>>) declarationList.get("declarations");
                text = getLeadingText(currNode,decls.get(0));
                StringBuilder sb = new StringBuilder(text);
                for (HashMap<String, Object> decl : decls){
                    HashMap<String, Object> name = (HashMap<String, Object>) decl.get("name");
                    sb.append((String) name.get("escapedText"));
                }
                var variable = new VariableAssignmentData(text);
                variable.setId(sb.toString());
                Artifact.Op<VariableAssignmentData> vData = this.entityFactory.createArtifact(variable);
                Node.Op vNode = this.entityFactory.createNode(vData);
                for (HashMap<String, Object> decl : decls) {
                    var init = (HashMap<String,Object>) decl.get("initializer");
                    if (!init.get("kind").equals("ArrowFunction")){
                        Artifact.Op<LeafArtifactData> line = this.entityFactory.createArtifact(new LeafArtifactData((String) decl.get("nodeText")));
                        node = this.entityFactory.createOrderedNode(line);
                    } else {
                        var name = this.getLeadingText(decl,init);
                        var body = (HashMap<String,Object>) init.get("body");
                        int parStart = (Integer) init.get("pos");
                        int kidStart = (Integer) body.get("pos");
                        var arrowParameters = ((String) init.get("fullText")).substring(0,kidStart-parStart);
                        Artifact.Op<ArrowFunctionArtifactData> arrow = this.entityFactory.createArtifact(new ArrowFunctionArtifactData(name + arrowParameters));
                        node = this.entityFactory.createOrderedNode(arrow);
                        node.addChild(makeNode(body));
                    }
                    vNode.addChild(node);
                }
                node = vNode;
                break;
            case "EnumDeclaration":
                var eMembers = (ArrayList<HashMap<String,Object>>) currNode.get("members");
                text = !eMembers.isEmpty() ? this.getLeadingText(currNode,eMembers.get(0)) : this.getLeadingText(currNode,null);
                var enu = new EnumArtifactData(text);
                Artifact.Op<EnumArtifactData> eData = this.entityFactory.createArtifact(enu);
                Node.Op eNode = this.entityFactory.createNode(eData);
                eMembers.forEach(x-> eNode.addChild(this.makeNode(x)));
                var lastEnumEntry = eMembers.get(eMembers.size()-1);
                endText = this.getTrailingText(currNode,lastEnumEntry);
                enu.setTrailingComment(endText);
                node = eNode;
                break;
            case "SwitchStatement":
                var caseBlock = (HashMap<String,Object>)currNode.get("caseBlock");
                var caseClauses = (ArrayList<HashMap<String,Object>>) caseBlock.get("clauses");
                text = !caseClauses.isEmpty() ? this.getLeadingText(currNode,caseClauses.get(0)) : this.getLeadingText(currNode,null);
                var switchData = new SwitchBlockArtifactData(text);
                Artifact.Op<SwitchBlockArtifactData> switchOp = this.entityFactory.createArtifact(switchData);
                Node.Op swNode = this.entityFactory.createNode(switchOp);
                caseClauses.forEach(x-> swNode.addChild(this.makeNode(x)));
                var lastSwitchEntry = caseClauses.get(caseClauses.size()-1);
                endText = this.getTrailingText(currNode,lastSwitchEntry);
                switchData.setTrailingComment(endText);
                node = swNode;
                break;
            case "IfStatement":
                HashMap<String, Object> then = (HashMap<String, Object>) currNode.get("thenStatement");
                text = getLeadingText(currNode, then);
                Artifact.Op<IfBlockArtifactData> ifArtifact = this.entityFactory.createArtifact(new IfBlockArtifactData(text));
                node = this.entityFactory.createOrderedNode(ifArtifact);
                node.addChild(makeNode(then));
                HashMap<String, Object> elseBlock = (HashMap<String, Object>) currNode.get("elseStatement");
                if (elseBlock != null) node.addChild(makeNode(elseBlock));
                break;
            case "CaseClause" :
            case "Block":
                ArrayList<HashMap<String, Object>> stats = (ArrayList<HashMap<String, Object>>) currNode.get("statements");
                text = !stats.isEmpty() ? getLeadingText(currNode,stats.get(0)) : getLeadingText(currNode,null);
                var blockData = new BlockArtifactData(text);
                Artifact.Op<BlockArtifactData> block = this.entityFactory.createArtifact(blockData);
                Node.Op finalNode = this.entityFactory.createOrderedNode(block);
                stats.forEach(x -> finalNode.addChild(this.makeNode(x)));
                var lastChild = stats.get(stats.size()-1);
                endText = this.getTrailingText(currNode,lastChild);
                blockData.setTrailingComment(endText);
                node = finalNode;
                break;
            case "ClassDeclaration":
                ArrayList<HashMap<String, Object>> members = (ArrayList<HashMap<String, Object>>) currNode.get("members");
                text = !members.isEmpty() ? getLeadingText(currNode,members.get(0)) : getLeadingText(currNode,null);
                Artifact.Op<ClassArtifactData> clazz = this.entityFactory.createArtifact(new ClassArtifactData(text));
                Node.Op finalNode1 = this.entityFactory.createOrderedNode(clazz);
                members.forEach(x -> finalNode1.addChild(this.makeNode(x)));
                node = finalNode1;
                break;
            case "MethodDeclaration","FunctionDeclaration":
                HashMap<String, Object> body = (HashMap<String,Object>) currNode.get("body");
                text = getLeadingText(currNode,body);
                Artifact.Op<FunctionArtifactData> fun = this.entityFactory.createArtifact(new FunctionArtifactData(text));
                node = this.entityFactory.createOrderedNode(fun);
                ArrayList<HashMap<String,Object>> modifiers = (ArrayList<HashMap<String, Object>>) currNode.get("modifiers");
                if (modifiers != null) {
                    if (modifiers.stream().anyMatch(x -> x.get("nodeText").equals("abstract"))) {
                        break;
                    }
                }
                node.addChild(this.makeNode(body));
                break;
            case "ForStatement", "ForInStatement", "ForOfStatement", "WhileStatement" :
                HashMap<String, Object> statement = (HashMap<String,Object>) currNode.get("statement");
                text = getLeadingText(currNode,statement);
                Artifact.Op<LoopArtifactData> fLoop = this.entityFactory.createArtifact(new LoopArtifactData(text));
                node = this.entityFactory.createOrderedNode(fLoop);
                node.addChild(this.makeNode(statement));
                break;
            case "DoStatement" :
                HashMap<String, Object> doStatement = (HashMap<String,Object>) currNode.get("statement");
                text = getLeadingText(currNode,doStatement);
                endText = getTrailingText(currNode,doStatement);
                Artifact.Op<DoBlockArtifactData> doLoop = this.entityFactory.createArtifact(new DoBlockArtifactData(endText));
                doLoop.getData().setTrailingComment(endText);
                doLoop.getData().setLeadingText(text);
                node = this.entityFactory.createOrderedNode(doLoop);
                node.addChild(this.makeNode(doStatement));
                break;
            default:
                Artifact.Op<LeafArtifactData> line = this.entityFactory.createArtifact(new LeafArtifactData((String) currNode.get("nodeText")));
                node = this.entityFactory.createOrderedNode(line);
                break;
        }
        insertTrivia(currNode, node);
        return node;
    }

    private String getLeadingText(Map<String, Object> par, Map<String, Object> kid) {
        int parStart = (Integer) par.get("pos");
        int trivia = (Integer) par.get("triviaWidth");
        String text = (String) par.get("fullText");
        if (kid != null) {
            int kidStart = (Integer) kid.get("pos");
            return text.substring(trivia, kidStart - parStart);
        } else {
            return text.substring(trivia);
        }
    }

    private String getTrailingText(Map<String, Object> par, Map<String, Object> kid){
        int parStart = (Integer) par.get("pos");
        String text = (String) par.get("fullText");
        if (kid != null) {
            int kidEnd = (Integer) kid.get("end");
            return text.substring(kidEnd - parStart);
        } else {
            return "";
        }
    }

    private void insertTrivia(Map<String, Object> curr, Node node) {
        var triviaWidth = (Integer) curr.get("triviaWidth");
        if (triviaWidth != null && triviaWidth > 0) {
            String ft = (String) curr.get("fullText");
            String trivia = ft.substring(0, triviaWidth);
            AbstractArtifactData data = (AbstractArtifactData) node.getArtifact().getData();
            data.setLeadingComment(trivia);
        }
    }


    private final Collection<ReadListener> listeners = new ArrayList<>();

    @Override
    public void addListener(ReadListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(ReadListener listener) {
        this.listeners.remove(listener);
    }

}
