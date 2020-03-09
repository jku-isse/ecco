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
import com.github.javaparser.Range;
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
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkNotNull;

/*
	Reader opens CONFIGFILE (.settings.onfig) and reads IP and port to connect to, example: "conn=127.0.0.1:8001"

	The program to be observed needs to be started manually using the parameters
	"-Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=8001,suspend=y -Djava.compiler=NONE". 
	The parameter "address" determines the IP address and the corresponding port.

 */

public class RuntimeReader implements ArtifactReader<Path, Set<Node.Op>> {

    protected static final Logger LOGGER = Logger.getLogger(DispatchWriter.class.getName());

    private final EntityFactory entityFactory;

    @Inject
    public RuntimeReader(EntityFactory entityFactory) {
        checkNotNull(entityFactory);
        this.entityFactory = entityFactory;
    }


    @Override
    public String getPluginId() {
        return "plugin";//RuntimePlugin.class.getName();
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
                        String className = typeDeclaration.getName().toString();
                        Artifact.Op<ClassArtifactData> classArtifact = this.entityFactory.createArtifact(new ClassArtifactData(packageName + "." + className));
                        Node.Op classNode = this.entityFactory.createNode(classArtifact);
                        pluginNode.addChild(classNode);

                        // imports
                        Artifact.Op<AbstractArtifactData> importsGroupArtifact = this.entityFactory.createArtifact(new AbstractArtifactData("IMPORTS"));
                        Node.Op importsGroupNode = this.entityFactory.createNode(importsGroupArtifact);
                        classNode.addChild(importsGroupNode);
                        for (ImportDeclaration importDeclaration : cu.getImports()) {
                            if (!importDeclaration.getName().asString().contains("jacoco") ){//&& !importDeclaration.getName().asString().toLowerCase().contains("deploymentdiagram") && !importDeclaration.getName().asString().toLowerCase().contains("statediagram") && !importDeclaration.getName().asString().toLowerCase().contains("collaborationdiagram") && !importDeclaration.getName().asString().toLowerCase().contains("sequencediagram") && !importDeclaration.getName().asString().toLowerCase().contains("usecasediagram") && !importDeclaration.getName().asString().toLowerCase().contains("java.io.printstream")) {
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

        for (Node nodeaux : nodes) {
            //System.out.println(nodeaux);
            //count++;

            getChild(nodeaux);
        }
        //System.out.println(count);
        return nodes;


    }

    public void getChild(Node node) {
        for (Node nodeaux : node.getChildren()) {
            if (nodeaux.toString().equals("IMPORTS") || nodeaux.toString().equals("METHODS") || nodeaux.toString().equals("ENUMS")) {
                //   System.out.println(nodeaux);
            } else {
                count++;
            }
            getChild(nodeaux);
        }
    }


    //These code is for jacoco files

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

                    /*
                     if (((ConstructorDeclaration) node).getBody().getStatements().isNonEmpty()) {
                        int beginLine = node.getRange().get().begin.line;
                        int endLine = node.getRange().get().end.line;
                        int i = beginLine;
                        while (i < endLine - 1) {
                            String trimmedLine = lines[i].trim();
                            if (!trimmedLine.isEmpty() && !trimmedLine.equals("}") && !trimmedLine.equals("{")) {
                                String actualLine = String.valueOf(i + 1);
                                if (justLineNumbers.contains(actualLine)) {
                                    justLineNumbers.remove(actualLine);
                                    array.remove(Integer.valueOf(actualLine));
                                    Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i]));
                                    Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
                                    methodNode.addChild(lineNode);
                                }
                            }
                            i++;
                        }
                    }
                     */
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
                        //boolean contains = IntStream.of(array).anyMatch(x -> (x > Integer.valueOf(init) && x < Integer.valueOf(fin)) );
                        String methodSignature = methodDeclaration.getName().toString() + "(" +
                                methodDeclaration.getParameters().stream().map(parameter -> parameter.getType().toString()).collect(Collectors.joining(",")) +
                                ")";
                        Artifact.Op<MethodArtifactData> methodArtifact = this.entityFactory.createArtifact(new MethodArtifactData(methodSignature));
                        Node.Op methodNode = this.entityFactory.createOrderedNode(methodArtifact);
                        methodsGroupNode.addChild(methodNode);
                        if (justLineNumbers.contains(fin))
                            justLineNumbers.remove(fin);
                        final String[] justLineNumbersAux = justLineNumbers.toArray(new String[0]);
                        for(int k=methodDeclaration.getRange().get().begin.line; k<methodDeclaration.getRange().get().end.line; k++){
                            String trimmedLine = lines[k].trim();
                            if (!trimmedLine.isEmpty() && !trimmedLine.equals("}") && !trimmedLine.equals("{") && !trimmedLine.startsWith("*") && !trimmedLine.startsWith("/*") && !trimmedLine.startsWith("*/") && !trimmedLine.startsWith("//")) {   Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[k]));
                                Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                                methodNode.addChild(lineNode);
                            }
                            String linetodelete = String.valueOf(k);
                            if (array.contains(Integer.valueOf(linetodelete)))
                                array.remove(Integer.valueOf(linetodelete));
                            if (justLineNumbers.contains(linetodelete))
                                justLineNumbers.remove(linetodelete);
                        }
                        /*for (int k = 0; k < justLineNumbersAux.length; k++) {
                            if (Integer.valueOf(justLineNumbersAux[k]) >= Integer.valueOf(init) && Integer.valueOf(justLineNumbersAux[k]) < Integer.valueOf(fin)) {
                                String trimmedLine = lines[Integer.valueOf(justLineNumbersAux[k]) - 1].trim();
                                if (!trimmedLine.isEmpty() && !trimmedLine.equals("}") && !trimmedLine.equals("{")) {
                                    Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[Integer.valueOf(justLineNumbersAux[k]) - 1]));
                                    Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                                    methodNode.addChild(lineNode);
                                }
                                String linetodelete = justLineNumbersAux[k];
                                if (array.contains(Integer.valueOf(linetodelete)))
                                    array.remove(Integer.valueOf(linetodelete));
                                if (justLineNumbers.contains(linetodelete))
                                    justLineNumbers.remove(linetodelete);
                            }
                        }*/
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

