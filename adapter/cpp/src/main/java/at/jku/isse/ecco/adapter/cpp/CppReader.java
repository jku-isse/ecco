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
            System.out.println(file.getName());
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

                if (ppIncludeStatements != null) {
                    for (IASTPreprocessorStatement preprocessorStatement : ppIncludeStatements) {
                        if (preprocessorStatement.getContainingFilename().equals(translationUnit.getContainingFilename()) && preprocessorStatement instanceof IASTPreprocessorIncludeStatement) {
                            String includeName = preprocessorStatement.getRawSignature();
                            Artifact.Op<IncludeArtifactData> includesArtifact = this.entityFactory.createArtifact(new IncludeArtifactData(includeName));
                            Node.Op includeNode = this.entityFactory.createOrderedNode(includesArtifact);
                            includesGroupNode.addChild(includeNode);
                        }
                    }
                }
                Map<String, Integer> macroPosition = new HashMap<>();
                Map<String, Integer> definesPosition = new HashMap<>();
                ArrayList<String> macros = new ArrayList<>();
                ArrayList<Integer> lineNumbers = new ArrayList<>();
                if (ppMacroStatements != null) {
                    for (IASTPreprocessorStatement macro : ppMacroStatements) {
                        if (macro.getContainingFilename().equals(translationUnit.getContainingFilename())) {
                            macroPosition.put(macro.getRawSignature(), macro.getFileLocation().getStartingLineNumber());
                            macros.add(macro.getRawSignature());
                        }
                    }
                }


                traverseAST(translationUnit.getOriginalNode(), pluginNode, functionsGroupNode, fieldsGroupNode, true, "", lines, lineNumbers);

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
                    if (preprocessorstatement instanceof IASTPreprocessorUndefStatement)
                        definesPosition.put(preprocessorstatement.getRawSignature(), preprocessorstatement.getFileLocation().getStartingLineNumber());
                }
                List<Map.Entry<String, Integer>> list = new ArrayList<>(definesPosition.entrySet());
                list.sort(Map.Entry.comparingByValue());

                for (Map.Entry<String, Integer> entry : list) {
                    Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(entry.getKey()));
                    Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                    definesGroupNode.addChild(lineNode);
                }

            } catch (CoreException e) {
                e.printStackTrace();
                throw new EccoException("Error parsing java file.", e);
            }

        }

        return nodes;
    }


    private void traverseAST(IASTNode astNode, Node.Op classnode, Node.Op functions, Node.Op fields, final boolean saveLocationInfromtation, String indent, String[] lines, ArrayList<Integer> lineNumbers) {

        for (IASTNode child : astNode.getChildren()) {
            if (child != null && child.getContainingFilename().equals(astNode.getContainingFilename())) {
                getIdentifier(child, classnode, functions, fields, lines, lineNumbers);
            }
        }
    }


    public void getIdentifier(IASTNode node, Node.Op parentNode, Node.Op functionsNode, Node.Op fieldsNode, String[] lines, ArrayList<Integer> lineNumbers) {
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
                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(node.getRawSignature()));
                Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                fieldsNode.addChild(lineNode);
                lineNumbers.add(node.getFileLocation().getStartingLineNumber());
                lineNumbers.add(node.getFileLocation().getEndingLineNumber());
            }
            // System.out.println(node.getRawSignature());
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
            //for (IASTNode nodechild : ((IASTFunctionDefinition) node).getBody().getChildren()) {
            //    System.out.println(nodechild.copy());
            //    getIdentifier(nodechild,functionNode,functionsNode,fieldsNode, lines);
            //}
            for (IASTNode child : ((IASTFunctionDefinition) node).getBody().getChildren()) {
                //System.out.println(child.getRawSignature());
                addChildFunction(child, functionNode, functionsNode, fieldsNode, lines, lineNumbers);
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
            //TODO insert parameter types into string
            for (IASTNode nodechild : node.getChildren()) {
                addChildFunction(nodechild, functionNode, functionsNode, fieldsNode, lines, lineNumbers);
            }
            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData("}"));
            Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
            functionNode.addChild(lineNode);
            //return name + "(" + parameters + ")";
        } else if (node instanceof ICPPASTLinkageSpecification) {
            //TODO could be more extern "C" -> not a unique identifier
            //return "extern " + ((ICPPASTLinkageSpecification) node).getLiteral() + "";
        } else if (node instanceof CPPASTProblemDeclaration) {
            if (!node.getRawSignature().equals(")")) {
                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(node.getRawSignature()));
                Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                fieldsNode.addChild(lineNode);
                lineNumbers.add(node.getFileLocation().getStartingLineNumber());
                lineNumbers.add(node.getFileLocation().getEndingLineNumber());
            }
            // System.out.println(node.getRawSignature());
        } else {
            System.out.println("+++++++++++++++++++++ corner case +++++++++++ " + node.getRawSignature() + " " + node.getFileLocation().getFileName() + " " + node.getFileLocation().getStartingLineNumber());
        }
        //return node.getRawSignature();
    }

    public void addChildFunction(IASTNode node, Node.Op parentNode, Node.Op functionsNode, Node.Op fieldsNode, String[] lines, ArrayList<Integer> lineNumbers) {
        if (node instanceof IASTExpressionStatement || node instanceof CPPASTContinueStatement || node instanceof IASTDeclarationStatement || node instanceof CPPASTReturnStatement || node instanceof IASTReturnStatement || node instanceof IASTLabelStatement || node instanceof IASTGotoStatement || node instanceof CPPASTGotoStatement || node instanceof IASTBinaryExpression || node instanceof IASTFunctionCallExpression || node instanceof CPPASTBinaryExpression || node instanceof CPPASTBreakStatement) {
            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(node.getRawSignature()));
            Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
            parentNode.addChild(lineNode);
        } else if (node instanceof IASTIfStatement || node instanceof ICPPASTIfStatement) {
            /*Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(node.getRawSignature()));
            Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
            parentNode.addChild(lineNode);*/
            if (((IASTIfStatement) node).getConditionExpression() != null) {
                boolean first = true;
                Artifact.Op<BlockArtifactData> blockArtifact;
                Node.Op blockNode = null;
                String ifexpression = "";
                if (node.getFileLocation().getStartingLineNumber() == node.getFileLocation().getEndingLineNumber()) {
                    blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(node.getRawSignature()));
                    blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                    parentNode.addChild(blockNode);
                } else {
                    for (IASTNode child : ((ICPPASTIfStatement) node).getThenClause().getChildren()) {
                        if ((child instanceof CPPASTReturnStatement || child instanceof CPPASTGotoStatement || ((ICPPASTIfStatement) node).getThenClause().getChildren().length == 1) && first) {
                            if (node.getRawSignature().contains("{"))
                                ifexpression = "if(" + ((IASTIfStatement) node).getConditionExpression().getRawSignature() + ") {";
                            blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(ifexpression));
                            blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                            parentNode.addChild(blockNode);
                            addChildFunction(child, blockNode, functionsNode, fieldsNode, lines, lineNumbers);
                            if (node.getRawSignature().contains("{") && node.getRawSignature().contains("};")) {
                                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(("};")));
                                Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                                blockNode.addChild(lineNodeChild);
                            } else if (node.getRawSignature().contains("{")) {
                                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(("}")));
                                Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                                blockNode.addChild(lineNodeChild);
                            }
                        } else if (first) {
                            blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData("if(" + ((IASTIfStatement) node).getConditionExpression().getRawSignature() + ") {"));
                            blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                            parentNode.addChild(blockNode);
                            first = false;
                            addChildFunction(child, blockNode, functionsNode, fieldsNode, lines, lineNumbers);
                        } else {
                            addChildFunction(child, blockNode, functionsNode, fieldsNode, lines, lineNumbers);
                        }
                    }
                    if (!first) {
                        Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(("}")));
                        Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                        blockNode.addChild(lineNodeChild);
                    }
                }
            } else {
                if (((ICPPASTIfStatement) node).getThenClause().getChildren().length > 0) {
                    Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData("if(" + ((ICPPASTIfStatement) node).getConditionDeclaration().getRawSignature() + ") {"));
                    Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                    parentNode.addChild(blockNode);
                    for (IASTNode child : ((ICPPASTIfStatement) node).getThenClause().getChildren()) {
                        addChildFunction(child, blockNode, functionsNode, fieldsNode, lines, lineNumbers);
                    }
                    Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(("}")));
                    Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                    blockNode.addChild(lineNodeChild);
                } else {
                    Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData("if(" + ((ICPPASTIfStatement) node).getConditionDeclaration().getRawSignature() + ") "));
                    Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                    parentNode.addChild(blockNode);
                    for (IASTNode child : ((ICPPASTIfStatement) node).getThenClause().getChildren()) {
                        addChildFunction(child, blockNode, functionsNode, fieldsNode, lines, lineNumbers);
                    }
                }
            }
        } else if (node instanceof IASTWhileStatement) {

            if (((IASTWhileStatement) node).getCondition() != null) {
                Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData("while( " + ((IASTWhileStatement) node).getCondition().getRawSignature() + " ) {"));
                Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                parentNode.addChild(blockNode);
                for (IASTNode child : ((ICPPASTWhileStatement) node).getBody().getChildren()) {
                    addChildFunction(child, blockNode, functionsNode, fieldsNode, lines, lineNumbers);
                }
                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(("}")));
                Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                blockNode.addChild(lineNodeChild);
            } else {
                Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData("while( " + ((ICPPASTWhileStatement) node).getConditionDeclaration().getRawSignature() + ") {"));
                Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                parentNode.addChild(blockNode);
                for (IASTNode child : ((ICPPASTWhileStatement) node).getBody().getChildren()) {
                    addChildFunction(child, blockNode, functionsNode, fieldsNode, lines, lineNumbers);
                }
                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(("}")));
                Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                blockNode.addChild(lineNodeChild);
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
            Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData("for(" + init + " " + condition + "; " + iteration + "){"));
            Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
            parentNode.addChild(blockNode);
            for (IASTNode child : ((IASTForStatement) node).getBody().getChildren()) {
                addChildFunction(child, blockNode, functionsNode, fieldsNode, lines, lineNumbers);
            }
            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(("}")));
            Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
            blockNode.addChild(lineNodeChild);
        } else if (node instanceof IASTSwitchStatement || node instanceof CPPASTSwitchStatement) {
            if (((IASTSwitchStatement) node).getControllerExpression() != null) {
                Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData("switch(" + ((IASTSwitchStatement) node).getControllerExpression().getRawSignature() + "){"));
                Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                parentNode.addChild(blockNode);
                Node.Op blockChildNode = blockNode;
                for (IASTNode child : ((IASTSwitchStatement) node).getBody().getChildren()) {
                    if (child instanceof CPPASTCaseStatement || node instanceof CPPASTDefaultStatement) {
                        blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(((CPPASTCaseStatement) child).getRawSignature() + " {"));
                        blockChildNode = this.entityFactory.createOrderedNode(blockArtifact);
                        blockNode.addChild(blockChildNode);
                    } else {
                        addChildFunction(child, blockChildNode, functionsNode, fieldsNode, lines, lineNumbers);
                    }
                }
                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(("}")));
                Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                blockNode.addChild(lineNodeChild);
            } else {
                Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData("switch(" + ((ICPPASTSwitchStatement) node).getControllerDeclaration().getRawSignature() + "){"));
                Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
                parentNode.addChild(blockNode);
                Node.Op blockChildNode = null;
                for (IASTNode child : ((ICPPASTSwitchStatement) node).getBody().getChildren()) {
                    if (child instanceof CPPASTCaseStatement || node instanceof CPPASTDefaultStatement) {
                        blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(((CPPASTCaseStatement) child).getRawSignature() + " {"));
                        blockChildNode = this.entityFactory.createOrderedNode(blockArtifact);
                        blockNode.addChild(blockChildNode);
                    } else {
                        addChildFunction(child, blockChildNode, functionsNode, fieldsNode, lines, lineNumbers);
                    }
                }
                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(("}")));
                Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
                blockNode.addChild(lineNodeChild);
            }
        } else if (node instanceof CPPASTCompoundStatement) {
            for (IASTNode child : node.getChildren()) {
                addChildFunction(child, parentNode, functionsNode, fieldsNode, lines, lineNumbers);
            }
            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(("}")));
            Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
            parentNode.addChild(lineNodeChild);
        } else if (node instanceof CPPASTDefaultStatement) {
            Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(((CPPASTDefaultStatement) node).getRawSignature() + " {"));
            Node.Op blockChildNode = this.entityFactory.createOrderedNode(blockArtifact);
            parentNode.addChild(blockChildNode);
        } else if (node instanceof IASTDoStatement) {
            Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData("do{"));
            Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
            parentNode.addChild(blockNode);
            for (IASTNode child : node.getChildren()) {
                addChildFunction(child, blockNode, functionsNode, fieldsNode, lines, lineNumbers);
            }
            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData("} while( " + ((IASTDoStatement) node).getCondition().getRawSignature() + ")"));
            Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
            blockNode.addChild(lineNodeChild);
            //return "do while(" + ((IASTDoStatement) node).getCondition().getRawSignature() + ")";
        } else if (node instanceof CPPASTNullStatement) {

        } else if (node instanceof CPPASTUnaryExpression) {
            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(node.getRawSignature() + ";"));
            Node.Op lineNodeChild = this.entityFactory.createOrderedNode(lineArtifact);
            parentNode.addChild(lineNodeChild);
        } else if (node instanceof CPPASTProblemStatement) {
            if (!node.getRawSignature().equals(")") && node.getRawSignature().length() > 1) {
                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(node.getRawSignature()));
                Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                parentNode.addChild(lineNode);
            }
            //System.out.println(node.getRawSignature());
        } else {
            //System.out.println(node.getRawSignature());
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