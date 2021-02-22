package at.jku.isse.ecco.adapter.runtime;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.adapter.ArtifactReader;
import at.jku.isse.ecco.adapter.dispatch.DispatchWriter;
import at.jku.isse.ecco.adapter.dispatch.PluginArtifactData;
import at.jku.isse.ecco.adapter.runtime.data.*;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.dao.EntityFactory;
import at.jku.isse.ecco.service.listener.ReadListener;
import at.jku.isse.ecco.tree.Node;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.*;
import com.google.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class RuntimeReader implements ArtifactReader<Path, Set<Node.Op>> {

    protected static final Logger LOGGER = Logger.getLogger(DispatchWriter.class.getName());

    private final EntityFactory entityFactory;

    public ArrayList<String> methods = new ArrayList<>();

    @Inject
    public RuntimeReader(EntityFactory entityFactory) {
        checkNotNull(entityFactory);
        this.entityFactory = entityFactory;
    }


    @Override
    public String getPluginId() {
        return RuntimePlugin.class.getName(); //"plugin";
    }

    private static Map<Integer, String[]> prioritizedPatterns;

    static {
        prioritizedPatterns = new HashMap<>();
        prioritizedPatterns.put(Integer.MAX_VALUE, new String[]{"**.runtime", "**.java"});
    }

    @Override
    public Map<Integer, String[]> getPrioritizedPatterns() {
        return Collections.unmodifiableMap(prioritizedPatterns);
    }

    @Override
    public Set<Node.Op> read(Path[] input) {
        return this.read(Paths.get("."), input);
    }

    public String dirsAux;

    int count = 0;

    @Override
    public Set<Node.Op> read(Path base, Path[] input) {
        Set<Node.Op> nodes = new HashSet<>();

        long totalJavaParserTime = 0;

        for (Path path : input) {
            String pathway = path.toString().replace(".java", ".runtime");
            dirsAux = base + "\\";
            String packageDir = pathway.replace("\\", ".");
            String dirs = base.toString().replace("\\src", "\\") + packageDir;
            if (new File(dirs).exists()) {
                Path resolvedPath = base.resolve(path);
                // create plugin artifact/node
                Artifact.Op<PluginArtifactData> pluginArtifact = this.entityFactory.createArtifact(new PluginArtifactData(this.getPluginId(), path));
                Node.Op pluginNode = this.entityFactory.createNode(pluginArtifact);
                nodes.add(pluginNode);
                try {
                    // read btrace file contents
                    String btraceFileContent = new String(Files.readAllBytes(Paths.get(dirs)), StandardCharsets.UTF_8);
                    String[] linesBtraceFile = btraceFileContent.split("\\r?\\n");
                    // read java file contents
                    String fileContent = new String(Files.readAllBytes(resolvedPath), StandardCharsets.UTF_8);
                    String[] lines = fileContent.split("\\r?\\n");

                    long localStartTime = System.currentTimeMillis();
                    CompilationUnit cu = JavaParser.parse(fileContent);
                    totalJavaParserTime += (System.currentTimeMillis() - localStartTime);

                    // package name
                    String packageName = "";
                    if (cu.getPackageDeclaration().isPresent())
                        packageName = cu.getPackageDeclaration().get().getName().toString();

                    for (TypeDeclaration<?> typeDeclaration : cu.getTypes()) {
                        // create class artifact/node
                        String aux = String.valueOf(typeDeclaration.getName().getRange().get().begin.line);
                        String classdeclaration = lines[Integer.valueOf(aux) - 1];
                        String className = typeDeclaration.getName().toString();
                        Artifact.Op<ClassArtifactData> classArtifact = this.entityFactory.createArtifact(new ClassArtifactData(packageName + "." + className, classdeclaration));
                        Node.Op classNode = this.entityFactory.createNode(classArtifact);
                        pluginNode.addChild(classNode);

                        // imports
                        Artifact.Op<AbstractArtifactData> importsGroupArtifact = this.entityFactory.createArtifact(new AbstractArtifactData("IMPORTS"));
                        Node.Op importsGroupNode = this.entityFactory.createNode(importsGroupArtifact);
                        classNode.addChild(importsGroupNode);
                        for (ImportDeclaration importDeclaration : cu.getImports()) {
                            if (!importDeclaration.getName().asString().contains("jacoco")) {//&& !importDeclaration.getName().asString().toLowerCase().contains("deploymentdiagram") && !importDeclaration.getName().asString().toLowerCase().contains("statediagram") && !importDeclaration.getName().asString().toLowerCase().contains("collaborationdiagram") && !importDeclaration.getName().asString().toLowerCase().contains("sequencediagram") && !importDeclaration.getName().asString().toLowerCase().contains("usecasediagram") && !importDeclaration.getName().asString().toLowerCase().contains("java.io.printstream")) {
                                String importName = "import " + importDeclaration.getName().asString();
                                Artifact.Op<ImportArtifactData> importArtifact = this.entityFactory.createArtifact(new ImportArtifactData(importName));
                                Node.Op importNode = this.entityFactory.createNode(importArtifact);
                                importsGroupNode.addChild(importNode);
                            }
                        }

                        this.addClassChildren(typeDeclaration, classNode, lines, linesBtraceFile);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new EccoException("Error parsing java file.", e);
                }

            }

            LOGGER.fine(JavaParser.class + ".parse(): " + totalJavaParserTime + "ms");

        }

        //these lines were added to compute the results at method level
        //try {
        //    Files.write(Paths.get(("C:\\Users\\gabil\\Desktop\\teste\\ActualECCO\\Method_comparison\\results\\methods.txt")), methods.stream().map(Object::toString).collect(Collectors.toList()));
        // } catch (IOException e) {
        //     e.printStackTrace();
        // }
        return nodes;


    }

    //This method contains the code to the ArgoUML Benchmark traces
    private void addClassChildren(TypeDeclaration<?> typeDeclaration, Node.Op classNode, String[] lines, String[] linesBtraceFile) throws IOException {
        if (linesBtraceFile.length > 0) {
            ArrayList<String> justLineNumbers = new ArrayList<String>();
            List<Integer> array = new ArrayList<>();
            int[] linessort = new int[linesBtraceFile.length - 1];
            for (int i = 1; i < linesBtraceFile.length; i++) {

                linessort[i - 1] = Integer.valueOf(linesBtraceFile[i]);
            }
            Arrays.sort(linessort);
            for (int i = 0; i < linessort.length; i++) {
                array.add(Integer.valueOf(linessort[i]));
                justLineNumbers.add(String.valueOf(linessort[i]));
            }

            // create methods artifact/node
            Artifact.Op<AbstractArtifactData> methodsGroupArtifact = this.entityFactory.createArtifact(new AbstractArtifactData("METHODS"));
            Node.Op methodsGroupNode = this.entityFactory.createNode(methodsGroupArtifact);
            classNode.addChild(methodsGroupNode);
            // create fields artifact/node
            Artifact.Op<AbstractArtifactData> fieldsGroupArtifact = this.entityFactory.createArtifact(new AbstractArtifactData("FIELDS"));
            Node.Op fieldsGroupNode = this.entityFactory.createOrderedNode(fieldsGroupArtifact);
            classNode.addChild(fieldsGroupNode);
            // create enums artifact/node
            Artifact.Op<AbstractArtifactData> enumsGroupArtifact = this.entityFactory.createArtifact(new AbstractArtifactData("ENUMS"));
            Node.Op enumsGroupNode = this.entityFactory.createOrderedNode(enumsGroupArtifact);
            classNode.addChild(enumsGroupNode);
            for (BodyDeclaration<?> node : typeDeclaration.getMembers()) {
                // nested classes/interfaces
                if (node instanceof ClassOrInterfaceDeclaration) {
                    String fileName = String.valueOf(((ClassOrInterfaceDeclaration) node).getName()) + ".runtime";
                    String classNodeName = classNode.toString().substring(0, classNode.toString().lastIndexOf("."));
                    String dirsNestedClass = dirsAux.replace("src\\", "") + classNodeName + "." + fileName;
                    if (new File(dirsNestedClass).exists()) {
                        Path nestedClass = Paths.get(dirsNestedClass);
                        String btraceFileContent = new String(Files.readAllBytes(nestedClass), StandardCharsets.UTF_8);
                        String[] linesNestedClass = btraceFileContent.split("\\r?\\n");
                        int qtlNestedClass = 1;
                        ArrayList<String> classAndNestedClass = new ArrayList<String>();
                        for (int i = 0; i < (linesBtraceFile.length + linesNestedClass.length - 1); i++) {
                            if (i < linesBtraceFile.length) {
                                classAndNestedClass.add(String.valueOf(linesBtraceFile[i]));
                            } else {
                                if (!(String.valueOf(linesNestedClass[qtlNestedClass]).equals(String.valueOf(node.getRange().get().begin.line)))) {
                                    classAndNestedClass.add(String.valueOf(linesNestedClass[qtlNestedClass]));
                                }
                                qtlNestedClass++;
                            }
                        }

                        String[] newLines = new String[classAndNestedClass.size()];                //primeiro é preciso

                        for (int i = 0; i < classAndNestedClass.size(); i++) {
                            newLines[i] = String.valueOf(classAndNestedClass.get(i));
                        }

                        Artifact.Op<ClassArtifactData> nestedClassArtifact = this.entityFactory.createArtifact(new ClassArtifactData(classNode.toString() + "." + ((ClassOrInterfaceDeclaration) node).getName().toString()));
                        Node.Op nestedClassNode = this.entityFactory.createNode(nestedClassArtifact);
                        classNode.addChild(nestedClassNode);
                        addClassChildren((ClassOrInterfaceDeclaration) node, nestedClassNode, lines, newLines);
                    } else {
                        String init;
                        String fin;
                        init = String.valueOf(node.getRange().get().begin.line);
                        fin = String.valueOf(node.getRange().get().end.line);

                        if (array.stream().anyMatch(x -> (x >= Integer.valueOf(init) && x <= Integer.valueOf(fin)))) {
                            Artifact.Op<ClassArtifactData> nestedClassArtifact = this.entityFactory.createArtifact(new ClassArtifactData(classNode.toString() + "." + ((ClassOrInterfaceDeclaration) node).getName().toString()));
                            Node.Op nestedClassNode = this.entityFactory.createNode(nestedClassArtifact);
                            classNode.addChild(nestedClassNode);
                            if (justLineNumbers.contains(init)) {
                                justLineNumbers.remove(init);
                                array.remove(Integer.valueOf(node.getRange().get().begin.line));
                            }
                            if (justLineNumbers.contains(fin)) {
                                justLineNumbers.remove(fin);
                                array.remove(Integer.valueOf(node.getRange().get().end.line));
                            }
                            String[] newLines = new String[justLineNumbers.size()];

                            for (int i = 0; i < justLineNumbers.size(); i++) {
                                newLines[i] = String.valueOf(justLineNumbers.get(i));
                            }
                            addClassChildren((ClassOrInterfaceDeclaration) node, nestedClassNode, lines, newLines);

                        }
                    }
                }
                // enumerations
                else if (node instanceof EnumDeclaration) {
                    String init;
                    String fin;
                    init = String.valueOf(node.getRange().get().begin.line);
                    fin = String.valueOf(node.getRange().get().end.line);

                    if (array.stream().anyMatch(x -> (x >= Integer.valueOf(init) && x <= Integer.valueOf(fin)))) {
                        int i = Integer.valueOf(init);
                        while (i <= Integer.valueOf(fin)) {
                            String trimmedLine = lines[i - 1].trim();
                            if (!trimmedLine.isEmpty() && !trimmedLine.equals("}") && !trimmedLine.equals("{")) {
                                if (justLineNumbers.contains(String.valueOf(i))) {
                                    justLineNumbers.remove(String.valueOf(i));
                                    array.remove(Integer.valueOf(i));
                                    Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i - 1]));
                                    Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                                    enumsGroupNode.addChild(lineNode);
                                }
                            }
                            if (i == Integer.valueOf(fin)) {
                                if (justLineNumbers.contains(fin)) {
                                    justLineNumbers.remove(fin);
                                    array.remove(Integer.valueOf(node.getRange().get().end.line));
                                }
                            }
                            i++;
                        }
                    }
                }
                // fields
                else if (node instanceof FieldDeclaration) {
                    int beginLine = node.getRange().get().begin.line;
                    int endLine = node.getRange().get().end.line;

                    if (beginLine == endLine) {
                        if (justLineNumbers.contains(String.valueOf(beginLine))) {
                            justLineNumbers.remove(String.valueOf(beginLine));
                            array.remove(String.valueOf(beginLine));
                            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[beginLine - 1]));
                            Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                            fieldsGroupNode.addChild(lineNode);
                        }
                    } else {
                        if (justLineNumbers.contains(String.valueOf(beginLine))) {

                            String line;
                            int i = beginLine - 1;
                            while (i <= endLine) {
                                String trimmedLine = lines[i - 1].trim();
                                if (!trimmedLine.isEmpty() && !trimmedLine.equals("}") && !trimmedLine.equals("{")) {
                                    if (justLineNumbers.contains(String.valueOf(i))) {
                                        justLineNumbers.remove(String.valueOf(i));
                                        array.remove(Integer.valueOf(i));
                                        Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i - 1]));
                                        Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                                        fieldsGroupNode.addChild(lineNode);
                                    }
                                }
                                i++;
                            }
                            if (justLineNumbers.contains(String.valueOf(endLine))) {
                                justLineNumbers.remove(String.valueOf(endLine));
                            }
                            if (justLineNumbers.contains(String.valueOf(endLine))) {
                                array.remove(endLine);
                            }
                        }
                    }
                }
                // constructors
                else if (node instanceof ConstructorDeclaration) {

                    String init;
                    String fin;
                    if (((ConstructorDeclaration) node).getBody().getRange().isPresent()) {
                        if (String.valueOf(((ConstructorDeclaration) node).getBody().getRange().get().begin.line).equals(String.valueOf(((ConstructorDeclaration) node).getBody().getRange().get().end.line)))
                            init = String.valueOf(((ConstructorDeclaration) node).getBody().getRange().get().begin.line);
                        else
                            init = String.valueOf(((ConstructorDeclaration) node).getBody().getRange().get().begin.line + 1);
                        fin = String.valueOf(((ConstructorDeclaration) node).getBody().getRange().get().end.line);
                    } else {
                        init = String.valueOf(((ConstructorDeclaration) node).getBody().getRange().get().begin.line);
                        fin = String.valueOf(((ConstructorDeclaration) node).getBody().getRange().get().end.line);
                    }

                    if (array.stream().anyMatch(x -> (x >= Integer.valueOf(init) && x <= Integer.valueOf(fin)))) {
                        String methodSignature = ((ConstructorDeclaration) node).getName().toString() + "(" +
                                ((ConstructorDeclaration) node).getParameters().stream().map(parameter -> parameter.getType().toString()).collect(Collectors.joining(",")) +
                                ")";
                        methods.add(classNode.getArtifact() + " " + methodSignature);
                        Artifact.Op<MethodArtifactData> methodArtifact = this.entityFactory.createArtifact(new MethodArtifactData(methodSignature));
                        Node.Op methodNode = this.entityFactory.createOrderedNode(methodArtifact);
                        methodsGroupNode.addChild(methodNode);
                        if (justLineNumbers.contains(fin))
                            justLineNumbers.remove(fin);
                        final String[] justLineNumbersAux = justLineNumbers.toArray(new String[0]);
                        for (int k = 0; k < justLineNumbersAux.length; k++) {
                            if (Integer.valueOf(justLineNumbersAux[k]) >= Integer.valueOf(init) && Integer.valueOf(justLineNumbersAux[k]) < Integer.valueOf(fin)) {
                                String trimmedLine = lines[Integer.valueOf(justLineNumbersAux[k]) - 1].trim();
                                if (!trimmedLine.isEmpty() && !trimmedLine.equals("}") && !trimmedLine.equals("{")) {
                                    Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[Integer.valueOf(justLineNumbersAux[k]) - 1]));
                                    Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                                    methodNode.addChild(lineNode);
                                }
                                String linetodelete = justLineNumbersAux[k];
                                array.remove(Integer.valueOf(linetodelete));
                                justLineNumbers.remove(linetodelete);
                            }
                        }
                        if (array.contains(Integer.valueOf(init)))
                            array.remove(Integer.valueOf(init));
                        if (array.contains(Integer.valueOf(fin)))
                            array.remove(Integer.valueOf(fin));
                        if (justLineNumbers.contains(init))
                            justLineNumbers.remove(init);
                    }
                }

                // methods
                for (MethodDeclaration methodDeclaration : typeDeclaration.getMethods()) {

                    String init;
                    String fin;
                    if (methodDeclaration.getBody().isPresent()) {
                        if (String.valueOf(methodDeclaration.getBody().get().getRange().get().begin.line).equals(String.valueOf(methodDeclaration.getBody().get().getRange().get().end.line)))
                            init = String.valueOf(methodDeclaration.getBody().get().getRange().get().begin.line);
                        else
                            init = String.valueOf(methodDeclaration.getBody().get().getRange().get().begin.line + 1);
                        fin = String.valueOf(methodDeclaration.getBody().get().getRange().get().end.line);
                    } else {
                        init = String.valueOf(methodDeclaration.getRange().get().begin.line);
                        fin = String.valueOf(methodDeclaration.getRange().get().end.line);
                    }

                    if (array.stream().anyMatch(x -> (x >= Integer.valueOf(init) && x <= Integer.valueOf(fin)))) {
                        String methodSignature = methodDeclaration.getName().toString() + "(" +
                                methodDeclaration.getParameters().stream().map(parameter -> parameter.getType().toString()).collect(Collectors.joining(",")) +
                                ")";
                        methods.add(classNode.getArtifact() + " " + methodSignature);
                        Artifact.Op<MethodArtifactData> methodArtifact = this.entityFactory.createArtifact(new MethodArtifactData(methodSignature));
                        Node.Op methodNode = this.entityFactory.createOrderedNode(methodArtifact);
                        methodsGroupNode.addChild(methodNode);
                        if (justLineNumbers.contains(fin))
                            justLineNumbers.remove(fin);
                        final String[] justLineNumbersAux = justLineNumbers.toArray(new String[0]);
                        for (int k = methodDeclaration.getBody().get().getRange().get().begin.line; k < methodDeclaration.getBody().get().getRange().get().end.line; k++) {
                            String trimmedLine = lines[k].trim();
                            String linetodelete = String.valueOf(k + 1);
                            if (!trimmedLine.isEmpty() && !trimmedLine.equals("}") && !trimmedLine.equals("{") && !trimmedLine.startsWith("*") && !trimmedLine.startsWith("/*") && !trimmedLine.startsWith("*/") && !trimmedLine.startsWith("//")) {
                                if (array.contains(Integer.valueOf(linetodelete)) || justLineNumbers.contains(linetodelete)) {
                                    Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[k]));
                                    Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                                    methodNode.addChild(lineNode);
                                }
                            }
                            if (array.contains(Integer.valueOf(linetodelete)))
                                array.remove(Integer.valueOf(linetodelete));
                            if (justLineNumbers.contains(linetodelete))
                                justLineNumbers.remove(linetodelete);
                        }
                        if (array.contains(Integer.valueOf(init)))
                            array.remove(Integer.valueOf(init));
                        if (array.contains(Integer.valueOf(fin)))
                            array.remove(Integer.valueOf(fin));
                        if (justLineNumbers.contains(init))
                            justLineNumbers.remove(init);
                    }

                }

            }
        }
    }


    /* // uncomment this method addClassChildren and comment the one above for mapping the systems for source code artifacts comparison instead of groundtruth traces
    private void addClassChildren(TypeDeclaration<?> typeDeclaration, Node.Op classNode, String[] lines, String[] linesBtraceFile) throws IOException {
        if (linesBtraceFile.length > 0) {
            ArrayList<String> justLineNumbers = new ArrayList<String>();
            List<Integer> array = new ArrayList<>();
            int[] linessort = new int[linesBtraceFile.length - 1];
            for (int i = 1; i < linesBtraceFile.length; i++) {

                linessort[i - 1] = Integer.valueOf(linesBtraceFile[i]);
            }
            Arrays.sort(linessort);
            for (int i = 0; i < linessort.length; i++) {
                array.add(Integer.valueOf(linessort[i]));
                justLineNumbers.add(String.valueOf(linessort[i]));
            }

            // create methods artifact/node
            Artifact.Op<AbstractArtifactData> methodsGroupArtifact = this.entityFactory.createArtifact(new AbstractArtifactData("METHODS"));
            Node.Op methodsGroupNode = this.entityFactory.createNode(methodsGroupArtifact);
            classNode.addChild(methodsGroupNode);
            // create fields artifact/node
            Artifact.Op<AbstractArtifactData> fieldsGroupArtifact = this.entityFactory.createArtifact(new AbstractArtifactData("FIELDS"));
            Node.Op fieldsGroupNode = this.entityFactory.createOrderedNode(fieldsGroupArtifact);
            classNode.addChild(fieldsGroupNode);
            // create enums artifact/node
            Artifact.Op<AbstractArtifactData> enumsGroupArtifact = this.entityFactory.createArtifact(new AbstractArtifactData("ENUMS"));
            Node.Op enumsGroupNode = this.entityFactory.createOrderedNode(enumsGroupArtifact);
            classNode.addChild(enumsGroupNode);
            for (BodyDeclaration<?> node : typeDeclaration.getMembers()) {
                // nested classes/interfaces
                if (node instanceof ClassOrInterfaceDeclaration) {
                    String fileName = String.valueOf(((ClassOrInterfaceDeclaration) node).getName()) + ".runtime";
                    String classNodeName = classNode.toString().substring(0, classNode.toString().lastIndexOf("."));
                    String dirsNestedClass = dirsAux.replace("src\\", "") + classNodeName + "." + fileName;
                    if (new File(dirsNestedClass).exists()) {
                        Path nestedClass = Paths.get(dirsNestedClass);
                        String btraceFileContent = new String(Files.readAllBytes(nestedClass), StandardCharsets.UTF_8);
                        String[] linesNestedClass = btraceFileContent.split("\\r?\\n");
                        int qtlNestedClass = 1;
                        ArrayList<String> classAndNestedClass = new ArrayList<String>();
                        for (int i = 0; i < (linesBtraceFile.length + linesNestedClass.length - 1); i++) {
                            if (i < linesBtraceFile.length) {
                                classAndNestedClass.add(String.valueOf(linesBtraceFile[i]));
                            } else {
                                if (!(String.valueOf(linesNestedClass[qtlNestedClass]).equals(String.valueOf(node.getRange().get().begin.line)))) {
                                    classAndNestedClass.add(String.valueOf(linesNestedClass[qtlNestedClass]));
                                }
                                qtlNestedClass++;
                            }
                        }

                        String[] newLines = new String[classAndNestedClass.size()];                //primeiro é preciso

                        for (int i = 0; i < classAndNestedClass.size(); i++) {
                            newLines[i] = String.valueOf(classAndNestedClass.get(i));
                        }

                        int linetoaddsignature = Integer.valueOf(node.asClassOrInterfaceDeclaration().getName().getRange().get().begin.line);
                        String classdeclaration = "";
                        while (!classdeclaration.contains("{")) {
                            classdeclaration += lines[linetoaddsignature - 1] + "\n";
                            linetoaddsignature++;
                        }
                        Artifact.Op<ClassArtifactData> nestedClassArtifact = this.entityFactory.createArtifact(new ClassArtifactData(classNode.toString() + "." + ((ClassOrInterfaceDeclaration) node).getName().toString(), classdeclaration, "", "", ""));
                        Node.Op nestedClassNode = this.entityFactory.createNode(nestedClassArtifact);
                        classNode.addChild(nestedClassNode);
                        addClassChildren((ClassOrInterfaceDeclaration) node, nestedClassNode, lines, newLines);
                    } else {
                        String init;
                        String fin;
                        init = String.valueOf(node.getRange().get().begin.line);
                        fin = String.valueOf(node.getRange().get().end.line);

                        if (array.stream().anyMatch(x -> (x >= Integer.valueOf(init) && x <= Integer.valueOf(fin)))) {
                            int linetoaddsignature = Integer.valueOf(node.asClassOrInterfaceDeclaration().getName().getRange().get().begin.line);
                            String classdeclaration = "";
                            while (!classdeclaration.contains("{")) {
                                classdeclaration += lines[linetoaddsignature - 1] + "\n";
                                linetoaddsignature++;
                            }
                            Artifact.Op<ClassArtifactData> nestedClassArtifact = this.entityFactory.createArtifact(new ClassArtifactData(classNode.toString() + "." + ((ClassOrInterfaceDeclaration) node).getName().toString(), classdeclaration, "", "", ""));
                            Node.Op nestedClassNode = this.entityFactory.createNode(nestedClassArtifact);
                            classNode.addChild(nestedClassNode);
                            if (justLineNumbers.contains(init)) {
                                justLineNumbers.remove(init);
                                array.remove(Integer.valueOf(node.getRange().get().begin.line));
                            }
                            if (justLineNumbers.contains(fin)) {
                                justLineNumbers.remove(fin);
                                array.remove(Integer.valueOf(node.getRange().get().end.line));
                            }
                            String[] newLines = new String[justLineNumbers.size()];

                            for (int i = 0; i < justLineNumbers.size(); i++) {
                                newLines[i] = String.valueOf(justLineNumbers.get(i));
                            }
                            addClassChildren((ClassOrInterfaceDeclaration) node, nestedClassNode, lines, newLines);

                        }
                    }
                }
                // enumerations
                else if (node instanceof EnumDeclaration) {
                    String init;
                    String fin;
                    init = String.valueOf(node.getRange().get().begin.line);
                    fin = String.valueOf(node.getRange().get().end.line);

                    if (array.stream().anyMatch(x -> (x >= Integer.valueOf(init) && x <= Integer.valueOf(fin)))) {
                        int i = Integer.valueOf(init);
                        while (i <= Integer.valueOf(fin)) {
                            if (justLineNumbers.contains(String.valueOf(i))) {
                                justLineNumbers.remove(String.valueOf(i));
                                array.remove(Integer.valueOf(i));
                            }
                            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i - 1]));
                            Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                            enumsGroupNode.addChild(lineNode);
                            if (i == Integer.valueOf(fin)) {
                                if (justLineNumbers.contains(fin)) {
                                    justLineNumbers.remove(fin);
                                    array.remove(Integer.valueOf(node.getRange().get().end.line));
                                }
                            }
                            i++;
                        }
                    }
                }
                // fields
                else if (node instanceof FieldDeclaration) {
                    int beginLine = node.getRange().get().begin.line;
                    int endLine = node.getRange().get().end.line;

                    if (beginLine == endLine) {
                        if (justLineNumbers.contains(String.valueOf(beginLine))) {
                            justLineNumbers.remove(String.valueOf(beginLine));
                            if (array.contains(Integer.valueOf(beginLine)))
                                array.remove(Integer.valueOf(beginLine));
                            Artifact.Op<LineArtifactData> lineArtifact = null;
                            String field = "";
                            String field2 = "";
                            if (((FieldDeclaration) node).getVariables().get(0).getComment().isPresent()) {
                                field = String.valueOf(((FieldDeclaration) node).getVariables().get(0).getComment()).replace("Optional[", "").replace("\r\n]", "\r\n");
                                field2 = String.valueOf(((FieldDeclaration) node).getVariables().get(0).getComment()).replace("Optional[", "").replace("\r\n]", "").replace("// ", "//");
                            }
                            lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[beginLine - 1].replace(field2, "")));
                            Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                            fieldsGroupNode.addChild(lineNode);
                        } else if (!((FieldDeclaration) node).getVariables().get(0).getInitializer().isPresent()) {
                            String field = String.valueOf(((FieldDeclaration) node).getVariables().get(0).getComment()).replace("Optional[", "").replace("\r\n]", "\r\n");
                            String field2 = String.valueOf(((FieldDeclaration) node).getVariables().get(0).getComment()).replace("Optional[", "").replace("\r\n]", "").replace("// ", "//");
                            Artifact.Op<LineArtifactData> lineArtifact = null;
                            lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[beginLine - 1].replace(field2, "")));
                            Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                            fieldsGroupNode.addChild(lineNode);
                        }
                    } else {
                        if (justLineNumbers.contains(String.valueOf(beginLine))) {
                            justLineNumbers.remove(String.valueOf(beginLine));
                            if (array.contains(beginLine))
                                array.remove(Integer.valueOf(beginLine));
                            Artifact.Op<LineArtifactData> lineArtifact = null;
                            int i = beginLine - 1;
                            String field = "";
                            String field2 = "";
                            Node.Op lineNode;
                            if (((FieldDeclaration) node).getVariables().get(0).getComment().isPresent()) {
                                field = String.valueOf(((FieldDeclaration) node).getVariables().get(0).getComment()).replace("Optional[", "").replace("\r\n]", "\r\n");
                                field2 = String.valueOf(((FieldDeclaration) node).getVariables().get(0).getComment()).replace("Optional[", "").replace("\r\n]", "").replace("// ", "//");
                            }
                            if (lines[i].contains("R4Feature") && lines[i + 1].contains(field2)) {
                                lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i + 1].replace(field2, "")));
                                lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                                if (justLineNumbers.contains(String.valueOf(beginLine + 1))) {
                                    justLineNumbers.remove(String.valueOf(beginLine + 1));
                                    array.remove(Integer.valueOf(beginLine + 1));
                                }
                                fieldsGroupNode.addChild(lineNode);
                                i += 2;
                            } else if (lines[i].contains("R4Feature") && !lines[i + 1].contains(field2)) {
                                lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i + 1]));
                                lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                                if (justLineNumbers.contains(String.valueOf(beginLine + 1))) {
                                    justLineNumbers.remove(String.valueOf(beginLine + 1));
                                    array.remove(Integer.valueOf(beginLine + 1));
                                }
                                fieldsGroupNode.addChild(lineNode);
                                i += 2;
                            } else {
                                while (i + 1 < ((FieldDeclaration) node).getVariables().get(0).getInitializer().get().getRange().get().begin.line) {
                                    lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i]));
                                    lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                                    fieldsGroupNode.addChild(lineNode);
                                    i++;
                                }
                            }

                            if (((FieldDeclaration) node).getVariables().get(0).getInitializer().isPresent() && ((FieldDeclaration) node).getVariables().get(0).getInitializer().get().getRange().get().end.line != beginLine) {
                                int init = ((FieldDeclaration) node).getVariables().get(0).getInitializer().get().getRange().get().begin.line;
                                int fin = ((FieldDeclaration) node).getVariables().get(0).getInitializer().get().getRange().get().end.line;
                                while (init <= fin) {
                                    if (justLineNumbers.contains(String.valueOf(init)))
                                        justLineNumbers.remove(String.valueOf(init));
                                    if (array.contains(Integer.valueOf(init)))
                                        array.remove(Integer.valueOf(init));
                                    if (((FieldDeclaration) node).getVariables().get(0).getComment().isPresent() && init == fin) {
                                        field = String.valueOf(((FieldDeclaration) node).getVariables().get(0).getComment()).replace("Optional[", "").replace("\r\n]", "\r\n");
                                        field2 = String.valueOf(((FieldDeclaration) node).getVariables().get(0).getComment()).replace("Optional[", "").replace("\r\n]", "").replace("// ", "//");
                                        lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[init - 1].replace(field2, "") + " " + field.replace("// ", "//")));
                                    } else
                                        lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[init - 1].replace(field2, "")));
                                    lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                                    fieldsGroupNode.addChild(lineNode);
                                    init++;
                                }
                            }
                            if (justLineNumbers.contains(String.valueOf(endLine))) {
                                justLineNumbers.remove(String.valueOf(endLine));
                            }
                            if (array.contains(Integer.valueOf(endLine))) {
                                array.remove(Integer.valueOf(endLine));
                            }
                        } else if (!((FieldDeclaration) node).getVariables().get(0).getInitializer().isPresent()) {
                            String line;
                            Artifact.Op<LineArtifactData> lineArtifact = null;
                            int i = beginLine - 1;
                            String field = "";
                            String field2 = "";
                            Node.Op lineNode;
                            if (((FieldDeclaration) node).getVariables().get(0).getComment().isPresent()) {
                                field = String.valueOf(((FieldDeclaration) node).getVariables().get(0).getComment()).replace("Optional[", "").replace("\r\n]", "\r\n");
                                field2 = String.valueOf(((FieldDeclaration) node).getVariables().get(0).getComment()).replace("Optional[", "").replace("\r\n]", "").replace("// ", "//");
                            }
                            if (lines[i].contains("R4Feature") && lines[i + 1].contains(field2)) {
                                lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i + 1].replace(field2, "")));
                                lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                                fieldsGroupNode.addChild(lineNode);
                                i += 2;
                            } else if (lines[i].contains("R4Feature") && !lines[i + 1].contains(field2)) {
                                lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i + 1]));
                                lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                                fieldsGroupNode.addChild(lineNode);
                                i += 2;
                            }

                            while (i <= endLine) {
                                if (!lines[i].contains("R4Feature")) {
                                    lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i]));
                                    lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                                    fieldsGroupNode.addChild(lineNode);
                                }
                                i++;
                            }
                        }
                    }
                }
                // constructors
                else if (node instanceof ConstructorDeclaration) {

                    String init;
                    String fin;
                    if (((ConstructorDeclaration) node).getBody().getRange().isPresent()) {
                        if (String.valueOf(((ConstructorDeclaration) node).getBody().getRange().get().begin.line).equals(String.valueOf(((ConstructorDeclaration) node).getBody().getRange().get().end.line)))
                            init = String.valueOf(((ConstructorDeclaration) node).getBody().getRange().get().begin.line);
                        else
                            init = String.valueOf(((ConstructorDeclaration) node).getBody().getRange().get().begin.line + 1);
                        fin = String.valueOf(((ConstructorDeclaration) node).getBody().getRange().get().end.line);
                    } else {
                        init = String.valueOf(((ConstructorDeclaration) node).getBody().getRange().get().begin.line);
                        fin = String.valueOf(((ConstructorDeclaration) node).getBody().getRange().get().end.line);
                    }

                    if (array.stream().anyMatch(x -> (x >= Integer.valueOf(init) && x <= Integer.valueOf(fin)))) {
                        int linetoaddsignature = Integer.valueOf(node.getRange().get().begin.line);
                        String methodSignature = "";
                        while (!methodSignature.contains("{")) {
                            if (!lines[linetoaddsignature - 1].contains("R4Feature"))
                                methodSignature += lines[linetoaddsignature - 1];
                            linetoaddsignature++;
                        }
                        Artifact.Op<MethodArtifactData> methodArtifact = this.entityFactory.createArtifact(new MethodArtifactData(methodSignature));

                        Node.Op methodNode = this.entityFactory.createOrderedNode(methodArtifact);
                        methodsGroupNode.addChild(methodNode);
                        if (justLineNumbers.contains(fin))
                            justLineNumbers.remove(fin);
                        for (int k = Integer.valueOf(init); k <= Integer.valueOf(fin); k++) {
                            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[k - 1]));
                            Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                            methodNode.addChild(lineNode);
                            if (array.contains(Integer.valueOf(k)))
                                array.remove(Integer.valueOf(k));
                            if (justLineNumbers.contains(k))
                                justLineNumbers.remove(k);
                        }
                    }
                }

                // methods
                for (MethodDeclaration methodDeclaration : typeDeclaration.getMethods()) {

                    String init;
                    String fin;
                    if (methodDeclaration.getBody().isPresent()) {
                        if (String.valueOf(methodDeclaration.getBody().get().getRange().get().begin.line).equals(String.valueOf(methodDeclaration.getBody().get().getRange().get().end.line)))
                            init = String.valueOf(methodDeclaration.getBody().get().getRange().get().begin.line);
                        else
                            init = String.valueOf(methodDeclaration.getBody().get().getRange().get().begin.line + 1);
                        fin = String.valueOf(methodDeclaration.getBody().get().getRange().get().end.line);
                    } else {
                        init = String.valueOf(methodDeclaration.getRange().get().begin.line);
                        fin = String.valueOf(methodDeclaration.getRange().get().end.line);
                    }

                    if (array.stream().anyMatch(x -> (x >= Integer.valueOf(init) && x <= Integer.valueOf(fin)))) {
                        int linetoaddsignature = Integer.valueOf(methodDeclaration.getRange().get().begin.line);
                        String methodSignature = "";

                        while (!methodSignature.contains("{")) {
                            if (!lines[linetoaddsignature - 1].contains("R4Feature"))
                                methodSignature += lines[linetoaddsignature - 1];
                            linetoaddsignature++;
                        }

                        Artifact.Op<MethodArtifactData> methodArtifact = this.entityFactory.createArtifact(new MethodArtifactData(methodSignature));
                        Node.Op methodNode = this.entityFactory.createOrderedNode(methodArtifact);
                        methodsGroupNode.addChild(methodNode);
                        if (justLineNumbers.contains(fin))
                            justLineNumbers.remove(fin);
                        for (int k = methodDeclaration.getBody().get().getRange().get().begin.line; k < methodDeclaration.getBody().get().getRange().get().end.line; k++) {
                            String linetodelete = String.valueOf(k + 1);
                            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[k]));
                            Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                            methodNode.addChild(lineNode);
                            if (array.contains(Integer.valueOf(linetodelete)))
                                array.remove(Integer.valueOf(linetodelete));
                            if (justLineNumbers.contains(linetodelete))
                                justLineNumbers.remove(linetodelete);
                        }

                        if (array.contains(Integer.valueOf(init)))
                            array.remove(Integer.valueOf(init));
                        if (array.contains(Integer.valueOf(fin)))
                            array.remove(Integer.valueOf(fin));
                        if (justLineNumbers.contains(init))
                            justLineNumbers.remove(init);
                    }

                }

            }
        }
    }*/


    private Collection<ReadListener> listeners = new ArrayList<>();

    @Override
    public void addListener(ReadListener listener) {

    }

    @Override
    public void removeListener(ReadListener listener) {

    }
}