    private void addMethodChildren(MethodDeclaration methodDeclaration, Node.Op methodNode, String[] lines, ArrayList<String> justLineNumbers) {
        // lines inside method
        if (methodDeclaration.getBody().isPresent()) {
            int beginLine = methodDeclaration.getBody().get().getRange().get().begin.line;
            int endLine = methodDeclaration.getBody().get().getRange().get().end.line;
            int i = beginLine;
            while (i < endLine - 1) {
                String trimmedLine = lines[i].trim();
                if (!trimmedLine.isEmpty() && !trimmedLine.equals("}") && !trimmedLine.equals("{")) {
                    String actualLine = String.valueOf(i + 1);
                    if (justLineNumbers.contains(actualLine)) {
                        justLineNumbers.remove(actualLine);
                        Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i]));
                        Node.Op lineNode = this.entityFactory.createOrderedNode(lineArtifact);
                        methodNode.addChild(lineNode);
                    }
                }
                i++;
            }
        }
    }


    /*
    private void addClassChildren(TypeDeclaration<?> typeDeclaration, Node.Op classNode, String[] lines, String[] linesBtraceFile) throws IOException {
        ArrayList<String> justLineNumbers = new ArrayList<String>();
        List<Integer> array = new ArrayList<>();
        for (int i = 1; i < linesBtraceFile.length; i++) {
            justLineNumbers.add(linesBtraceFile[i]);
            array.add(Integer.valueOf(linesBtraceFile[i]));
        }

        // create methods artifact/node
        Artifact.Op<AbstractArtifactData> methodsGroupArtifact = this.entityFactory.createArtifact(new AbstractArtifactData("METHODS"));
        Node.Op methodsGroupNode = this.entityFactory.createOrderedNode(methodsGroupArtifact);
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
                    if (justLineNumbers.contains(String.valueOf(node.getRange().get().begin.line))) {
                        Artifact.Op<ClassArtifactData> nestedClassArtifact = this.entityFactory.createArtifact(new ClassArtifactData(classNode.toString() + "." + ((ClassOrInterfaceDeclaration) node).getName().toString()));
                        Node.Op nestedClassNode = this.entityFactory.createNode(nestedClassArtifact);
                        classNode.addChild(nestedClassNode);
                        justLineNumbers.remove(String.valueOf(node.getRange().get().begin.line));
                        array.remove(Integer.valueOf(node.getRange().get().begin.line));
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
                int beginLine = node.getRange().get().begin.line;
                int endLine = node.getRange().get().end.line;
                if (justLineNumbers.contains(String.valueOf(beginLine))) {
                    int i = beginLine - 1;
                    while (i <= endLine) {
                        String trimmedLine = lines[i].trim();
                        if (!trimmedLine.isEmpty() && !trimmedLine.equals("}") && !trimmedLine.equals("{")) {
                            if (justLineNumbers.contains(String.valueOf(i + 1))) {
                                justLineNumbers.remove(String.valueOf(i + 1));
                                array.remove(Integer.valueOf(i + 1));
                                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i]));
                                Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
                                enumsGroupNode.addChild(lineNode);
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

                if (justLineNumbers.contains(String.valueOf(beginLine))) {

                    String line;
                    int i = beginLine - 1;
                    while (i < endLine) {
                        String trimmedLine = lines[i].trim();
                        if (!trimmedLine.isEmpty() && !trimmedLine.equals("}") && !trimmedLine.equals("{")) {
                            if (justLineNumbers.contains(String.valueOf(i + 1))) {
                                justLineNumbers.remove(String.valueOf(i + 1));
                                array.remove(Integer.valueOf(i + 1));
                                Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i]));
                                Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
                                fieldsGroupNode.addChild(lineNode);
                            }
                        }
                        i++;
                    }
                }
            }
            // constructors
            else if (node instanceof ConstructorDeclaration) {
                String init = String.valueOf(((ConstructorDeclaration) node).getBody().getRange().get().begin.line + 1);
                String fin = String.valueOf(((ConstructorDeclaration) node).getBody().getRange().get().end.line);

                if (justLineNumbers.contains(init)) {
                    //justLineNumbers.remove(String.valueOf(init));
                    if (justLineNumbers.contains(fin)) {
                        justLineNumbers.remove(String.valueOf(fin));
                        array.remove(Integer.valueOf(fin));
                    }

                    String methodSignature = ((ConstructorDeclaration) node).getName().toString() + "(" +
                            ((ConstructorDeclaration) node).getParameters().stream().map(parameter -> parameter.getType().toString()).collect(Collectors.joining(",")) +
                            ")";
                    Artifact.Op<MethodArtifactData> methodArtifact = this.entityFactory.createArtifact(new MethodArtifactData(methodSignature));
                    Node.Op methodNode = this.entityFactory.createOrderedNode(methodArtifact);
                    methodsGroupNode.addChild(methodNode);
                    if (((ConstructorDeclaration) node).getBody().getStatements().isNonEmpty()) {
                        int beginLine = node.getRange().get().begin.line;
                        int endLine = node.getRange().get().end.line;
                        int i = beginLine;
                        while (i < endLine - 1) {
                            String trimmedLine = lines[i].trim();
                            if (!trimmedLine.isEmpty() && !trimmedLine.equals("}") && !trimmedLine.equals("{")) {
                                String actualLine = String.valueOf(i + 1);
                                if (justLineNumbers.contains(actualLine)) {
                                    justLineNumbers.remove(actualLine);
                                    array.remove(Integer.valueOf(actualLine));
                                    Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i]));
                                    Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
                                    methodNode.addChild(lineNode);
                                }
                            }
                            i++;
                        }
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
                if (justLineNumbers.contains(init)) {
                    if (justLineNumbers.contains(fin) && !(fin.equals(init))) {
                        justLineNumbers.remove(String.valueOf(fin));
                        array.remove(Integer.valueOf(fin));
                    }

                    String methodSignature = methodDeclaration.getName().toString() + "(" +
                            methodDeclaration.getParameters().stream().map(parameter -> parameter.getType().toString()).collect(Collectors.joining(",")) +
                            ")";
                    Artifact.Op<MethodArtifactData> methodArtifact = this.entityFactory.createArtifact(new MethodArtifactData(methodSignature));
                    Node.Op methodNode = this.entityFactory.createOrderedNode(methodArtifact);
                    methodsGroupNode.addChild(methodNode);
                    if (methodDeclaration.getBody().isPresent()) {
                        int beginLine = methodDeclaration.getBody().get().getRange().get().begin.line;
                        int endLine = methodDeclaration.getBody().get().getRange().get().end.line;
                        int i = beginLine;
                        if (beginLine == endLine) {
                            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(methodDeclaration.getBody().get().toString()));
                            Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
                            methodNode.addChild(lineNode);
                            if (justLineNumbers.contains(String.valueOf(beginLine))) {
                                justLineNumbers.remove(String.valueOf(beginLine));
                                array.remove(Integer.valueOf(beginLine));
                            }
                        } else if (methodDeclaration.getBody().get().getStatements().size() == 0) {
                            if (justLineNumbers.contains(String.valueOf(endLine))) {
                                justLineNumbers.remove(String.valueOf(endLine));
                                array.remove(Integer.valueOf(node.getRange().get().begin.line));
                            }
                        } else {
                            while (i < endLine - 1) {
                                String trimmedLine = lines[i].trim();
                                if (!trimmedLine.isEmpty() && !trimmedLine.equals("}") && !trimmedLine.equals("{")) {
                                    String actualLine = String.valueOf(i + 1);
                                    if (justLineNumbers.contains(actualLine)) {
                                        justLineNumbers.remove(actualLine);
                                        array.remove(Integer.valueOf(i + 1));
                                        Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i]));
                                        Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
                                        methodNode.addChild(lineNode);
                                    }
                                }
                                i++;
                            }
                            if (justLineNumbers.contains(fin)) {
                                justLineNumbers.remove(String.valueOf(fin));
                                array.remove(Integer.valueOf(fin));
                            }
                        }
                    }
                    final String[] justLineNumbersAux = justLineNumbers.toArray(new String[0]);
                    for (int k = 0; k < justLineNumbersAux.length; k++) {
                        if (Integer.valueOf(justLineNumbersAux[k]) > methodDeclaration.getBody().get().getRange().get().begin.line && Integer.valueOf(justLineNumbersAux[k]) < methodDeclaration.getBody().get().getRange().get().end.line) {
                            String linetodelete = justLineNumbersAux[k];
                            array.remove(Integer.valueOf(linetodelete));
                            justLineNumbers.remove(linetodelete);
                        }
                    }
                } else if (justLineNumbers.contains(fin)) {
                    if (justLineNumbers.contains(fin) && !(fin.equals(init))) {
                        justLineNumbers.remove(String.valueOf(fin));
                        array.remove(Integer.valueOf(fin));
                    }

                    String methodSignature = methodDeclaration.getName().toString() + "(" +
                            methodDeclaration.getParameters().stream().map(parameter -> parameter.getType().toString()).collect(Collectors.joining(",")) +
                            ")";
                    Artifact.Op<MethodArtifactData> methodArtifact = this.entityFactory.createArtifact(new MethodArtifactData(methodSignature));
                    Node.Op methodNode = this.entityFactory.createOrderedNode(methodArtifact);
                    methodsGroupNode.addChild(methodNode);
                    if (methodDeclaration.getBody().isPresent()) {
                        int beginLine = methodDeclaration.getBody().get().getRange().get().begin.line;
                        int endLine = methodDeclaration.getBody().get().getRange().get().end.line;
                        int i = beginLine;
                        if (beginLine == endLine) {
                            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(methodDeclaration.getBody().get().toString()));
                            Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
                            methodNode.addChild(lineNode);
                            if (justLineNumbers.contains(String.valueOf(beginLine))) {
                                justLineNumbers.remove(String.valueOf(beginLine));
                                array.remove(Integer.valueOf(beginLine));
                            }
                        } else if (methodDeclaration.getBody().get().getStatements().size() == 0) {
                            if (justLineNumbers.contains(String.valueOf(endLine))) {
                                justLineNumbers.remove(String.valueOf(endLine));
                                array.remove(Integer.valueOf(endLine));
                            }
                        } else {
                            while (i < endLine - 1) {
                                String trimmedLine = lines[i].trim();
                                if (!trimmedLine.isEmpty() && !trimmedLine.equals("}") && !trimmedLine.equals("{")) {
                                    String actualLine = String.valueOf(i + 1);
                                    if (justLineNumbers.contains(actualLine)) {
                                        justLineNumbers.remove(actualLine);
                                        array.remove(Integer.valueOf(i + 1));
                                        Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i]));
                                        Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
                                        methodNode.addChild(lineNode);
                                    }
                                }
                                i++;
                            }
                            if (justLineNumbers.contains(fin)) {
                                justLineNumbers.remove(String.valueOf(fin));
                                array.remove(Integer.valueOf(fin));
                            }
                        }
                    }
                    final String[] justLineNumbersAux = justLineNumbers.toArray(new String[0]);
                    for (int k = 0; k < justLineNumbersAux.length; k++) {
                        if (Integer.valueOf(justLineNumbersAux[k]) > methodDeclaration.getBody().get().getRange().get().begin.line && Integer.valueOf(justLineNumbersAux[k]) < methodDeclaration.getBody().get().getRange().get().end.line) {
                            String linetodelete = justLineNumbersAux[k];
                            array.remove(Integer.valueOf(linetodelete));
                            justLineNumbers.remove(linetodelete);
                        }
                    }
                } else if (array.stream().anyMatch(x -> (x > Integer.valueOf(init) && x < Integer.valueOf(fin)))) {
                    //boolean contains = IntStream.of(array).anyMatch(x -> (x > Integer.valueOf(init) && x < Integer.valueOf(fin)) );
                    String methodSignature = methodDeclaration.getName().toString() + "(" +
                            methodDeclaration.getParameters().stream().map(parameter -> parameter.getType().toString()).collect(Collectors.joining(",")) +
                            ")";
                    Artifact.Op<MethodArtifactData> methodArtifact = this.entityFactory.createArtifact(new MethodArtifactData(methodSignature));
                    Node.Op methodNode = this.entityFactory.createOrderedNode(methodArtifact);
                    methodsGroupNode.addChild(methodNode);
                    final String[] justLineNumbersAux = justLineNumbers.toArray(new String[0]);
                    for (int k = 0; k < justLineNumbersAux.length; k++) {
                        if (Integer.valueOf(justLineNumbersAux[k]) > methodDeclaration.getBody().get().getRange().get().begin.line && Integer.valueOf(justLineNumbersAux[k]) < methodDeclaration.getBody().get().getRange().get().end.line) {
                            Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[Integer.valueOf(justLineNumbersAux[k]) - 1]));
                            Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
                            methodNode.addChild(lineNode);
                            String linetodelete = justLineNumbersAux[k];
                            array.remove(Integer.valueOf(linetodelete));
                            justLineNumbers.remove(linetodelete);
                        }
                    }

                }

            }
        }

    }

    private void addMethodChildren(MethodDeclaration methodDeclaration, Node.Op methodNode, String[] lines, ArrayList<String> justLineNumbers) {
        // lines inside method
        if (methodDeclaration.getBody().isPresent()) {
            int beginLine = methodDeclaration.getBody().get().getRange().get().begin.line;
            int endLine = methodDeclaration.getBody().get().getRange().get().end.line;
            int i = beginLine;
            while (i < endLine - 1) {
                String trimmedLine = lines[i].trim();
                if (!trimmedLine.isEmpty() && !trimmedLine.equals("}") && !trimmedLine.equals("{")) {
                    String actualLine = String.valueOf(i + 1);
                    if (justLineNumbers.contains(actualLine)) {
                        justLineNumbers.remove(actualLine);
                        Artifact.Op<LineArtifactData> lineArtifact = this.entityFactory.createArtifact(new LineArtifactData(lines[i]));
                        Node.Op lineNode = this.entityFactory.createNode(lineArtifact);
                        methodNode.addChild(lineNode);
                    }
                }
                i++;
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
