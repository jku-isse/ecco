package at.jku.isse.ecco.adapter.runtime;

import at.jku.isse.ecco.adapter.ArtifactWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.runtime.data.*;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.service.listener.WriteListener;
import at.jku.isse.ecco.tree.Node;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParseStart;
import com.github.javaparser.StringProvider;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.utils.Pair;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static com.github.javaparser.ParseStart.COMPILATION_UNIT;

public class RuntimeWriter implements ArtifactWriter<Set<Node>, Path> {

    @Override
    public String getPluginId() {
        return RuntimePlugin.class.getName();
    }

    public ArrayList<String> methods = new ArrayList<>();

    //public String dir = "";

    @Override
    public Path[] write(Set<Node> input) {
        return this.write(Paths.get("."), input);
    }

    String[] code = {""};
    String[] imports = {""};
    String[] packageName = {""};
    String[] javaDoc = {""};
    String[] fields = {""};

    @Override
    public Path[] write(Path base, Set<Node> input) {
        Path[] toreturn = input.parallelStream().map(node -> {
            try {
                imports[0] = "";
                packageName[0] = "";
                javaDoc[0] = "";
                //dir = "C:\\Users\\gabil\\Desktop\\teste\\ActualECCO\\Method_comparison\\results\\"+ base.getFileName().toString()+".txt";
                return processNode(node, base);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }).filter(Objects::nonNull).toArray(Path[]::new);
        if (toreturn.length != input.size())
            throw new IllegalStateException("Not all files could be written!");
        return toreturn;
    }

    /**
     * @param baseNode The base node which should be processed
     * @param basePath The base path (need to parse package hierarchy
     * @return The path were the file got placed
     */
    private Path processNode(Node baseNode, Path basePath) throws IOException {
        if (!(baseNode.getArtifact().getData() instanceof PluginArtifactData)) return null;
        PluginArtifactData rootData = (PluginArtifactData) baseNode.getArtifact().getData();
        final List<? extends Node> children = baseNode.getChildren();
        if (children.size() < 1)
            return null;

        Node childNode = null;
        Path returnPath = basePath.resolve(rootData.getPath());
        if (children.size() > 1) {
            int last = children.size() - 1;
            for (int i = 0; i < children.size(); i++) {
                childNode = children.get(i);
                if (i == 0) {
                    //    code[0] = ((Node) childNode).getArtifact().getData().toString() + "\n"; //"// Hey, this is a comment\n\n\n// Another one\n\nclass A { }";
                    //else if (i == 1)
                    //    code[0] = imports[0] + javaDoc[0] + code[0] + "\n" + ((Node) childNode).getArtifact().getData().toString() + "\n";
                    //else
                    //    code[0] = code[0] + "\n" + ((Node) childNode).getArtifact().getData().toString() + "\n";
                    packageName[0] = childNode.getArtifact().toString().substring(0, childNode.getArtifact().toString().lastIndexOf("."));
                    if (childNode.getArtifact().getData() instanceof ClassArtifactData) {
                        if (childNode.getChildren().size() > 0) {
                            for (Node node : childNode.getChildren()) {
                                visitingNodes(node);
                            }
                        }
                    }
                    code[0] = imports[0] + "\n" + ((ClassArtifactData) childNode.getArtifact().getData()).getClassDeclaration() + "\n" + fields[0] + "\n" + code[0];
                } else if (i < last) {
                    if (childNode.getArtifact().getData() instanceof ClassArtifactData) {
                        code[0] = code[0] + "\n" + ((ClassArtifactData) childNode.getArtifact().getData()).getClassDeclaration() + "\n";
                        if (childNode.getChildren().size() > 0) {
                            for (Node node : childNode.getChildren()) {
                                visitingNodes(node);
                            }
                        }
                    }
                    code[0] = code[0] + "}\n";
                } else if (i == last) {
                    if (childNode.getArtifact().getData() instanceof ClassArtifactData) {
                        code[0] = code[0] + "\n" + ((ClassArtifactData) childNode.getArtifact().getData()).getClassDeclaration() + "\n";
                        if (childNode.getChildren().size() > 0) {
                            for (Node node : childNode.getChildren()) {
                                visitingNodes(node);
                            }
                        }
                    }
                    code[0] = "package " + packageName[0] + ";\n" + code[0] + "}\n}\n";
                    //these lines are used when the java lexical preserver is desired to write the files
                    //Pair<ParseResult<CompilationUnit>, LexicalPreservingPrinter> result = LexicalPreservingPrinter.setup(COMPILATION_UNIT, new StringProvider(code[0]));
                    //CompilationUnit cu = result.a.getResult().get();
                    //cu.setPackageDeclaration(packageName[0]);
                    try (BufferedWriter writer = Files.newBufferedWriter(returnPath, StandardCharsets.UTF_8)) {
                        writer.write(code[0]);
                        //writer.write(cu.toString());
                    } catch (IOException x) {
                        System.err.format("IOException: %s%n", x);
                    }
                    code = new String[]{""};
                    packageName = new String[]{""};
                    imports = new String[]{""};
                    fields = new String[]{""};
                }
            }
        } else {
            childNode = children.get(0);

            packageName[0] = childNode.getArtifact().toString().substring(0, childNode.getArtifact().toString().lastIndexOf("."));
            //code[0] = "\n" + ((ClassArtifactData) childNode.getArtifact().getData()).getClassDeclaration() + "\n";//"public class "+ childNode.getArtifact().toString().substring(childNode.getArtifact().toString().lastIndexOf(".")+1)+"{";
            //code[0] = ((Node) childNode).getArtifact().getData().toString() + "\n"; //"// Hey, this is a comment\n\n\n// Another one\n\nclass A { }";
            if (childNode.getArtifact().getData() instanceof ClassArtifactData) {
                if (childNode.getChildren().size() > 0) {
                    for (Node node : childNode.getChildren()) {
                        visitingNodes(node);
                    }
                }
            }

            code[0] = "package " + packageName[0] + ";\n" + imports[0] + "\n" + ((ClassArtifactData) childNode.getArtifact().getData()).getClassDeclaration() + "\n" + fields[0] + "\n" + code[0] + "}\n";
            //these lines are used when the java lexical preserver is desired to write the files
            //Pair<ParseResult<CompilationUnit>, LexicalPreservingPrinter> result = LexicalPreservingPrinter.setup(COMPILATION_UNIT, new StringProvider(code[0]));
            //CompilationUnit cu = result.a.getResult().get();
            //cu.setPackageDeclaration(packageName[0]);
            //LexicalPreservingPrinter lpp = result.b;
            try (BufferedWriter writer = Files.newBufferedWriter(returnPath, StandardCharsets.UTF_8)) {
                //writer.write(cu.toString());
                writer.write(code[0]);
            } catch (IOException x) {
                System.err.format("IOException: %s%n", x);
            }

            code = new String[]{""};
            packageName = new String[]{""};
            imports = new String[]{""};
            fields = new String[]{""};

        }
        //these lines were added to compute the ground truth at method level
        //try {
        //    Files.write(Paths.get((dir)), methods.stream().map(Object::toString).collect(Collectors.toList()));
        //} catch (IOException e) {
        //    e.printStackTrace();
        //}

        return returnPath;
    }

    public void visitingNodesField(Node node) {
        if ((node.getArtifact().getData() instanceof LineArtifactData)) {
            fields[0] += ((LineArtifactData) node.getArtifact().getData()).getLine() + "\n";
            if (node.getChildren().size() > 0) {
                for (Node nodechildnode : node.getChildren()) {
                    visitingNodesField(nodechildnode);
                }
            }
        }
    }

    public void visitingNodes(Node childNode) {
        if ((childNode.getArtifact().getData() instanceof ClassArtifactData)) {
            //final ClassArtifactData artifactData = (ClassArtifactData) childNode.getArtifact().getData();
            code[0] += ((ClassArtifactData) childNode.getArtifact().getData()).getClassDeclaration();//artifactData.toString() + "{\n";
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node);
                }
            }
            code[0] += "}\n";
        } else if (childNode.getArtifact().toString().equals("IMPORTS") || childNode.getArtifact().toString().equals("METHODS") || childNode.getArtifact().toString().equals("ENUMS")) {
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node);
                }
            }
        } else if (childNode.getArtifact().toString().equals("FIELDS")) {
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodesField(node);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof FieldArtifactData)) {
            final FieldArtifactData artifactData = (FieldArtifactData) childNode.getArtifact().getData();
            fields[0] += artifactData.toString() + "\n";
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodesField(node);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof ImportArtifactData)) {
            final ImportArtifactData artifactData = (ImportArtifactData) childNode.getArtifact().getData();
            imports[0] += artifactData.toString() + "\n";
        } else if ((childNode.getArtifact().getData() instanceof LineArtifactData)) {
            code[0] += ((LineArtifactData) childNode.getArtifact().getData()).getLine() + "\n";
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node);
                }
            }
        } else if ((childNode.getArtifact().getData() instanceof BlockArtifactData)) {
            final BlockArtifactData artifactData = (BlockArtifactData) childNode.getArtifact().getData();
            code[0] += artifactData.toString() + "\n";
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node);
                }
            }
            code[0] += "\n";
        } else if ((childNode.getArtifact().getData() instanceof MethodArtifactData)) {
            //final MethodArtifactData artifactData = (MethodArtifactData) childNode.getArtifact().getData();
            methods.add(childNode.getParent().getParent().getArtifact().toString() + " " + ((MethodArtifactData) childNode.getArtifact().getData()).getSignature());
            code[0] += "\n" + ((MethodArtifactData) childNode.getArtifact().getData()).getSignature() + "\n";//artifactData.toString() + "{\n";
            if (childNode.getChildren().size() > 0) {
                for (Node node : childNode.getChildren()) {
                    visitingNodes(node);
                }
            }
        }
    }

    private Collection<WriteListener> listeners = new ArrayList<WriteListener>();

    @Override
    public void addListener(WriteListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(WriteListener listener) {
        this.listeners.remove(listener);
    }

}
