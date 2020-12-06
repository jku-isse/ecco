package at.jku.isse.ecco.adapter.cpp;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.cpp.data.*;
import at.jku.isse.ecco.adapter.dispatch.DispatchWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.google.inject.Inject;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.cpp.*;
import org.eclipse.cdt.core.dom.ast.gnu.cpp.GPPLanguage;
import org.eclipse.cdt.core.model.ILanguage;
import org.eclipse.cdt.core.parser.*;
import org.eclipse.cdt.internal.core.dom.parser.cpp.*;
import org.eclipse.cdt.internal.core.parser.IMacroDictionary;
import org.eclipse.cdt.internal.core.parser.SavedFilesProvider;
import org.eclipse.cdt.internal.core.parser.scanner.InternalFileContent;
import org.eclipse.core.runtime.CoreException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkNotNull;

public class CppReader implements ArtifactReader<Path, Set<Node.Op>> {

    protected static final Logger LOGGER = Logger.getLogger(DispatchWriter.class.getName());
    public static final String NODE_OFFSET = "offset";
    private final EntityFactory entityFactory;

    @Inject
    public CppReader(EntityFactory entityFactory) {
        checkNotNull(entityFactory);
        this.entityFactory = entityFactory;
    }

    @Override
    public String getPluginId() {
        return CppPlugin.class.getName();
    }

    private static Map<Integer, String[]> prioritizedPatterns;

    static {
        prioritizedPatterns = new HashMap<>();
        prioritizedPatterns.put(Integer.MAX_VALUE, new String[]{"**.c", "**.h", "**.cpp", "**.hpp"});
    }

    @Override
    public Map<Integer, String[]> getPrioritizedPatterns() {
        return Collections.unmodifiableMap(prioritizedPatterns);
    }

    @Override
    public Set<Node.Op> read(Path[] input) {
        return this.read(Paths.get("."), input);
    }

    @Override
    public Set<Node.Op> read(Path base, Path[] input) {
        Set<Node.Op> nodes = new HashSet<>();
        final List<String> headerFiles = new ArrayList<String>();
        for (Path path : input) {
            Path resolvedPath = base.resolve(path);
            File file = resolvedPath.toFile();
            //System.out.println(file.getName());
            String fileCont = null;
            try {
                fileCont = new String(Files.readAllBytes(resolvedPath), StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }
            String[] lines = fileCont.split("\\r?\\n");

            Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
            Node.Op pluginNode = this.entityFactory.createNode(pluginArtifact);
            nodes.add(pluginNode);

            try {
                FileContent fileContent = FileContent.createForExternalFileLocation(file.getAbsolutePath());

                Map<String, String> definedSymbols = new HashMap<>();
                String[] includePaths = headerFiles.toArray(new String[headerFiles.size()]);
                IScannerInfo info = new ScannerInfo(definedSymbols, includePaths);
                IParserLogService log = new DefaultLogService();

                IncludeFileContentProvider emptyIncludes = new SavedFilesProvider() {
                    @Override
                    public InternalFileContent getContentForInclusion(String path, IMacroDictionary macroDictionary) {
                        if (!getInclusionExists(path)) {
//						if(!headerFiles.contains(path)){
                            return null;
                        }
                        return (InternalFileContent) FileContent.createForExternalFileLocation(path);
                    }
                };

                int opts = ILanguage.OPTION_PARSE_INACTIVE_CODE | ILanguage.OPTION_IS_SOURCE_UNIT;

                IASTTranslationUnit translationUnit = GPPLanguage.getDefault().getASTTranslationUnit(fileContent, info, emptyIncludes, null, opts, log);
                IASTPreprocessorStatement[] ppAllStatements = translationUnit.getAllPreprocessorStatements();
                IASTPreprocessorStatement[] ppMacroStatements = translationUnit.getMacroDefinitions();
                IASTPreprocessorStatement[] ppIncludeStatements = translationUnit.getIncludeDirectives();

                //comment out preprocessor directives
                try {
                    String content = getFileContentWithoutIfdefs(file, ppAllStatements);

                    fileContent = FileContent.create(file.getCanonicalPath(), content.toCharArray());

                    //parse again
                    translationUnit = GPPLanguage.getDefault().getASTTranslationUnit(fileContent, info, emptyIncludes, null, opts, log);

                } catch (IOException e) {
                    e.printStackTrace();
                }

                // create includes artifact/node
                Artifact.Op<AbstractArtifactData> includesGroupArtifact = this.entityFactory.createArtifact(new AbstractArtifactData("INCLUDES"));
                Node.Op includesGroupNode = this.entityFactory.createOrderedNode(includesGroupArtifact);
                pluginNode.addChild(includesGroupNode);
                // create defines artifact/node
                Artifact.Op<AbstractArtifactData> definesGroupArtifact = this.entityFactory.createArtifact(new AbstractArtifactData("DEFINES"));
                Node.Op definesGroupNode = this.entityFactory.createOrderedNode(definesGroupArtifact);
                pluginNode.addChild(definesGroupNode);
                // create fields artifact/node
                Artifact.Op<AbstractArtifactData> fieldsGroupArtifact = this.entityFactory.createArtifact(new AbstractArtifactData("FIELDS"));
                Node.Op fieldsGroupNode = this.entityFactory.createOrderedNode(fieldsGroupArtifact);
                pluginNode.addChild(fieldsGroupNode);
                // create functions artifact/node
                Artifact.Op<AbstractArtifactData> functionsGroupArtifact = this.entityFactory.createArtifact(new AbstractArtifactData("FUNCTIONS"));
                Node.Op functionsGroupNode = this.entityFactory.createOrderedNode(functionsGroupArtifact);
                pluginNode.addChild(functionsGroupNode);


                Map<String, Integer> macroPosition = new HashMap<>();
                Map<String, Integer> definesPosition = new HashMap<>();
                ArrayList<String> macros = new ArrayList<>();
                ArrayList<Integer> lineNumbers = new ArrayList<>();
                ArrayList<Integer> lineNumbersSwitchCase = new ArrayList<>();
                if (ppMacroStatements != null) {
                    for (IASTPreprocessorStatement macro : ppMacroStatements) {
                        if (macro.getContainingFilename().equals(translationUnit.getContainingFilename())) {
                            macroPosition.put(macro.getRawSignature(), macro.getFileLocation().getStartingLineNumber());
                            macros.add(macro.getRawSignature());
                        }
                    }
                }


                traverseAST(translationUnit.getOriginalNode(), pluginNode, functionsGroupNode, fieldsGroupNode, true, "", lines, lineNumbers, lineNumbersSwitchCase);

                for (Map.Entry<String, Integer> macro : macroPosition.entrySet()) {
                    for (int i = 0; i < lineNumbers.size() - 1; i += 2) {
                        if (macro.getValue() >= lineNumbers.get(i) && macro.getValue() <= lineNumbers.get(i + 1)) {
                            macros.remove(macro.getKey());
                            break;
                        }
                    }
                    if (macros.contains(macro.getKey()))
                        definesPosition.put(macro.getKey(), macro.getValue());
                }
                for (IASTPreprocessorStatement preprocessorstatement : ppAllStatements) {
                    if (preprocessorstatement instanceof IASTPreprocessorUndefStatement) {
                        Boolean add = true;
                        for (int i = 0; i < lineNumbers.size() - 1; i += 2) {
                            if (preprocessorstatement.getFileLocation().getStartingLineNumber() >= lineNumbers.get(i) && preprocessorstatement.getFileLocation().getStartingLineNumber() <= lineNumbers.get(i + 1)) {
                                add = false;
                                break;
                            }
                        }
                        if (add)
                            definesPosition.put(preprocessorstatement.getRawSignature(), preprocessorstatement.getFileLocation().getStartingLineNumber());
                    }
                }
                List<Map.Entry<String, Integer>> list = new ArrayList<>(definesPosition.entrySet());
                list.sort(Map.Entry.comparingByValue());

                for (Map.Entry<String, Integer> entry : list) {
                    Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(entry.getKey()));
                    Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                    definesGroupNode.addChild(lineNode);
                }

                if (ppIncludeStatements != null) {
                    for (IASTPreprocessorStatement preprocessorStatement : ppIncludeStatements) {
                        if (preprocessorStatement.getContainingFilename().equals(translationUnit.getContainingFilename()) && preprocessorStatement instanceof IASTPreprocessorIncludeStatement) {
                            Boolean add = true;
                            for (int i = 0; i < lineNumbers.size() - 1; i += 2) {
                                if (preprocessorStatement.getFileLocation().getStartingLineNumber() >= lineNumbers.get(i) && preprocessorStatement.getFileLocation().getStartingLineNumber() <= lineNumbers.get(i + 1)) {
                                    add = false;
                                    break;
                                }
                            }
                            if (add) {
                                String includeName = preprocessorStatement.getRawSignature();
                                Artifact.Op<IncludeArtifactData> includesArtifact = this.entityFactory.createArtifact(new IncludeArtifactData(includeName));
                                Node.Op includeNode = this.entityFactory.createOrderedNode(includesArtifact);
                                includesGroupNode.addChild(includeNode);
                            }
                        }
                    }
                }

            } catch (CoreException e) {
                e.printStackTrace();
                throw new EccoException("Error parsing java file.", e);
            }

        }

        return nodes;
    }


