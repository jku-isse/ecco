package at.jku.isse.ecco.adapter.java.test;

import at.jku.isse.ecco.adapter.java.JavaReader;
import at.jku.isse.ecco.storage.perst.dao.PerstEntityFactory;
import at.jku.isse.ecco.tree.Node;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class XMLWriter {
    private static Path rootPath = NewJavaReaderTests.ressourceRoot;

    public static void main(String[] args) throws Exception {
        Path to = rootPath.getParent().resolve("xmlOut").resolve("tmp.xml");
        Path from = //rootPath.resolve("jdk").resolve("ArrayList.java");
                rootPath.resolve("InvisibleHandOverZoneImpl.java");

        writeXML(from, to);

    }

    private static void writeXML(Path from, Path to) throws IOException {
        from = rootPath.relativize(from);
        final Set<Node.Op> read = new JavaReader(new PerstEntityFactory()).read(rootPath, new Path[]{from});
        Files.deleteIfExists(to);
        Files.createDirectories(to.getParent());
        Files.createFile(to);
        try (PrintStream writer = new PrintStream(Files.newOutputStream(to))) {
            writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>");
            for (Node.Op cur : read)
                visitNode(cur, writer);
        }
    }

    private static void visitNode(Node.Op node, PrintStream writer) {
        writer.print("<entry>");
        writer.print("<id>");
        writer.print(node.toString());
        writer.print("</id>");
        if (!node.getChildren().isEmpty()) {
            writer.print("<children>");
            for (Node.Op cur : node.getChildren())
                visitNode(cur, writer);
            writer.print("</children>");
        }
        writer.print("</entry>");
    }
}
