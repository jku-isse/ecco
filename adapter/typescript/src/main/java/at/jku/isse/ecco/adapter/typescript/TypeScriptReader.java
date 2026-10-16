package at.jku.isse.ecco.adapter.typescript;

import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.SourcePosition;
import at.jku.isse.ecco.adapter.view.RenderedSource;
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
    public String readContext(Path base) {
        // a file's tree depends on nothing but the file
        return "";
    }

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
                // what follows the last declaration, e.g. the ";" - used to be dropped, so every
                // checkout lost the semicolons of variable statements
                variable.setTrailingComment(getTrailingText(currNode, decls.get(decls.size() - 1)));
                Artifact.Op<VariableAssignmentData> vData = this.entityFactory.createArtifact(variable);
                // ordered, like every node whose children's order matters (also enums, switches):
                // unordered, a composed checkout could put them in any order
                Node.Op vNode = this.entityFactory.createOrderedNode(vData);
                for (HashMap<String, Object> decl : decls) {
                    var init = (HashMap<String,Object>) decl.get("initializer");
                    // no initializer: let x; or a declaration (declare const x: T;, .d.ts)
                    if (init == null || !init.get("kind").equals("ArrowFunction")){
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
                    // the writer separates declarations by ","; what followed it (e.g. the space in
                    // "a = 1, b = 2") is the next declaration's leading text
                    if (decl != decls.get(0))
                        insertTrivia(decl, node);
                    vNode.addChild(node);
                }
                node = vNode;
                break;
            case "PropertyDeclaration":
                var init = (HashMap<String,Object>) currNode.get("initializer");
                if (init == null || !init.get("kind").equals("ArrowFunction")){
                    Artifact.Op<LeafArtifactData> line = this.entityFactory.createArtifact(new LeafArtifactData((String) currNode.get("nodeText")));
                    node = this.entityFactory.createOrderedNode(line);
                } else {
                    var name = this.getLeadingText(currNode,init);
                    var body = (HashMap<String,Object>) init.get("body");
                    int parStart = (Integer) init.get("pos");
                    int kidStart = (Integer) body.get("pos");
                    var arrowParameters = ((String) init.get("fullText")).substring(0,kidStart-parStart);
                    Artifact.Op<ArrowFunctionArtifactData> arrow = this.entityFactory.createArtifact(new ArrowFunctionArtifactData(name + arrowParameters));
                    node = this.entityFactory.createOrderedNode(arrow);
                    node.addChild(makeNode(body));
                }
                break;
            case "EnumDeclaration":
                var eMembers = (ArrayList<HashMap<String,Object>>) currNode.get("members");
                text = !eMembers.isEmpty() ? this.getLeadingText(currNode,eMembers.get(0)) : this.getLeadingText(currNode,null);
                var enu = new EnumArtifactData(text);
                Artifact.Op<EnumArtifactData> eData = this.entityFactory.createArtifact(enu);
                Node.Op eNode = this.entityFactory.createOrderedNode(eData);
                eMembers.forEach(x-> eNode.addChild(this.makeNode(x)));
                // an empty enum ({}) is all leading text
                endText = !eMembers.isEmpty() ? this.getTrailingText(currNode, eMembers.get(eMembers.size() - 1)) : "";
                enu.setTrailingComment(endText);
                node = eNode;
                break;
            case "SwitchStatement":
                var caseBlock = (HashMap<String,Object>)currNode.get("caseBlock");
                var caseClauses = (ArrayList<HashMap<String,Object>>) caseBlock.get("clauses");
                text = !caseClauses.isEmpty() ? this.getLeadingText(currNode,caseClauses.get(0)) : this.getLeadingText(currNode,null);
                var switchData = new SwitchBlockArtifactData(text);
                Artifact.Op<SwitchBlockArtifactData> switchOp = this.entityFactory.createArtifact(switchData);
                Node.Op swNode = this.entityFactory.createOrderedNode(switchOp);
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
                // an empty block ({}) is all leading text
                endText = !stats.isEmpty() ? this.getTrailingText(currNode, stats.get(stats.size() - 1)) : "";
                blockData.setTrailingComment(endText);
                node = finalNode;
                break;
            case "ClassDeclaration":
                ArrayList<HashMap<String, Object>> members = (ArrayList<HashMap<String, Object>>) currNode.get("members");
                text = !members.isEmpty() ? getLeadingText(currNode,members.get(0)) : getLeadingText(currNode,null);
                var classData = new ClassArtifactData(text);
                // what closes the class as written (see ClassArtifactData#getClosing)
                classData.setTrailingComment(!members.isEmpty() ? getTrailingText(currNode, members.get(members.size() - 1)) : null);
                Artifact.Op<ClassArtifactData> clazz = this.entityFactory.createArtifact(classData);
                Node.Op finalNode1 = this.entityFactory.createOrderedNode(clazz);
                members.forEach(x -> finalNode1.addChild(this.makeNode(x)));
                node = finalNode1;
                break;
            case "MethodDeclaration","FunctionDeclaration":
                HashMap<String, Object> body = (HashMap<String,Object>) currNode.get("body");
                text = getLeadingText(currNode,body);
                Artifact.Op<FunctionArtifactData> fun = this.entityFactory.createArtifact(new FunctionArtifactData(text));
                node = this.entityFactory.createOrderedNode(fun);
                // no body: abstract, a declaration (declare, .d.ts) or an overload's signature - its text is all of it
                if (body != null) {
                    node.addChild(this.makeNode(body));
                }
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


	private static final java.util.regex.Pattern CALLABLE_NAME = java.util.regex.Pattern.compile("([A-Za-z_$][\\w$]*)\\s*(?:<[^>]*>)?\\s*(?:=\\s*(?:async\\s*)?(?:function\\s*)?)?\\(");
	private static final java.util.regex.Pattern DECLARED_NAME = java.util.regex.Pattern.compile("\\b(?:class|enum|interface)\\s+([A-Za-z_$][\\w$]*)");
	private static final java.util.regex.Pattern ASSIGNED_NAME = java.util.regex.Pattern.compile("([A-Za-z_$][\\w$]*)\\s*(?::[^=]*)?=");

	/**
	 * Classes, enums, functions, methods and arrow functions, nested ones named "Class.method". Lines
	 * from the rendered source, which is the file as read (TypeScriptWriter#render).
	 */
	@Override
	public List<SourcePosition> positions(Node fileNode) {
		RenderedSource rendered = TypeScriptWriter.render(fileNode);
		String text = rendered.getText();
		List<Integer> lineStarts = new ArrayList<>(List.of(0));
		for (int i = 0; i < text.length(); i++)
			if (text.charAt(i) == '\n')
				lineStarts.add(i + 1);
		// named first, qualified after: the spans come children first
		record Found(Node node, String kind, String name, int start, int end) {
		}
		Map<Node, Found> found = new IdentityHashMap<>();
		for (RenderedSource.Span span : rendered.getSpans()) {
			Node node = span.node();
			if (node.getArtifact() == null || span.end() <= span.start())
				continue;
			Object data = node.getArtifact().getData();
			String kind = null, name = null;
			if (data instanceof ClassArtifactData c) {
				kind = "class";
				name = firstGroup(DECLARED_NAME, c.getClassDecl());
			} else if (data instanceof EnumArtifactData e) {
				kind = "enum";
				name = firstGroup(DECLARED_NAME, e.toString());
				if (name == null)
					name = firstGroup(java.util.regex.Pattern.compile("([A-Za-z_$][\\w$]*)"), e.toString());
			} else if (data instanceof FunctionArtifactData f) {
				kind = "function";
				name = firstGroup(CALLABLE_NAME, f.getSignature().replaceAll("\\b(?:export|default|async|static|public|private|protected|function|get|set)\\b", " "));
			} else if (data instanceof ArrowFunctionArtifactData a) {
				kind = "function";
				name = firstGroup(ASSIGNED_NAME, a.toString());
				if (name == null)
					name = firstGroup(CALLABLE_NAME, a.toString());
			}
			if (kind == null || name == null)
				continue;
			// a span starts with the separator before it: the line break ending the line before
			int start = span.start();
			while (start < span.end() && Character.isWhitespace(text.charAt(start)))
				start++;
			found.put(node, new Found(node, kind, name, lineOf(lineStarts, start), lineOf(lineStarts, Math.max(start, span.end() - 1))));
		}
		List<SourcePosition> positions = new ArrayList<>();
		for (Found f : found.values()) {
			StringBuilder qualified = new StringBuilder(f.name());
			for (Node parent = f.node().getParent(); parent != null; parent = parent.getParent())
				if (found.containsKey(parent))
					qualified.insert(0, found.get(parent).name() + ".");
			positions.add(new SourcePosition(f.kind(), qualified.toString(), f.start(), f.end()));
		}
		return positions;
	}

	private static String firstGroup(java.util.regex.Pattern pattern, String text) {
		java.util.regex.Matcher m = pattern.matcher(text == null ? "" : text);
		return m.find() ? m.group(1) : null;
	}

	/** The 1-based line of a character offset. */
	private static int lineOf(List<Integer> lineStarts, int offset) {
		int index = Collections.binarySearch(lineStarts, offset);
		return (index >= 0 ? index : -index - 2) + 1;
	}

}
