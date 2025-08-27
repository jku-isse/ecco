package at.jku.isse.ecco.adapter.rust.antlr;

import at.jku.isse.ecco.adapter.rust.RustReader;
import at.jku.isse.ecco.adapter.rust.data.FunctionArtifactData;
import at.jku.isse.ecco.adapter.rust.data.LineArtifactData;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.artifact.ArtifactData;
import at.jku.isse.ecco.storage.mem.dao.MemEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RustReaderTest {

//    @Test
//    public void testRustReader() {
//        RustReader reader = new RustReader(new MemEntityFactory());
//
//        Path[] input = new Path[]{Paths.get("/home/zaber/Documents/bachelor/ecco/adapter/rust/src/test/resources/rust_examples/greeter.rs")};
//        Set<Node.Op> nodes = reader.read(input);
//        for (Node.Op child : nodes) {
//            List<Node.Op> pluginNodeChildren = (List<Node.Op>) child.getChildren();
//            for (Node.Op node : pluginNodeChildren) {
//                node.getChildren().forEach(System.out::println);
//                Artifact artifact = node.getArtifact();
//                ArtifactData data = artifact.getData();
//                if (data instanceof FunctionArtifactData) {
//                    System.out.println("Function: " + data);
//                } else if (data instanceof LineArtifactData) {
//                    System.out.println("Line: " + data);
//                } else {
//                    System.out.println("Other: " + data);
//                }
//            }
//        }
//
//    }

    @Test
    public void testRustReader() {
        RustReader reader = new RustReader(new MemEntityFactory());
        Path[] input = new Path[]{Paths.get("src/test/resources/rust_examples/greeter.rs")};
        Set<Node.Op> nodes = reader.read(input);

        // Assert one plugin node
        assertEquals(1, nodes.size());
        Node.Op pluginNode = nodes.iterator().next();
        List<Node.Op> children = (List<Node.Op>) pluginNode.getChildren();

        // Check function signatures and their lines
        boolean foundMain = false, foundTest = false;
        for (Node.Op node : children) {
            ArtifactData data = node.getArtifact().getData();
            if (data instanceof FunctionArtifactData) {
                String signature = ((FunctionArtifactData) data).getSignature();
                if (signature.contains("main")) {
                    foundMain = true;
                    List<Node.Op> mainLines = (List<Node.Op>) node.getChildren();
                    System.out.println("Main function lines: " + mainLines);
                    assertEquals(3, mainLines.size());
                    assertEquals("fn main() {", ((LineArtifactData) mainLines.get(0).getArtifact().getData()).getLine());
                    assertEquals("    test(\"Simon\")", ((LineArtifactData) mainLines.get(1).getArtifact().getData()).getLine());
                    assertEquals("}", ((LineArtifactData) mainLines.get(2).getArtifact().getData()).getLine());
                } else if (signature.contains("test")) {
                    foundTest = true;
                    List<Node.Op> testLines = (List<Node.Op>) node.getChildren();
                    assertEquals(3, testLines.size());
                    assertEquals("pub fn test(string: &str) -> String {", ((LineArtifactData) testLines.get(0).getArtifact().getData()).getLine());
                    assertEquals("    println!(\"Hello from Rust! {}\", string);", ((LineArtifactData) testLines.get(1).getArtifact().getData()).getLine());
                    assertEquals("}", ((LineArtifactData) testLines.get(2).getArtifact().getData()).getLine());
                } else {
                    throw new AssertionError("Unexpected function signature: " + signature);
                }
            }
        }
        assertTrue(foundMain, "main function not found");
        assertTrue(foundTest, "test function not found");
    }

}
