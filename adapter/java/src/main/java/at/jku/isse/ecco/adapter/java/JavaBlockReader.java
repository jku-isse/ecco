package at.jku.isse.ecco.adapter.java;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.google.inject.Inject;
import com.sun.org.apache.xerces.internal.dom.ChildNode;
import jdk.nashorn.internal.ir.ExpressionStatement;
import jdk.nashorn.internal.ir.TryNode;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class JavaBlockReader implements ArtifactReader<Path, Set<Node.Op>> {

    private final EntityFactory entityFactory;

    @Inject
    public JavaBlockReader(EntityFactory entityFactory) {
        checkNotNull(entityFactory);

        this.entityFactory = entityFactory;
    }

    @Override
    public String getPluginId() {
        return JavaPlugin.class.getName();
    }

    private static Map<Integer, String[]> prioritizedPatterns;

    static {
        prioritizedPatterns = new HashMap<>();
        prioritizedPatterns.put(Integer.MAX_VALUE, new String[]{"**.java"});
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

        for (Path path : input) {
            Path resolvedPath = base.resolve(path);

            Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
            Node.Op pluginNode = this.entityFactory.createNode(pluginArtifact);
            nodes.add(pluginNode);


            // TODO: user JavaParser to create remaining tree

            try {
                CompilationUnit cu = JavaParser.parse(resolvedPath);

                String packageName = "";
                if (cu.getPackageDeclaration().isPresent())
                    packageName = cu.getPackageDeclaration().get().getName().toString();


                for (TypeDeclaration<?> typeDeclaration : cu.getTypes()) {

                    String className = typeDeclaration.getName().toString();
                    Artifact.Op<ClassArtifactData> classArtifact = this.entityFactory.createArtifact(new ClassArtifactData(packageName + "." + className));
                    Node.Op classNode = this.entityFactory.createNode(classArtifact);
                    pluginNode.addChild(classNode);

                    //add classChild from imports
                    for (ImportDeclaration importDeclaration : cu.getImports()) {
                        String importName = "import " + importDeclaration.getName().asString();
                        Artifact.Op<ImportsArtifactData> importsArtifact = this.entityFactory.createArtifact(new ImportsArtifactData(importName));
                        Node.Op importNode = this.entityFactory.createNode(importsArtifact);
                        classNode.addChild(importNode);
                    }

                    for (BodyDeclaration<?> node : typeDeclaration.getMembers()) {
                        //get enums of a class
                        if (node instanceof EnumDeclaration) {
                            String enumName = ((EnumDeclaration) node).getName().toString();
                            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(enumName));
                            Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
                            classNode.addChild(lineNode);
                            int entries = ((EnumDeclaration) node).getEntries().size();
                            for (int i = 0; i < entries; i++) {
                                String enumInitializerName = ((EnumDeclaration) node).getEntries().get(i).getName().toString();
                                Artifact.Op<LineArtifactData> lineArtifactChild = this.entityFactory.createArtifact(new LineArtifactData(enumInitializerName));
                                Node.Op lineNodeChild = this.entityFactory.createNode(lineArtifactChild);
                                lineNode.addChild(lineNodeChild);
                                //System.out.println("entrada: "+((EnumDeclaration) node).getEntries().get(i).getName());
                            }

                            //System.out.println("nome: "+((EnumDeclaration) node).getName());
                        } else
                            //add classChild from fields
                            if (node instanceof FieldDeclaration) {

                                if (((FieldDeclaration) node).getVariables().get(0).getInitializer().isPresent()) {
                                    if (((FieldDeclaration) node).getVariables().get(0).getInitializer().get() instanceof ArrayInitializerExpr) {
                                        String stmt = "Array " + ((FieldDeclaration) node).getVariables().get(0).getName().toString();
                                        Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(stmt));
                                        Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                                        classNode.addChild(lineNode);
                                        for (com.github.javaparser.ast.Node childNode : ((FieldDeclaration) node).getVariables().get(0).getChildNodes().get(2).getChildNodes()) {
                                            String stmtChild = childNode.toString();
                                            Artifact.Op<LineArtifactData> lineArtifactChild = this.entityFactory.createArtifact(new LineArtifactData(stmtChild));
                                            Node.Op lineNodeChild = this.entityFactory.createNode(lineArtifactChild);
                                            lineNode.addChild(lineNodeChild);
                                        }

                                    }
                                } else {

                                    String fieldOfClass = node.removeComment().toString();
                                    Artifact.Op<FieldArtifactData> fieldArtifact = this.entityFactory.createArtifact(new FieldArtifactData(fieldOfClass));
                                    Node.Op fieldNode = this.entityFactory.createNode(fieldArtifact);
                                    classNode.addChild(fieldNode);
                                }
                            } else
                                //add classChild from constructorMethod
                                if (node instanceof ConstructorDeclaration) {
                                    String methodSignature = ((ConstructorDeclaration) node).getSignature().toString();
                                    Artifact.Op<MethodArtifactData> methodArtifact = this.entityFactory.createArtifact(new MethodArtifactData(methodSignature));
                                    Node.Op methodNode = this.entityFactory.createOrderedNode(methodArtifact);
                                    classNode.addChild(methodNode);
                                    if(((ConstructorDeclaration) node).getBody().getStatements().size() > 0) {
                                        for(Statement stm : ((ConstructorDeclaration) node).getBody().getStatements()){
                                            addMethodChild(stm,methodNode);
                                        }
                                    }
                                }
                    }

                    //add classChild from Methods
                    for (MethodDeclaration methodDeclaration : typeDeclaration.getMethods()) {
                        String methodSignature = methodDeclaration.getSignature().toString();
                        Artifact.Op<MethodArtifactData> methodArtifact = this.entityFactory.createArtifact(new MethodArtifactData(methodSignature));
                        Node.Op methodNode = this.entityFactory.createOrderedNode(methodArtifact);
                        classNode.addChild(methodNode);
                        Optional<BlockStmt> block = methodDeclaration.getBody();
                        Boolean hasAnyStatement = false;
                        if (methodDeclaration.getBody().isPresent()) {
                            for (int i = 0; i < block.get().getStatements().size(); i++) {
                                Statement blockStatement = block.get().getStatements().get(i);
                                addMethodChild(blockStatement, methodNode);
                                //if(statereturn instanceof Statement){
                                //    addMethodChild(statereturn, methodNode);
                                // }
                                   /*

                                   } else if (block.get().getStatements().get(i).getChildNodes().get(j) instanceof EnumDeclaration) {

                                   } else if (block.get().getStatements().get(i).getChildNodes().get(j) instanceof FieldDeclaration) {
                                   }*/
                            }


                        }

                    }
                }


							/*
							NodeList<Statement> statements = block.get().getStatements();
							for (Statement tmp : statements) {
								tmp.getChildNodes().get(0).getChildNodes().get(0);
								String line = tmp.removeComment().toString();
								Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(line));
								Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
								methodNode.addChild(lineNode);
							}*/


                //}
                //}

                //print tree
                //System.out.println("\n Class: "+classNode.toString());
                //for (int i = 0; i < classNode.getChildren().size(); i++) { //imports, fields and methods declaration
                //    //	System.out.println("\n Class child: "+classNode.getChildren().get(i));
                //    if (classNode.getChildren().get(i).getChildren().size() > 0) { //methods statements and methods fields declaration
                //        for (int j = 0; j < classNode.getChildren().get(i).getChildren().size(); j++) {
                //            //System.out.println("\n class child: "+classNode.getChildren().get(i).getChildren().get(j));
                //            for (int k = 0; k < classNode.getChildren().get(i).getChildren().get(j).getChildren().size(); k++) {
                //	System.out.println("\n Statement: "+classNode.getChildren().get(i).getChildren().get(j).getChildren().get(k));
                //            }
                //        }
                //    }
                // }

                //System.out.println(typeDeclaration.getMethods().get(0).getBody().get().toString());
                //System.out.println(Arrays.asList(typeDeclaration.getMethods().get(0).getBody().get().toString().split("\\r?\\n")));

                // }
                // }

            } catch (IOException e) {
                e.printStackTrace();
                throw new EccoException("Error parsing java file.", e);
            }

        }
        return nodes;
    }


    public void addMethodChild(Statement statement, Node.Op methodNode) {
        if (statement instanceof IfStmt) {
            String stmt = "if (" + statement.getChildNodes().get(0).toString() + ")";
            Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(stmt));
            Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
            methodNode.addChild(blockNode);

            if (statement.getChildNodes().size() > 1) {
                if (statement.getChildNodes().get(1) instanceof BreakStmt) {
                    BreakStmt breakStatement = ((BreakStmt) statement.getChildNodes().get(1));
                    addMethodChild(breakStatement, blockNode);
                } else if (statement.getChildNodes().get(1) instanceof BlockStmt) {
                    BlockStmt blockStatement = ((BlockStmt) statement.getChildNodes().get(1));
                    addMethodChild(blockStatement, blockNode);
                }else if (statement.getChildNodes().get(1) instanceof Statement) {
                    Statement st = (Statement) statement.getChildNodes().get(1);
                    addMethodChild(st, blockNode);
                }

                //    String stmt = statement.getChildNodes().get(j).toString();
                //    Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(stmt));
                //    Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
                //    blockNode.addChild(lineNode);
            }
            //   String ifstmt = statement.toString();//.getChildNodes().get(j).toString();
            //   Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(ifstmt));
            //   blockNode = this.entityFactory.createNode(blockArtifact);
            //    methodNode.addChild(blockNode);
        } else if (statement instanceof ForStmt) {
            /*Node.Op blockNode = null;
            int endFor = statement.toString().indexOf("{");
            String forstmt = statement.toString().substring(0, endFor - 1);
            Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(forstmt));
            blockNode = this.entityFactory.createNode(blockArtifact);
            methodNode.addChild(blockNode);
            ForStmt forStmt = statement.asForStmt();
            for (int j = 0; j < forStmt.getBody().getChildNodes().size(); j++) {
                String stmt = forStmt.getBody().getChildNodes().get(j).toString();
                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(stmt));
                Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
                blockNode.addChild(lineNode);
            }*/
            String stmt = "for(" + statement.getChildNodes().get(0).toString() + "; "; //+ statement.getChildNodes().get(1).toString() + "; " + statement.getChildNodes().get(2).toString() + ")";
            Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(stmt));
            Node.Op blockNode = this.entityFactory.createNode(blockArtifact);
            methodNode.addChild(blockNode);

            if (statement.getChildNodes().size() > 3) {
                for (com.github.javaparser.ast.Node stmtaux : ((ForStmt) statement).getBody().getChildNodes()) {
                    if (stmtaux instanceof BlockStmt) {
                        BlockStmt blockStatement = (BlockStmt) stmtaux;
                        addMethodChild(blockStatement, blockNode);
                    } else if (stmtaux instanceof Statement) {
                        Statement stmtchild = (Statement) stmtaux;
                        addMethodChild(stmtchild, blockNode);
                    }

                }
            }
        } else if (statement instanceof DoStmt) {
            Node.Op blockNode = null;
            int endDo = statement.toString().indexOf("{");
            String dostmt = statement.toString().substring(0, endDo - 1) + " (" + ((DoStmt) statement).getCondition().toString() + " )";
            Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(dostmt));
            blockNode = this.entityFactory.createNode(blockArtifact);
            methodNode.addChild(blockNode);
            DoStmt doStmt = statement.asDoStmt();
            if (doStmt.getChildNodes().size() > 1) {
                if (doStmt.getChildNodes().get(0) instanceof Statement) {
                    Statement statementChild = (Statement) doStmt.getChildNodes().get(0);
                    addMethodChild(statementChild, blockNode);
                } else if (doStmt.getChildNodes().get(0) instanceof BlockStmt) {
                    BlockStmt statementChild = (BlockStmt) doStmt.getChildNodes().get(0);
                    addMethodChild(statementChild, blockNode);
                }
            }

            //Node.Op blockNode = null;
            //int endDo = statement.toString().indexOf("{");
            //String dostmt = statement.toString().substring(0, endDo - 1);
            //Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(dostmt));
            //blockNode = this.entityFactory.createNode(blockArtifact);
            //methodNode.addChild(blockNode);
            //DoStmt doStmt = statement.asDoStmt();
            //for (int j = 0; j < doStmt.getBody().getChildNodes().size(); j++) {
            //    String stmt = doStmt.getBody().getChildNodes().get(j).toString();
            //    Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(stmt));
            //    Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
            //    blockNode.addChild(lineNode);
            //}
            //String condition = doStmt.getCondition().toString();
            //Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(condition));
            //Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
            //blockNode.addChild(lineNode);
        } else if (statement instanceof ForeachStmt) {
            /*Node.Op blockNode = null;
            int foreach = statement.toString().indexOf("{");
            String foreachstmt = statement.toString().substring(0, foreach - 1);
            Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(foreachstmt));
            blockNode = this.entityFactory.createNode(blockArtifact);
            methodNode.addChild(blockNode);
            ForeachStmt foreachStmt = statement.asForeachStmt();
            for (int j = 0; j < foreachStmt.getBody().getChildNodes().size(); j++) {
                String stmt = foreachStmt.getBody().getChildNodes().get(j).toString();
                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(stmt));
                Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
                blockNode.addChild(lineNode);
            }*/
            String stmt = "for (" + statement.getChildNodes().get(0).toString() + " : " + statement.getChildNodes().get(1).toString() + ")";
            Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(stmt));
            Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
            methodNode.addChild(blockNode);

            if (statement.getChildNodes().size() > 2) {
                for (int l = 2; l < statement.getChildNodes().size(); l++) {
                    BlockStmt blockStatement = ((BlockStmt) statement.getChildNodes().get(l));
                    addMethodChild(blockStatement, blockNode);
                }
            }

        } else if (statement instanceof ReturnStmt) {
            /*Node.Op blockNode = null;
            int inicio = statement.asReturnStmt().toString().indexOf("return");
            String returnstmt = statement.asReturnStmt().toString().substring(inicio);
            Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(returnstmt));
            blockNode = this.entityFactory.createNode(blockArtifact);
            methodNode.addChild(blockNode);
             */
            int inicio = statement.asReturnStmt().toString().indexOf("return");
            String stmt = statement.asReturnStmt().toString().substring(inicio);
            Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(stmt));
            Node.Op blockNode = this.entityFactory.createNode(blockArtifact);
            methodNode.addChild(blockNode);

            if (statement.getChildNodes().size() > 1) {
                if (statement.getChildNodes().get(1) instanceof BlockComment) {
                    //not add as child the comments
                } else {
                    BlockStmt blockStatement = ((BlockStmt) statement.getChildNodes().get(1));
                    addMethodChild(blockStatement, blockNode);
                }
            }

        } else if (statement instanceof SwitchStmt) {
            /*Node.Op blockNode = null;
            int end = statement.asSwitchStmt().toString().indexOf("{");
            String switchstmt = statement.asSwitchStmt().toString().substring(0, end - 1);
            Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(switchstmt));
            blockNode = this.entityFactory.createNode(blockArtifact);
            methodNode.addChild(blockNode);
            SwitchStmt switchStmt = statement.asSwitchStmt();
            for (int j = 0; j < switchStmt.getEntries().size(); j++) {
                int endSwitchEntry = switchStmt.getEntries().get(j).asSwitchEntryStmt().toString().indexOf(":");
                String entry = switchStmt.getEntries().get(j).asSwitchEntryStmt().toString().substring(0, endSwitchEntry);
                Artifact.Op<BlockArtifactData> blockArtifactEntry = this.entityFactory.createArtifact(new BlockArtifactData(entry));
                Node.Op blockNodeEntry = this.entityFactory.createNode(blockArtifactEntry);
                blockNode.addChild(blockNodeEntry);
                for (Statement statement2 : switchStmt.getEntries().get(j).getStatements()) {
                    String stmt = statement2.toString();
                    Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(stmt));
                    Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
                    blockNodeEntry.addChild(lineNode);
                }
            }

             */
            int end = statement.asSwitchStmt().toString().indexOf("{");
            String stmt = statement.asSwitchStmt().toString().substring(0, end - 1);
            Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(stmt));
            Node.Op blockNode = this.entityFactory.createNode(blockArtifact);
            methodNode.addChild(blockNode);

            if (((SwitchStmt) statement).getEntries().size() > 1) {
                for (SwitchEntryStmt entryStmt : ((SwitchStmt) statement).getEntries()) {
                    SwitchEntryStmt switchEntryStmt = entryStmt;
                    int endSwitchEntry = switchEntryStmt.toString().indexOf(":");
                    String entry = switchEntryStmt.toString().substring(0, endSwitchEntry);
                    Artifact.Op<BlockArtifactData> blockArtifactEntry = this.entityFactory.createArtifact(new BlockArtifactData(entry));
                    Node.Op blockNodeEntry = this.entityFactory.createOrderedNode(blockArtifactEntry);
                    blockNode.addChild(blockNodeEntry);
                    addMethodChild(switchEntryStmt, blockNodeEntry);
                }

            }

        } else if (statement instanceof ThrowStmt) {
            /*Node.Op lineNode = null;
            String throwstmt = statement.asThrowStmt().toString();
            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(throwstmt));
            lineNode = this.entityFactory.createNode(lineArtifact);
            methodNode.addChild(lineNode);

             */
            String stmt = statement.getChildNodes().get(0).toString();
            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(stmt));
            Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
            methodNode.addChild(lineNode);
            if (statement.getChildNodes().size() > 1) {
                if (statement.getChildNodes().get(1) instanceof Statement) {
                    Statement statementChild = (Statement) statement.getChildNodes().get(1);
                    addMethodChild(statementChild, methodNode);
                } else if (statement.getChildNodes().get(1) instanceof BlockStmt) {
                    BlockStmt statementChild = (BlockStmt) statement.getChildNodes().get(1);
                    addMethodChild(statementChild, methodNode);
                }
            }

        } else if (statement instanceof TryStmt) {
            /*Node.Op blockNode = null;
            Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData("try"));
            blockNode = this.entityFactory.createNode(blockArtifact);
            methodNode.addChild(blockNode);
            TryStmt tryStmt = statement.asTryStmt();
            for (int j = 0; j < tryStmt.getTryBlock().getStatements().size(); j++) {
                String stmt = tryStmt.getTryBlock().getStatements().get(j).toString();
                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(stmt));
                Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
                blockNode.addChild(lineNode);
            }
            Node.Op blockNodeCatch = null;
            Artifact.Op<BlockArtifactData> catchBlockArtifact = this.entityFactory.createArtifact(new BlockArtifactData("catch"));
            blockNodeCatch = this.entityFactory.createNode(catchBlockArtifact);
            blockNode.addChild(blockNodeCatch);
            for (int k = 0; k < tryStmt.getCatchClauses().size(); k++) {
                for (int a = 0; a < tryStmt.getCatchClauses().get(k).getChildNodes().size(); a++) {
                    String catchclause = tryStmt.getCatchClauses().get(k).getChildNodes().get(a).toString();
                    Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(catchclause));
                    Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
                    blockNodeCatch.addChild(lineNode);
                }
            }*/
            Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData("try"));
            Node.Op blockNode = this.entityFactory.createNode(blockArtifact);
            methodNode.addChild(blockNode);

            if (statement.getChildNodes().size() > 1) {
                for (com.github.javaparser.ast.Node childNode : statement.getChildNodes()) {
                    if (childNode instanceof BlockStmt) {
                        BlockStmt statementChild = (BlockStmt) childNode;
                        addMethodChild(statementChild, blockNode);
                    } else if (childNode instanceof CatchClause) {
                        Artifact.Op<BlockArtifactData> catchArtifact = this.entityFactory.createArtifact(new BlockArtifactData(childNode.toString().substring(0, childNode.toString().indexOf(")") + 1)));
                        Node.Op catchNode = this.entityFactory.createNode(catchArtifact);
                        methodNode.addChild(catchNode);
                        if (((CatchClause) childNode).getBody() instanceof BlockStmt) {
                            BlockStmt blockStatement = ((CatchClause) childNode).getBody();
                            addMethodChild(blockStatement, catchNode);
                        } else if (((CatchClause) childNode).getBody() instanceof Statement) {
                            Statement stmtchild = ((CatchClause) childNode).getBody();
                            addMethodChild(stmtchild, catchNode);
                        }

                    }
                }
            }

        } else if (statement instanceof WhileStmt) {
            /*Node.Op blockNode = null;
            int whilend = statement.toString().indexOf("{");
            String whilestmt = statement.toString().substring(0, whilend - 1);
            Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(whilestmt));
            blockNode = this.entityFactory.createNode(blockArtifact);
            methodNode.addChild(blockNode);
            WhileStmt whileStmt = statement.asWhileStmt();
            for (int j = 0; j < whileStmt.getBody().getChildNodes().size(); j++) {
                String stmt = whileStmt.getBody().getChildNodes().get(j).toString();
                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(stmt));
                Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
                blockNode.addChild(lineNode);
            }*/
            //int whilend = statement.toString().indexOf("{");
            String whilestmt = "while("+((WhileStmt) statement).getCondition().toString()+")";
            Artifact.Op<BlockArtifactData> blockArtifact = this.entityFactory.createArtifact(new BlockArtifactData(whilestmt));
            Node.Op blockNode = this.entityFactory.createOrderedNode(blockArtifact);
            methodNode.addChild(blockNode);

            if (statement.getChildNodes().size() > 1) {
                for (com.github.javaparser.ast.Node stmtaux : ((WhileStmt) statement).getBody().getChildNodes()) {
                    if (stmtaux instanceof BlockStmt) {
                        BlockStmt blockStatement = (BlockStmt) stmtaux;
                        addMethodChild(blockStatement, blockNode);
                    } else if (stmtaux instanceof Statement) {
                        Statement stmtchild = (Statement) stmtaux;
                        addMethodChild(stmtchild, blockNode);
                    }

                }
            }

        } else if (statement instanceof BlockStmt) {
            if (statement.getChildNodes().size() > 0) {
                if (statement.getChildNodes().get(0) instanceof ExpressionStmt) {
                    ExpressionStmt exp = (ExpressionStmt) statement.getChildNodes().get(0);
                    VariableDeclarationExpr expr;
                    if ((exp.getExpression() instanceof VariableDeclarationExpr)) {
                        expr = (VariableDeclarationExpr) exp.getExpression();

                        if (expr.getVariable(0).getInitializer().isPresent()) {
                            String stmt = "Array " + expr.getVariable(0).getName().toString();
                            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(stmt));
                            Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                            methodNode.addChild(lineNode);
                            for (com.github.javaparser.ast.Node childNode : expr.getVariable(0).getChildNodes().get(2).getChildNodes()) {
                                String stmtChild = childNode.toString();
                                Artifact.Op<LineArtifactData> lineArtifactChild = this.entityFactory.createArtifact(new LineArtifactData(stmtChild));
                                Node.Op lineNodeChild = this.entityFactory.createNode(lineArtifactChild);
                                lineNode.addChild(lineNodeChild);
                            }

                        }
                    }
                } else {
                    String stmt = statement.getChildNodes().get(0).toString();
                    Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(stmt));
                    Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
                    methodNode.addChild(lineNode);

                    if (statement.getChildNodes().size() > 1) {
                        if (statement.getChildNodes().get(1) instanceof Statement) {
                            Statement statementChild = (Statement) statement.getChildNodes().get(1);
                            addMethodChild(statementChild, methodNode);
                        } else if (statement.getChildNodes().get(1) instanceof BlockStmt) {
                            BlockStmt statementChild = (BlockStmt) statement.getChildNodes().get(1);
                            addMethodChild(statementChild, methodNode);
                        }

                    }
                }
            } else {
                String stmt = statement.toString();
                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(stmt));
                Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
                methodNode.addChild(lineNode);
            }
        } else if (statement instanceof ExpressionStmt) {
            String stmt = statement.getChildNodes().get(0).toString();
            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(stmt));
            Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
            methodNode.addChild(lineNode);
            if (statement.getChildNodes().size() > 1) {
                if (statement.getChildNodes().get(1) instanceof Statement) {
                    Statement statementChild = (Statement) statement.getChildNodes().get(1);
                    addMethodChild(statementChild, methodNode);
                } else if (statement.getChildNodes().get(1) instanceof BlockStmt) {
                    BlockStmt statementChild = (BlockStmt) statement.getChildNodes().get(1);
                    addMethodChild(statementChild, methodNode);
                }
            }
        } else if (statement instanceof SwitchEntryStmt) {

            if (((SwitchEntryStmt) statement).getStatements().size() > 1) {
                for (Statement switchentry : ((SwitchEntryStmt) statement).getStatements()) {
                    if (switchentry instanceof Statement) {
                        Statement statementChild = switchentry;
                        addMethodChild(statementChild, methodNode);
                    } else if (switchentry instanceof BlockStmt) {
                        BlockStmt statementChild = (BlockStmt) switchentry;
                        addMethodChild(statementChild, methodNode);
                    }
                }
            }
        } else if (statement instanceof BreakStmt) {
            String stmt = "break";
            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(stmt));
            Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
            methodNode.addChild(lineNode);

        }else if(statement instanceof ExplicitConstructorInvocationStmt){
                String stmt = statement.toString();
                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(stmt));
                Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
                methodNode.addChild(lineNode);
        }

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