    private void traverseAST(IASTNode astNode, Node.Op classnode, Node.Op functions, Node.Op fields, final boolean saveLocationInfromtation, String indent, String[] lines, ArrayList<Integer> lineNumbers, ArrayList<Integer> lineNumbersSwitchCase) {

        for (IASTNode child : astNode.getChildren()) {
            if (child != null && child.getContainingFilename().equals(astNode.getContainingFilename())) {
                getIdentifier(child, classnode, functions, fields, lines, lineNumbers, lineNumbersSwitchCase);
            }
        }
    }


    public void getIdentifier(IASTNode node, Node.Op parentNode, Node.Op functionsNode, Node.Op fieldsNode, String[] lines, ArrayList<Integer> lineNumbers, ArrayList<Integer> lineNumbersSwitchCase) {
        //TODO create identifiers for nodes
        if (node instanceof IASTFieldDeclarator) {
            Artifact.Op<FieldArtifactData> fieldArtifact = this.entityFactory.createArtifact(new FieldArtifactData(node.getRawSignature()));
            //Artifact.Op<FieldArtifactData> fieldArtifact = this.entityFactory.createArtifact(new FieldArtifactData(lines[node.getFileLocation().getStartingLineNumber()-1]));
            Node.Op fieldNode = this.entityFactory.createOrderedNode(fieldArtifact);
            fieldsNode.addChild(fieldNode);
            lineNumbers.add(node.getFileLocation().getStartingLineNumber());
            lineNumbers.add(node.getFileLocation().getEndingLineNumber());
            /*if (node.getFileLocation().getStartingLineNumber() != node.getFileLocation().getEndingLineNumber()) {
                for (int i = node.getFileLocation().getStartingLineNumber(); i <= node.getFileLocation().getEndingLineNumber() - 1; i++) {
                    Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i]));
                    Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                    fieldNode.addChild(lineNode);
                }
            }*/
        } else if (node instanceof IASTProblemDeclaration) {
            //System.out.println(((IASTProblemDeclaration) node).getProblem().copy());
            if (!node.getRawSignature().equals(")")) {
                Artifact.Op<ProblemBlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new ProblemBlockArtifactData(node.getRawSignature()));
                Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                fieldsNode.addChild(blockNode);
                lineNumbers.add(node.getFileLocation().getStartingLineNumber());
                lineNumbers.add(node.getFileLocation().getEndingLineNumber());
            }
        } else if (node instanceof IASTTranslationUnit) {
            //return node.getContainingFilename();
        } else if (node instanceof IASTCompoundStatement) {
            //return "BLOCK";
        } else if (node instanceof IASTCompositeTypeSpecifier) {
            //return "struct " + ((IASTCompositeTypeSpecifier) node).getName().getRawSignature();
        } else if (node instanceof IASTIfStatement) {
            if (((IASTIfStatement) node).getConditionExpression() != null) {
                System.out.println("if(" + ((IASTIfStatement) node).getConditionExpression().getRawSignature() + ")");
                //return "if(" + ((IASTIfStatement) node).getConditionExpression().getRawSignature() + ")";
            } else {
                System.out.println("if(" + ((ICPPASTIfStatement) node).getConditionDeclaration().getRawSignature() + ")");
                //return "if(" + ((ICPPASTIfStatement) node).getConditionDeclaration().getRawSignature() + ")";
            }
        } else if (node instanceof IASTSwitchStatement) {
            if (((IASTSwitchStatement) node).getControllerExpression() != null) {
                //return "switch(" + ((IASTSwitchStatement) node).getControllerExpression().getRawSignature() + ")";
            } else {
                //return "switch(" + ((ICPPASTSwitchStatement) node).getControllerDeclaration().getRawSignature() + ")";
            }
        } else if (node instanceof IASTForStatement) {
            String init = ";";
            if (((IASTForStatement) node).getInitializerStatement() != null) {
                init = ((IASTForStatement) node).getInitializerStatement().getRawSignature();
            }
            String condition = "";
            if (((IASTForStatement) node).getConditionExpression() != null) {
                condition = ((IASTForStatement) node).getConditionExpression().getRawSignature();
            } else if (node instanceof ICPPASTForStatement && ((ICPPASTForStatement) node).getConditionDeclaration() != null) {
                condition = ((ICPPASTForStatement) node).getConditionDeclaration().getRawSignature();
            }
            String iteration = "";
            if (((IASTForStatement) node).getIterationExpression() != null) {
                iteration = ((IASTForStatement) node).getIterationExpression().getRawSignature();
            }
            //return "for(" + init + " "
            //        + condition + "; "
            //        + iteration + ")";
        } else if (node instanceof IASTWhileStatement) {
            if (((IASTWhileStatement) node).getCondition() != null) {
                //return "while(" + ((IASTWhileStatement) node).getCondition().getRawSignature() + ")";
            } else {
                //return "while(" + ((ICPPASTWhileStatement) node).getConditionDeclaration().getRawSignature() + ")";
            }
        } else if (node instanceof IASTDoStatement) {
            //return "do while(" + ((IASTDoStatement) node).getCondition().getRawSignature() + ")";
        } else if (node instanceof IASTSimpleDeclaration) {
            if (((IASTSimpleDeclaration) node).getDeclSpecifier() instanceof IASTCompositeTypeSpecifier) {
                //Artifact.Op<FieldArtifactData> fieldArtifact = this.entityFactory.createArtifact(new FieldArtifactData(lines[node.getFileLocation().getStartingLineNumber()-1]));
                Artifact.Op<FieldArtifactData> fieldArtifact = this.entityFactory.createArtifact(new FieldArtifactData(node.getRawSignature()));
                Node.Op fieldNode = this.entityFactory.createOrderedNode(fieldArtifact);
                fieldsNode.addChild(fieldNode);
                lineNumbers.add(node.getFileLocation().getStartingLineNumber());
                lineNumbers.add(node.getFileLocation().getEndingLineNumber());
                //return getIdentifier(((IASTSimpleDeclaration) node).getDeclSpecifier(), functionsNode, fieldsNode, lines);
                /*if (node.getFileLocation().getStartingLineNumber() != node.getFileLocation().getEndingLineNumber()) {
                    for (int i = node.getFileLocation().getStartingLineNumber(); i <= node.getFileLocation().getEndingLineNumber() - 1; i++) {
                        Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i]));
                        Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                        fieldNode.addChild(lineNode);
                    }
                }*/
            } else if (((IASTSimpleDeclaration) node).getDeclarators().length == 1) {
                int init = node.getFileLocation().getStartingLineNumber() - 1;
                int end = node.getFileLocation().getEndingLineNumber() - 1;
                String field = "";
                for (int i = init; i <= end; i++) {
                    field += lines[i] + "\n";
                }
                //Artifact.Op<FieldArtifactData> fieldArtifact = this.entityFactory.createArtifact(new FieldArtifactData(lines[node.getFileLocation().getStartingLineNumber()-1]));
                Artifact.Op<FieldArtifactData> fieldArtifact = this.entityFactory.createArtifact(new FieldArtifactData(field));
                Node.Op fieldNode = this.entityFactory.createOrderedNode(fieldArtifact);
                fieldsNode.addChild(fieldNode);
                lineNumbers.add(node.getFileLocation().getStartingLineNumber());
                lineNumbers.add(node.getFileLocation().getEndingLineNumber());
                /*if (node.getFileLocation().getStartingLineNumber() != node.getFileLocation().getEndingLineNumber()) {
                    for (int i = node.getFileLocation().getStartingLineNumber(); i <= node.getFileLocation().getEndingLineNumber() - 1; i++) {
                        Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i]));
                        Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                        fieldNode.addChild(lineNode);
                    }
                }*/
                //return getIdentifier(((IASTSimpleDeclaration) node).getDeclarators()[0], functionsNode, fieldsNode, lines);
            } else if (node instanceof CPPASTSimpleDeclaration) {
                if (!lineNumbers.contains(node.getFileLocation().getStartingLineNumber())) {
                    //Artifact.Op<FieldArtifactData> fieldArtifact = this.entityFactory.createArtifact(new FieldArtifactData(lines[node.getFileLocation().getStartingLineNumber()-1]));
                    Artifact.Op<FieldArtifactData> fieldArtifact = this.entityFactory.createArtifact(new FieldArtifactData(node.getRawSignature()));
                    Node.Op fieldNode = this.entityFactory.createOrderedNode(fieldArtifact);
                    fieldsNode.addChild(fieldNode);
                    lineNumbers.add(node.getFileLocation().getStartingLineNumber());
                    lineNumbers.add(node.getFileLocation().getEndingLineNumber());
                }
                //return getIdentifier(((IASTSimpleDeclaration) node).getDeclSpecifier(), functionsNode, fieldsNode, lines);
                /*if (node.getFileLocation().getStartingLineNumber() != node.getFileLocation().getEndingLineNumber()) {
                    for (int i = node.getFileLocation().getStartingLineNumber(); i <= node.getFileLocation().getEndingLineNumber() - 1; i++) {
                        Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i]));
                        Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                        fieldNode.addChild(lineNode);
                    }
                }*/
            }
        } else if (node instanceof IASTFunctionDefinition) {
            int init = ((IASTFunctionDefinition) node).getDeclSpecifier().getFileLocation().getStartingLineNumber() - 1;
            int end = ((IASTFunctionDefinition) node).getDeclarator().getFileLocation().getEndingLineNumber() - 1;
            String function = "";
            for (int i = init; i <= end; i++) {
                function += lines[i] + "\n";
            }
            Artifact.Op<FunctionArtifactData> functionsArtifact = this.entityFactory.createArtifact(new FunctionArtifactData(function));
            Node.Op functionNode = this.entityFactory.createOrderedNode(functionsArtifact);
            functionsNode.addChild(functionNode);
            lineNumbers.add(node.getFileLocation().getStartingLineNumber());
            lineNumbers.add(node.getFileLocation().getEndingLineNumber());
            if (node.getTranslationUnit().getMacroDefinitions().length > 0) {
                for (IASTPreprocessorMacroDefinition macro : node.getTranslationUnit().getMacroDefinitions()) {
                    if (macro.getFileLocation().getFileName().equals(node.getFileLocation().getFileName()) && macro.getFileLocation().getStartingLineNumber() > node.getFileLocation().getStartingLineNumber() && macro.getFileLocation().getStartingLineNumber() < node.getFileLocation().getEndingLineNumber()) {
                        Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(macro.getRawSignature()));
                        Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                        functionNode.addChild(lineNode);
                    }
                }
            }
            for (IASTNode child : ((IASTFunctionDefinition) node).getBody().getChildren()) {
                addChildFunction(child, functionNode, functionsNode, fieldsNode, lines, lineNumbers, lineNumbersSwitchCase);
            }
            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData("}"));
            Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
            functionNode.addChild(lineNode);
            /*for (int i = ((IASTFunctionDefinition) node).getBody().getFileLocation().getStartingLineNumber(); i <= ((IASTFunctionDefinition) node).getBody().getFileLocation().getEndingLineNumber() - 1; i++) {
                // ((IASTFunctionDefinition) node).getBody().getFileLocation().get(i - 1).setExists(true);
                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i]));
                Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                functionNode.addChild(lineNode);
            }*/

            //return getIdentifier(((IASTFunctionDefinition) node).getDeclarator(), functionsNode, fieldsNode, lines);
        } else if (node instanceof ICPPASTFunctionDeclarator) {
            String name = ((IASTFunctionDeclarator) node).getName().getRawSignature();
            String parameters = "";
            boolean first = true;
            for (ICPPASTParameterDeclaration para : ((ICPPASTFunctionDeclarator) node).getParameters()) {
                if (!first) {
                    parameters += ", ";
                }
                parameters += para.getDeclSpecifier().getRawSignature();
                first = false;
            }
            //TODO insert parameter types into string
            // return name + "(" + parameters + ")";
        } else if (node instanceof IASTFunctionDeclarator) {
            String name = ((IASTFunctionDeclarator) node).getName().getRawSignature();
            String parameters = "";
            String functionName = node.getRawSignature();
            Artifact.Op<FunctionArtifactData> functionsArtifact = this.entityFactory.createArtifact(new FunctionArtifactData(functionName));
            Node.Op functionNode = this.entityFactory.createOrderedNode(functionsArtifact);
            functionsNode.addChild(functionNode);
            lineNumbers.add(node.getFileLocation().getStartingLineNumber());
            lineNumbers.add(node.getFileLocation().getEndingLineNumber());
            if (node.getTranslationUnit().getMacroDefinitions().length > 0) {
                for (IASTPreprocessorMacroDefinition macro : node.getTranslationUnit().getMacroDefinitions()) {
                    if (macro.getFileLocation().equals(node.getFileLocation()) && macro.getFileLocation().getStartingLineNumber() > node.getFileLocation().getStartingLineNumber() && macro.getFileLocation().getStartingLineNumber() < node.getFileLocation().getEndingLineNumber()) {
                        Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(macro.getRawSignature()));
                        Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                        functionNode.addChild(lineNode);
                    }
                }
            }
            //TODO insert parameter types into string
            for (IASTNode nodechild : node.getChildren()) {
                addChildFunction(nodechild, functionNode, functionsNode, fieldsNode, lines, lineNumbers, lineNumbersSwitchCase);
            }
            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData("}"));
            Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
            functionNode.addChild(lineNode);
            //return name + "(" + parameters + ")";
        } else if (node instanceof ICPPASTLinkageSpecification) {
            Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(lines[node.getFileLocation().getStartingLineNumber() - 1]));
            Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
            functionsNode.addChild(blockNode);
            Node.Op lineNode = null;
            for (int i = node.getFileLocation().getStartingLineNumber(); i <= node.getFileLocation().getEndingLineNumber() - 1; i++) {
                String line = lines[i];
                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i]));
                lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                blockNode.addChild(lineNode);
            }
            lineNumbers.add(node.getFileLocation().getStartingLineNumber());
            lineNumbers.add(node.getFileLocation().getEndingLineNumber());
            //TODO could be more extern "C" -> not a unique identifier
            //return "extern " + ((ICPPASTLinkageSpecification) node).getLiteral() + "";
        } else if (node instanceof CPPASTProblemDeclaration) {
            if (!node.getRawSignature().equals(")")) {
                Artifact.Op<ProblemBlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new ProblemBlockArtifactData(node.getRawSignature()));
                Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                fieldsNode.addChild(blockNode);
                lineNumbers.add(node.getFileLocation().getStartingLineNumber());
                lineNumbers.add(node.getFileLocation().getEndingLineNumber());
            }
        } else {
            System.out.println("+++++++++++++++++++++ corner case +++++++++++ " + node.getRawSignature() + " " + node.getFileLocation().getFileName() + " " + node.getFileLocation().getStartingLineNumber());
        }
        //return node.getRawSignature();
    }

    public void addChildFunction(IASTNode node, Node.Op parentNode, Node.Op functionsNode, Node.Op fieldsNode, String[] lines, ArrayList<Integer> lineNumbers, ArrayList<Integer> lineNumbersSwitchCase) {
        if (node instanceof IASTExpressionStatement || node instanceof CPPASTContinueStatement || node instanceof IASTDeclarationStatement || node instanceof CPPASTReturnStatement || node instanceof IASTReturnStatement || node instanceof IASTLabelStatement || node instanceof IASTGotoStatement || node instanceof CPPASTGotoStatement || node instanceof IASTBinaryExpression || node instanceof IASTFunctionCallExpression || node instanceof CPPASTBinaryExpression || node instanceof CPPASTBreakStatement || node instanceof CPPASTFieldReference || node instanceof CPPASTLiteralExpression) {
            Artifact.Op<LineArtifactData> lineArtifact;
            Node.Op lineNode;
            if (node.getFileLocation().getStartingLineNumber() != node.getFileLocation().getEndingLineNumber()) {
                for (int i = node.getFileLocation().getStartingLineNumber() - 1; i <= node.getFileLocation().getEndingLineNumber() - 1; i++) {
                    lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i]));
                    lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                    parentNode.addChild(lineNode);
                }
            } else if (!lineNumbersSwitchCase.contains(node.getFileLocation().getStartingLineNumber() - 1)) {
                lineNumbersSwitchCase.add(node.getFileLocation().getStartingLineNumber() - 1);
                lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[node.getFileLocation().getStartingLineNumber() - 1]));
                lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                parentNode.addChild(lineNode);
            }
        } else if (node instanceof IASTIfStatement || node instanceof ICPPASTIfStatement) {
            /*Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(node.getRawSignature()));
            Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
            parentNode.addChild(lineNode);*/
            if (((IASTIfStatement) node).getConditionExpression() != null) {
                boolean first = true;
                Artifact.Op<IfBlockArtifactData> blockArtifact;
                Node.Op blockNode = null;
                String ifexpression = "";
                if (node.getFileLocation().getStartingLineNumber() == node.getFileLocation().getEndingLineNumber()) {
                    blockArtifact = this.entityFactory.createArtifact(new IfBlockArtifactData(lines[node.getFileLocation().getStartingLineNumber() - 1]));
                    blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                    parentNode.addChild(blockNode);
                    lineNumbersSwitchCase.add(node.getFileLocation().getStartingLineNumber() - 1);
                } else {
                    /*for (IASTNode child : ((ICPPASTIfStatement) node).getThenClause().getChildren()) {
                        if ((child instanceof CPPASTReturnStatement || child instanceof CPPASTGotoStatement || ((ICPPASTIfStatement) node).getThenClause().getChildren().length == 1) && first) {
                            //if (node.getRawSignature().contains("{"))
                            //    ifexpression = "if( " + ((IASTIfStatement) node).getConditionExpression().getRawSignature() + " ) {";
                            for (int i = node.getFileLocation().getStartingLineNumber() - 1; i < child.getFileLocation().getStartingLineNumber() - 1; i++) {
                                if (!ifexpression.equals(""))
                                    ifexpression += "\n" + lines[i];
                                else
                                    ifexpression += lines[i];
                            }
                            if (ifexpression.equals("")) {
                                blockNode = parentNode;
                                addChildFunction(child, parentNode, functionsNode, fieldsNode, lines, lineNumbers, lineNumbersSwitchCase);
                            } else {
                                blockArtifact = this.entityFactory.createArtifact(new IfBlockArtifactData(ifexpression));
                                blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                                parentNode.addChild(blockNode);
                            }
                            if (node.getFileLocation().getStartingLineNumber() != child.getFileLocation().getStartingLineNumber()) {
                                first = false;
                                addChildFunction(child, blockNode, functionsNode, fieldsNode, lines, lineNumbers, lineNumbersSwitchCase);
                            }
                        } else if (first) {
                            if (child.getFileLocation().getStartingLineNumber() == node.getFileLocation().getEndingLineNumber()) {
                                first = false;
                            } else {
                                String ifaux = lines[((IASTIfStatement) node).getConditionExpression().getFileLocation().getStartingLineNumber() - 1].substring(lines[((IASTIfStatement) node).getConditionExpression().getFileLocation().getStartingLineNumber() - 1].lastIndexOf(")") + 1);
                                blockArtifact = this.entityFactory.createArtifact(new IfBlockArtifactData("if(" + ((IASTIfStatement) node).getConditionExpression().getRawSignature() + ") "+ifaux));
                                blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                                parentNode.addChild(blockNode);
                                first = false;
                                addChildFunction(child, blockNode, functionsNode, fieldsNode, lines, lineNumbers, lineNumbersSwitchCase);
                            }
                        } else {
                            addChildFunction(child, blockNode, functionsNode, fieldsNode, lines, lineNumbers, lineNumbersSwitchCase);
                        }
                    }*/
                    if (((ICPPASTIfStatement) node).getThenClause() != null) {
                        for (int i = node.getFileLocation().getStartingLineNumber() - 1; i <= ((ICPPASTIfStatement) node).getConditionExpression().getFileLocation().getEndingLineNumber() - 1; i++) {
                            if (!ifexpression.equals(""))
                                ifexpression += "\n" + lines[i];
                            else
                                ifexpression += lines[i];
                        }
                        if (ifexpression.equals("")) {
                            ifexpression = lines[((ICPPASTIfStatement) node).getFileLocation().getStartingLineNumber() - 1];
                        }

                        blockArtifact = this.entityFactory.createArtifact(new IfBlockArtifactData(ifexpression));
                        blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                        parentNode.addChild(blockNode);
                        ifexpression = "";

                        addChildFunction(((ICPPASTIfStatement) node).getThenClause(), blockNode, functionsNode, fieldsNode, lines, lineNumbers, lineNumbersSwitchCase);
                    }
                    //means if does not have anything
                    if (blockNode == null) {
                        String ifaux = lines[((IASTIfStatement) node).getConditionExpression().getFileLocation().getStartingLineNumber() - 1].substring(lines[((IASTIfStatement) node).getConditionExpression().getFileLocation().getStartingLineNumber() - 1].lastIndexOf(")") + 1);
                        blockArtifact = this.entityFactory.createArtifact(new IfBlockArtifactData("if(" + ((IASTIfStatement) node).getConditionExpression().getRawSignature() + ") " + ifaux));
                        blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                        parentNode.addChild(blockNode);
                        first = false;
                    }
                    if (((ICPPASTIfStatement) node).getElseClause() != null) {
                        if (((ICPPASTIfStatement) node).getElseClause().getFileLocation().getStartingLineNumber() != ((ICPPASTIfStatement) node).getElseClause().getFileLocation().getEndingLineNumber()) {
                            for (int i = ((ICPPASTIfStatement) node).getElseClause().getFileLocation().getStartingLineNumber() - 1; i <= ((ICPPASTIfStatement) node).getElseClause().getFileLocation().getEndingLineNumber() - 1; i++) {
                                String elsestring = "";
                                if (!lines[i].contains("else") && blockNode.getArtifact().getData().toString().contains("{") && i == ((ICPPASTIfStatement) node).getElseClause().getFileLocation().getStartingLineNumber() - 1) {
                                    elsestring += "}else" + "\n";
                                } else if (!lines[i].contains("else") && i == ((ICPPASTIfStatement) node).getElseClause().getFileLocation().getStartingLineNumber() - 1) {
                                    elsestring += "else" + "\n";
                                }
                                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData((elsestring + lines[i])));
                                Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                                blockNode.addChild(lineNodeChild);
                            }
                        } else {
                            Artifact.Op<LineArtifactData> lineArtifact = null;
                            if (lines[((ICPPASTIfStatement) node).getElseClause().getFileLocation().getStartingLineNumber() - 1].contains("else")) {
                                lineArtifact = this.entityFactory.createArtifact(new LineArtifactData((lines[((ICPPASTIfStatement) node).getElseClause().getFileLocation().getStartingLineNumber() - 1])));
                                Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                                blockNode.addChild(lineNodeChild);
                            } else {
                                lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(("else")));
                                Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                                blockNode.addChild(lineNodeChild);
                                lineArtifact = this.entityFactory.createArtifact(new LineArtifactData((lines[((ICPPASTIfStatement) node).getElseClause().getFileLocation().getStartingLineNumber() - 1])));
                                lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                                blockNode.addChild(lineNodeChild);
                            }
                        }
                    }
                    if (!first && blockNode.getArtifact().getData().toString().contains("{")) {
                        Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(("}")));
                        Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                        blockNode.addChild(lineNodeChild);
                    }
                }
            } else {
                Node.Op blockNode;
                if (((ICPPASTIfStatement) node).getThenClause().getChildren().length > 0) {
                    String ifaux = lines[((ICPPASTIfStatement) node).getConditionDeclaration().getFileLocation().getStartingLineNumber() - 1].substring(lines[((ICPPASTIfStatement) node).getConditionDeclaration().getFileLocation().getStartingLineNumber() - 1].lastIndexOf(")") + 1);
                    Artifact.Op<IfBlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new IfBlockArtifactData("if(" + ((ICPPASTIfStatement) node).getConditionDeclaration().getRawSignature() + ") " + ifaux));
                    blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                    parentNode.addChild(blockNode);
                    for (IASTNode child : ((ICPPASTIfStatement) node).getThenClause().getChildren()) {
                        addChildFunction(child, blockNode, functionsNode, fieldsNode, lines, lineNumbers, lineNumbersSwitchCase);
                    }
                    Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(("}")));
                    Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                    blockNode.addChild(lineNodeChild);
                } else {
                    String ifaux = lines[((ICPPASTIfStatement) node).getConditionDeclaration().getFileLocation().getStartingLineNumber() - 1].substring(lines[((ICPPASTIfStatement) node).getConditionDeclaration().getFileLocation().getStartingLineNumber() - 1].lastIndexOf(")") + 1);
                    Artifact.Op<IfBlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new IfBlockArtifactData("if(" + ((ICPPASTIfStatement) node).getConditionDeclaration().getRawSignature() + ") " + ifaux));
                    blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                    parentNode.addChild(blockNode);
                    for (IASTNode child : ((ICPPASTIfStatement) node).getThenClause().getChildren()) {
                        addChildFunction(child, blockNode, functionsNode, fieldsNode, lines, lineNumbers, lineNumbersSwitchCase);
                    }
                }
                if (((ICPPASTIfStatement) node).getElseClause() != null) {
                    for (int i = ((ICPPASTIfStatement) node).getElseClause().getFileLocation().getStartingLineNumber() - 1; i < ((ICPPASTIfStatement) node).getElseClause().getFileLocation().getEndingLineNumber() - 1; i++) {
                        Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData((lines[i])));
                        Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                        blockNode.addChild(lineNodeChild);
                    }
                }
            }
        } else if (node instanceof IASTWhileStatement) {

            if (((IASTWhileStatement) node).getCondition() != null) {
                String whileaux = lines[((IASTWhileStatement) node).getCondition().getFileLocation().getStartingLineNumber() - 1].substring(lines[((IASTWhileStatement) node).getCondition().getFileLocation().getStartingLineNumber() - 1].lastIndexOf(")") + 1);
                if (node.getFileLocation().getStartingLineNumber() != node.getFileLocation().getEndingLineNumber()) {
                    Artifact.Op<WhileBlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new WhileBlockArtifactData("while( " + ((IASTWhileStatement) node).getCondition().getRawSignature() + " ) " + whileaux));
                    Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                    parentNode.addChild(blockNode);
                    for (IASTNode child : ((ICPPASTWhileStatement) node).getBody().getChildren()) {
                        addChildFunction(child, blockNode, functionsNode, fieldsNode, lines, lineNumbers, lineNumbersSwitchCase);
                    }
                    if (whileaux.contains("{")) {
                        Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(("}")));
                        Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                        blockNode.addChild(lineNodeChild);
                    }
                } else {
                    Artifact.Op<WhileBlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new WhileBlockArtifactData(lines[node.getFileLocation().getStartingLineNumber() - 1]));
                    Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                    parentNode.addChild(blockNode);
                }
            } else {
                if (node.getFileLocation().getStartingLineNumber() != node.getFileLocation().getEndingLineNumber()) {

                    Artifact.Op<WhileBlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new WhileBlockArtifactData("while( " + ((ICPPASTWhileStatement) node).getConditionDeclaration().getRawSignature() + ") {"));
                    Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                    parentNode.addChild(blockNode);
                    for (IASTNode child : ((ICPPASTWhileStatement) node).getBody().getChildren()) {
                        addChildFunction(child, blockNode, functionsNode, fieldsNode, lines, lineNumbers, lineNumbersSwitchCase);
                    }
                    Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(("}")));
                    Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                    blockNode.addChild(lineNodeChild);
                } else {
                    Artifact.Op<WhileBlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new WhileBlockArtifactData(lines[node.getFileLocation().getStartingLineNumber() - 1]));
                    Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                    parentNode.addChild(blockNode);
                }
            }
        } else if (node instanceof IASTForStatement) {
            if (node.getFileLocation().getStartingLineNumber() != node.getFileLocation().getEndingLineNumber()) {
                String init = ";";
                if (((IASTForStatement) node).getInitializerStatement() != null) {
                    init = ((IASTForStatement) node).getInitializerStatement().getRawSignature();
                }
                String condition = "";
                if (((IASTForStatement) node).getConditionExpression() != null) {
                    condition = ((IASTForStatement) node).getConditionExpression().getRawSignature();
                } else if (node instanceof ICPPASTForStatement && ((ICPPASTForStatement) node).getConditionDeclaration() != null) {
                    condition = ((ICPPASTForStatement) node).getConditionDeclaration().getRawSignature();
                }
                String iteration = "";
                if (((IASTForStatement) node).getIterationExpression() != null) {
                    iteration = ((IASTForStatement) node).getIterationExpression().getRawSignature();
                }
                String foraux = "";
                if (((IASTForStatement) node).getIterationExpression() != null) {
                    foraux = lines[((IASTForStatement) node).getIterationExpression().getFileLocation().getStartingLineNumber() - 1].substring(lines[((IASTForStatement) node).getIterationExpression().getFileLocation().getStartingLineNumber() - 1].lastIndexOf(")") + 1);
                } else {
                    foraux = lines[((IASTForStatement) node).getConditionExpression().getFileLocation().getStartingLineNumber() - 1].substring(lines[((IASTForStatement) node).getConditionExpression().getFileLocation().getStartingLineNumber() - 1].lastIndexOf(")") + 1);
                }
                Artifact.Op<ForBlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new ForBlockArtifactData("for(" + init + " " + condition + "; " + iteration + ")" + foraux));
                Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                parentNode.addChild(blockNode);

                for (IASTNode child : ((IASTForStatement) node).getBody().getChildren()) {
                    if (child instanceof CPPASTArraySubscriptExpression)
                        addChildFunction(child.getParent(), blockNode, functionsNode, fieldsNode, lines, lineNumbers, lineNumbersSwitchCase);
                    else
                        addChildFunction(child, blockNode, functionsNode, fieldsNode, lines, lineNumbers, lineNumbersSwitchCase);
                }
                if (foraux.lastIndexOf("{") != -1) {
                    Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(("}")));
                    Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                    blockNode.addChild(lineNodeChild);
                }
            } else {
                Artifact.Op<ForBlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new ForBlockArtifactData(lines[node.getFileLocation().getStartingLineNumber() - 1]));
                Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                parentNode.addChild(blockNode);
            }
        } else if (node instanceof IASTSwitchStatement || node instanceof CPPASTSwitchStatement) {
            if (((IASTSwitchStatement) node).getControllerExpression() != null) {
                Artifact.Op<SwitchBlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new SwitchBlockArtifactData("switch(" + ((IASTSwitchStatement) node).getControllerExpression().getRawSignature() + "){"));
                Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                parentNode.addChild(blockNode);
                Node.Op blockChildNode = blockNode;
                Artifact.Op<BlockArtifactData> blockCaseArtifact;
                for (IASTNode child : ((IASTSwitchStatement) node).getBody().getChildren()) {
                    if (child instanceof CPPASTCaseStatement || child instanceof CPPASTDefaultStatement) {
                        //if ((lines[child.getFileLocation().getStartingLineNumber() - 1]).contains("{"))
                        //    blockCaseArtifact = this.entityFactory.createArtifact(new BlockArtifactData(lines[child.getFileLocation().getStartingLineNumber() - 1].substring(0, lines[child.getFileLocation().getStartingLineNumber() - 1].indexOf("{") + 1)));
                        //else
                        if (!lineNumbersSwitchCase.contains(child.getFileLocation().getStartingLineNumber() - 1)) {
                            blockCaseArtifact = this.entityFactory.createArtifact(new BlockArtifactData(lines[child.getFileLocation().getStartingLineNumber() - 1]));
                            lineNumbersSwitchCase.add(child.getFileLocation().getStartingLineNumber() - 1);
                            blockChildNode = this.entityFactory.createOrderedNode(blockCaseArtifact);
                            blockNode.addChild(blockChildNode);
                        }
                    } else if (!lineNumbersSwitchCase.contains(Integer.valueOf(child.getFileLocation().getStartingLineNumber() - 1))) {
                        addChildFunction(child, blockChildNode, functionsNode, fieldsNode, lines, lineNumbers, lineNumbersSwitchCase);
                    } else if (child.getChildren().length > 1) {
                        addChildFunction(child, blockChildNode, functionsNode, fieldsNode, lines, lineNumbers, lineNumbersSwitchCase);
                    } else if (child instanceof CPPASTCompoundStatement) {
                        addChildFunction(child, blockChildNode, functionsNode, fieldsNode, lines, lineNumbers, lineNumbersSwitchCase);
                    }
                }
                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(("}")));
                Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                blockNode.addChild(lineNodeChild);
            } else {
                Artifact.Op<SwitchBlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new SwitchBlockArtifactData("switch(" + ((ICPPASTSwitchStatement) node).getControllerDeclaration().getRawSignature() + "){"));
                Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                parentNode.addChild(blockNode);
                Node.Op blockChildNode = null;
                Artifact.Op<BlockArtifactData> blockCaseArtifact;
                for (IASTNode child : ((ICPPASTSwitchStatement) node).getBody().getChildren()) {
                    if (child instanceof CPPASTCaseStatement || child instanceof CPPASTDefaultStatement) {
                        //if ((lines[child.getFileLocation().getStartingLineNumber() - 1]).contains("{"))
                        //    blockCaseArtifact = this.entityFactory.createArtifact(new BlockArtifactData(lines[child.getFileLocation().getStartingLineNumber() - 1].substring(0, lines[child.getFileLocation().getStartingLineNumber() - 1].indexOf("{") + 1)));
                        //else
                        blockCaseArtifact = this.entityFactory.createArtifact(new BlockArtifactData(lines[child.getFileLocation().getStartingLineNumber() - 1]));
                        lineNumbersSwitchCase.add(child.getFileLocation().getStartingLineNumber() - 1);
                        blockChildNode = this.entityFactory.createOrderedNode(blockCaseArtifact);
                        blockNode.addChild(blockChildNode);
                    } else if (!lineNumbersSwitchCase.contains(Integer.valueOf(child.getFileLocation().getStartingLineNumber() - 1))) {
                        addChildFunction(child, blockChildNode, functionsNode, fieldsNode, lines, lineNumbers, lineNumbersSwitchCase);
                    } else if (child.getChildren().length > 1) {
                        addChildFunction(child, blockChildNode, functionsNode, fieldsNode, lines, lineNumbers, lineNumbersSwitchCase);
                    } else if (child instanceof CPPASTCompoundStatement) {
                        addChildFunction(child, blockChildNode, functionsNode, fieldsNode, lines, lineNumbers, lineNumbersSwitchCase);
                    }
                }
            }
        } else if (node instanceof CPPASTCompoundStatement) {
            for (IASTNode child : node.getChildren()) {
                addChildFunction(child, parentNode, functionsNode, fieldsNode, lines, lineNumbers, lineNumbersSwitchCase);
            }
            if (parentNode.getArtifact().getData().toString().contains("{") && !parentNode.getArtifact().getData().toString().contains("do")) {
                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(("}")));
                Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                parentNode.addChild(lineNodeChild);
            }
        } else if (node instanceof CPPASTDefaultStatement) {
            Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(((CPPASTDefaultStatement) node).getRawSignature() + " {"));
            Node.Op blockChildNode = this.entityFactory.createOrderedNode(blockArtifact);
            parentNode.addChild(blockChildNode);
        } else if (node instanceof IASTDoStatement) {
            if (node.getFileLocation().getStartingLineNumber() != node.getFileLocation().getEndingLineNumber()) {
                Artifact.Op<DoBlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new DoBlockArtifactData("do{"));
                Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                parentNode.addChild(blockNode);
                for (IASTNode child : node.getChildren()) {
                    addChildFunction(child, blockNode, functionsNode, fieldsNode, lines, lineNumbers, lineNumbersSwitchCase);
                }
            } else {
                Artifact.Op<DoBlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new DoBlockArtifactData(lines[node.getFileLocation().getStartingLineNumber() - 1]));
                Node.Op blocknode = this.entityFactory.createOrderedNode(blockArtifact);
                parentNode.addChild(blocknode);
            }
            //Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData("} while( " + ((IASTDoStatement) node).getCondition().getRawSignature() + ")"));
            //Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
            //blockNode.addChild(lineNodeChild);
            //return "do while(" + ((IASTDoStatement) node).getCondition().getRawSignature() + ")";
        } else if (node instanceof CPPASTNullStatement) {

        } else if (node instanceof CPPASTIdExpression) {
            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[node.getFileLocation().getStartingLineNumber() - 1]));
            Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
            parentNode.addChild(lineNodeChild);
        } else if (node instanceof CPPASTUnaryExpression) {
            if (!parentNode.getArtifact().getData().toString().contains("do{")) {
                if (node.getParent() instanceof IASTIfStatement) {
                    String line = lines[node.getParent().getFileLocation().getStartingLineNumber() - 1];
                    Artifact.Op<IfBlockArtifactData> lineArtifact = this.entityFactory.createArtifact(new IfBlockArtifactData(line));
                    Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                    parentNode.addChild(lineNodeChild);
                } else if (node.getParent() instanceof CPPASTReturnStatement) {
                    String line = lines[node.getParent().getFileLocation().getStartingLineNumber() - 1];
                    Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(line));
                    Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                    parentNode.addChild(lineNodeChild);
                } else {
                    Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(node.getRawSignature() + ";"));
                    Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                    parentNode.addChild(lineNodeChild);
                }
            } else {
                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[node.getParent().getFileLocation().getStartingLineNumber() - 1]));
                Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                parentNode.addChild(lineNodeChild);
            }
        } else if (node instanceof CPPASTProblemStatement) {
            if (!node.getRawSignature().equals(")") && node.getRawSignature().length() > 1) {
                if (!lineNumbersSwitchCase.contains(node.getFileLocation().getStartingLineNumber() - 1) && node.getFileLocation().getStartingLineNumber() - 1 == node.getFileLocation().getEndingLineNumber() - 1) {
                    Artifact.Op<ProblemBlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new ProblemBlockArtifactData(lines[node.getFileLocation().getStartingLineNumber() - 1]));
                    lineNumbersSwitchCase.add(node.getFileLocation().getStartingLineNumber() - 1);
                    Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                    parentNode.addChild(blockNode);
                } else if (node.getFileLocation().getStartingLineNumber() - 1 != node.getFileLocation().getEndingLineNumber() - 1) {
                    String line = "";
                    for (int i = node.getFileLocation().getStartingLineNumber(); i <= node.getFileLocation().getEndingLineNumber() - 1; i++) {
                        line += lines[i] + "\n";
                        lineNumbersSwitchCase.add(i);
                    }
                    Artifact.Op<ProblemBlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new ProblemBlockArtifactData(line));
                    Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                    parentNode.addChild(blockNode);
                }
            }
        } else if (node instanceof CPPASTProblem) {
            if (!node.getRawSignature().equals(")") && node.getRawSignature().length() > 1) {
                Artifact.Op<ProblemBlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new ProblemBlockArtifactData(node.getRawSignature()));
                Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                parentNode.addChild(blockNode);
            }
        } else {
            System.out.println(node.toString() + "  " + node.getRawSignature());
        }

    }

    private static String getFileContentWithoutIfdefs(File f, IASTPreprocessorStatement[] ppStatements) throws IOException {
        StringBuffer content = new StringBuffer();
        BufferedReader reader = new BufferedReader(new FileReader(f));

        String line = reader.readLine();
        while (line != null) {

            for (IASTPreprocessorStatement ppStatement : ppStatements) {
                if (ppStatement instanceof IASTPreprocessorIfdefStatement ||
                        ppStatement instanceof IASTPreprocessorIfndefStatement ||
                        ppStatement instanceof IASTPreprocessorIfStatement ||
                        ppStatement instanceof IASTPreprocessorElseStatement ||
                        ppStatement instanceof IASTPreprocessorElifStatement ||
                        ppStatement instanceof IASTPreprocessorEndifStatement) {

                    if (line.contains(ppStatement.getRawSignature())) {
                        line = line.replace(ppStatement.getRawSignature(), "//" + ppStatement.getRawSignature().substring(2));
                        break;
                    }
                }
            }
            content.append(line + "\n");

            line = reader.readLine();
        }

        reader.close();

        return content.toString();
    }

    private Collection<ReadListener> listeners = new ArrayList<>();

    @Override
    public void addListener(ReadListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(ReadListener listener) {
        this.listeners.remove(listener);
    }


}