package at.jku.isse.ecco.test;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.storage.mem.artifact.MemArtifact;
import at.jku.isse.ecco.storage.mem.pog.MemPartialOrderGraph;
import at.jku.isse.ecco.storage.mem.pog.MemPartialOrderGraphNode;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.graphstream.ui.layout.Layout;
import org.graphstream.ui.layout.springbox.implementations.SpringBox;
import org.graphstream.ui.swingViewer.ViewPanel;
import org.graphstream.ui.view.Viewer;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class PartialOrderGraphTest {

	private static final Path DATA_DIR;

	static {
		Path dataPath = null;
		try {
			dataPath = Paths.get(PartialOrderGraphTest.class.getClassLoader().getResource("data").toURI());
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		DATA_DIR = dataPath;
	}


	@Test(groups = {"unit", "base", "pog"})
	public void SequenceTest5() throws IOException {
		List<String> lines1 = Files.readAllLines(DATA_DIR.resolve("s5\\1.txt"));
		List<String> lines2 = Files.readAllLines(DATA_DIR.resolve("s5\\2.txt"));
		List<String> lines3 = Files.readAllLines(DATA_DIR.resolve("s5\\3.txt"));
		List<String> lines4 = Files.readAllLines(DATA_DIR.resolve("s5\\4.txt"));
		List<String> lines5 = Files.readAllLines(DATA_DIR.resolve("s5\\5.txt"));
		List<String> lines6 = Files.readAllLines(DATA_DIR.resolve("s5\\6.txt"));

		List<Artifact.Op<?>> artifacts1 = lines1.stream().filter(s -> !s.trim().isEmpty()).map(line -> A(line)).collect(Collectors.toList());
		List<Artifact.Op<?>> artifacts2 = lines2.stream().filter(s -> !s.trim().isEmpty()).map(line -> A(line)).collect(Collectors.toList());
		List<Artifact.Op<?>> artifacts3 = lines3.stream().filter(s -> !s.trim().isEmpty()).map(line -> A(line)).collect(Collectors.toList());
		List<Artifact.Op<?>> artifacts4 = lines4.stream().filter(s -> !s.trim().isEmpty()).map(line -> A(line)).collect(Collectors.toList());
		List<Artifact.Op<?>> artifacts5 = lines5.stream().filter(s -> !s.trim().isEmpty()).map(line -> A(line)).collect(Collectors.toList());
		List<Artifact.Op<?>> artifacts6 = lines6.stream().filter(s -> !s.trim().isEmpty()).map(line -> A(line)).collect(Collectors.toList());

		PartialOrderGraph.Op pog1 = new MemPartialOrderGraph();

		pog1.merge(artifacts1);
		pog1.merge(artifacts2);
		pog1.merge(artifacts3);
		pog1.merge(artifacts4);
//		pog1.merge(artifacts5);
//		pog1.merge(artifacts6);

		displayPOG(pog1);
	}


	@Test(groups = {"unit", "base", "pog"})
	public void MergeTest6() {
		List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("1"), A("2"), A("3"), A("5"), A("6"));
		List<Artifact.Op<?>> artifacts2 = Arrays.asList(A("1"), A("4"), A("3"), A("5"), A("6"));
		List<Artifact.Op<?>> artifacts3 = Arrays.asList(A("1"), A("3"), A("5"), A("7"), A("6"));

		PartialOrderGraph.Op pog1 = new MemPartialOrderGraph();

		pog1.merge(artifacts1);
		pog1.merge(artifacts2);

		pog1.align(artifacts3);
		for (Artifact.Op<?> artifact : artifacts3) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();

		pog1.merge(artifacts3);

		displayPOG(pog1);

		pog1.merge(artifacts3);
	}


	@Test(groups = {"unit", "base", "pog"})
	public void SequenceTest4() throws IOException {
		List<String> lines1 = Files.readAllLines(DATA_DIR.resolve("s4simple\\1.txt"));
		List<String> lines2 = Files.readAllLines(DATA_DIR.resolve("s4simple\\2.txt"));
		List<String> lines3 = Files.readAllLines(DATA_DIR.resolve("s4simple\\3.txt"));

		List<Artifact.Op<?>> artifacts1 = lines1.stream().filter(s -> !s.trim().isEmpty()).map(line -> A(line)).collect(Collectors.toList());
		List<Artifact.Op<?>> artifacts2 = lines2.stream().filter(s -> !s.trim().isEmpty()).map(line -> A(line)).collect(Collectors.toList());
		List<Artifact.Op<?>> artifacts3 = lines3.stream().filter(s -> !s.trim().isEmpty()).map(line -> A(line)).collect(Collectors.toList());

		PartialOrderGraph.Op pog1 = new MemPartialOrderGraph();

		pog1.merge(artifacts1);
		pog1.merge(artifacts2);

		pog1.align(artifacts3);
		for (Artifact.Op<?> artifact : artifacts3) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();

		System.out.println(pog1.collectNodes().size());

		pog1.merge(artifacts3);

		displayPOG(pog1);

		pog1.merge(artifacts3);
	}


	@Test(groups = {"unit", "base", "pog"})
	public void MergeTest5() {
		List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("3"), A("4"));
		List<Artifact.Op<?>> artifacts2 = Arrays.asList(A("3"), A("L"), A("C"), A("D"), A("L2"), A("4"));
		List<Artifact.Op<?>> artifacts3 = Arrays.asList(A("3"), A("L"), A("E"), A("F"), A("L2"), A("4"));
		List<Artifact.Op<?>> artifacts4 = Arrays.asList(A("3"), A("C"), A("D"), A("L"), A("E"), A("F"), A("L2"), A("4"));

		PartialOrderGraph.Op pog1 = new MemPartialOrderGraph();

		pog1.merge(artifacts1);
		pog1.merge(artifacts2);
		pog1.merge(artifacts3);

		pog1.align(artifacts4);
		for (Artifact.Op<?> artifact : artifacts4) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();

		displayPOG(pog1);

		pog1.merge(artifacts4);
	}

	@Test(groups = {"unit", "base", "pog"})
	public void MergeTest4() {
		List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("3"), A("L"), A("L"), A("S"), A("4"));
		List<Artifact.Op<?>> artifacts2 = Arrays.asList(A("3"), A("L"), A("A"), A("B"), A("L"), A("L"), A("C"), A("D"), A("L"), A("S"), A("4"));
		List<Artifact.Op<?>> artifacts3 = Arrays.asList(A("3"), A("L"), A("A"), A("B"), A("L"), A("L"), A("S"), A("E"), A("F"), A("L"), A("4"));
		List<Artifact.Op<?>> artifacts4 = Arrays.asList(A("3"), A("L"), A("L"), A("C"), A("D"), A("L"), A("S"), A("E"), A("F"), A("L"), A("4"));

		PartialOrderGraph.Op pog1 = new MemPartialOrderGraph();

		pog1.merge(artifacts1);
		pog1.merge(artifacts2);
		pog1.merge(artifacts3);

		pog1.align(artifacts4);
		for (Artifact.Op<?> artifact : artifacts4) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();

		displayPOG(pog1);

		pog1.merge(artifacts4);
	}


	@Test(groups = {"unit", "base", "pog"})
	public void SequenceTest3() throws IOException {
		List<String> lines1 = Files.readAllLines(DATA_DIR.resolve("s3\\1.txt"));
		List<String> lines2 = Files.readAllLines(DATA_DIR.resolve("s3\\2.txt"));
		List<String> lines3 = Files.readAllLines(DATA_DIR.resolve("s3\\3.txt"));
		List<String> lines4 = Files.readAllLines(DATA_DIR.resolve("s3\\4.txt"));

		List<Artifact.Op<?>> artifacts1 = lines1.stream().map(line -> A(line)).collect(Collectors.toList());
		List<Artifact.Op<?>> artifacts2 = lines2.stream().map(line -> A(line)).collect(Collectors.toList());
		List<Artifact.Op<?>> artifacts3 = lines3.stream().map(line -> A(line)).collect(Collectors.toList());
		List<Artifact.Op<?>> artifacts4 = lines4.stream().map(line -> A(line)).collect(Collectors.toList());

		PartialOrderGraph.Op pog1 = new MemPartialOrderGraph();

		pog1.merge(artifacts1);
		pog1.merge(artifacts2);
		pog1.merge(artifacts3);

		pog1.align(artifacts4);
		for (Artifact.Op<?> artifact : artifacts4) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();

		System.out.println(pog1.collectNodes().size());

		//pog1.merge(artifacts4);

		displayPOG(pog1);
	}


	@Test(groups = {"unit", "base", "pog"})
	public void ComplexMergeTest() {
		List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("1"), A("8"), A("2"), A("7"));
		List<Artifact.Op<?>> artifacts2 = Arrays.asList(A("1"), A("10"), A("3"));
		List<Artifact.Op<?>> artifacts3 = Arrays.asList(A("1"), A("9"), A("2"), A("4"), A("5"));
		List<Artifact.Op<?>> artifacts4 = Arrays.asList(A("1"), A("6"), A("4"), A("3"));

		PartialOrderGraph.Op pog1 = new MemPartialOrderGraph();
		PartialOrderGraph.Op pog2 = new MemPartialOrderGraph();

		pog1.merge(artifacts1);
		pog1.merge(artifacts3);

		pog2.merge(artifacts2);
		pog2.merge(artifacts4);

		pog1.align(pog2);

		for (PartialOrderGraph.Node.Op node : pog1.collectNodes()) {
			System.out.println(node.getArtifact() + " [" + (node.getArtifact() != null ? node.getArtifact().getSequenceNumber() : "-") + "]");
		}
		System.out.println();
		for (PartialOrderGraph.Node.Op node : pog2.collectNodes()) {
			System.out.println(node.getArtifact() + " [" + (node.getArtifact() != null ? node.getArtifact().getSequenceNumber() : "-") + "]");
		}
		System.out.println();

		for (Artifact.Op<?> artifact : artifacts1) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();
		for (Artifact.Op<?> artifact : artifacts2) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();
		for (Artifact.Op<?> artifact : artifacts3) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();
		for (Artifact.Op<?> artifact : artifacts4) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();

		//displayPOG(pog2);

		pog1.merge(pog2);

		displayPOG(pog1);
	}


	@Test(groups = {"unit", "base", "pog"})
	public void OnlyOneTest() {
		List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("1"));
		List<Artifact.Op<?>> artifacts2 = Arrays.asList(A("1"));

		PartialOrderGraph.Op pog1 = new MemPartialOrderGraph();

		pog1.merge(artifacts1);
		for (Artifact.Op<?> artifact : artifacts1) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();

		pog1.align(artifacts2);
		for (Artifact.Op<?> artifact : artifacts2) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();
		pog1.merge(artifacts2);

		displayPOG(pog1);
	}


	@Test(groups = {"unit", "base", "pog"})
	public void EmptyTest() {
		List<Artifact.Op<?>> artifacts1 = Collections.emptyList();
		List<Artifact.Op<?>> artifacts2 = Collections.emptyList();

		PartialOrderGraph.Op pog1 = new MemPartialOrderGraph();

		pog1.merge(artifacts1);
		for (Artifact.Op<?> artifact : artifacts1) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();

		pog1.align(artifacts2);
		for (Artifact.Op<?> artifact : artifacts2) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();
		pog1.merge(artifacts2);

		displayPOG(pog1);
	}


	@Test(groups = {"unit", "base", "pog"})
	public void CycleTest() {
		List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("1"), A("2"), A("3"), A("4"), A("5"), A("6"), A("7"), A("8"));
		List<Artifact.Op<?>> artifacts2 = Arrays.asList(A("1"), A("2"), A("3"), A("X"), A("4"), A("5"), A("6"), A("Y"), A("7"), A("8"));

		PartialOrderGraph.Op pog1 = new MemPartialOrderGraph();


		pog1.merge(artifacts1);
		for (Artifact.Op<?> artifact : artifacts1) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();

		pog1.align(artifacts2);
		for (Artifact.Op<?> artifact : artifacts2) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();
		pog1.merge(artifacts2);

		displayPOG(pog1);
	}


	@Test(groups = {"unit", "base", "pog"})
	public void YetAnotherTest3() throws IOException {
		List<String> lines1 = Files.readAllLines(DATA_DIR.resolve("s2\\1.txt"));
		List<String> lines2 = Files.readAllLines(DATA_DIR.resolve("s2\\2.txt"));
		List<String> lines3 = Files.readAllLines(DATA_DIR.resolve("s2\\3.txt"));

		List<Artifact.Op<?>> artifacts1 = lines1.stream().map(line -> A(line.trim())).collect(Collectors.toList());
		List<Artifact.Op<?>> artifacts2 = lines2.stream().map(line -> A(line.trim())).collect(Collectors.toList());
		List<Artifact.Op<?>> artifacts3 = lines3.stream().map(line -> A(line.trim())).collect(Collectors.toList());

		PartialOrderGraph.Op pog1 = new MemPartialOrderGraph();

		pog1.merge(artifacts1);
		pog1.merge(artifacts2);

//		pog1.align(artifacts3);
//		for (Artifact.Op<?> artifact : artifacts3) {
//			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
//		}
//		System.out.println();

		pog1.merge(artifacts3);

		displayPOG(pog1);
	}


	@Test(groups = {"unit", "base", "pog"})
	public void YetAnotherTest2() throws IOException {
		List<String> lines1 = Files.readAllLines(DATA_DIR.resolve("yetanothersequence\\1.txt"));
		List<String> lines2 = Files.readAllLines(DATA_DIR.resolve("yetanothersequence\\2.txt"));
		List<String> lines3 = Files.readAllLines(DATA_DIR.resolve("yetanothersequence\\3.txt"));

		List<Artifact.Op<?>> artifacts1 = lines1.stream().map(line -> A(line.trim())).collect(Collectors.toList());
		List<Artifact.Op<?>> artifacts2 = lines2.stream().map(line -> A(line.trim())).collect(Collectors.toList());
		List<Artifact.Op<?>> artifacts3 = lines3.stream().map(line -> A(line.trim())).collect(Collectors.toList());
		List<Artifact.Op<?>> artifacts4 = lines3.stream().map(line -> A(line.trim())).collect(Collectors.toList());

		PartialOrderGraph.Op pog1 = new MemPartialOrderGraph();

		pog1.merge(artifacts1);

		pog1.align(artifacts2);
		for (Artifact.Op<?> artifact : artifacts2) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();
		pog1.merge(artifacts2);

		pog1.align(artifacts3);
		for (Artifact.Op<?> artifact : artifacts3) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();
		pog1.merge(artifacts3);

		pog1.align(artifacts4);
		for (Artifact.Op<?> artifact : artifacts4) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();
		pog1.merge(artifacts4);

		displayPOG(pog1);
	}


	@Test(groups = {"unit", "base", "pog"})
	public void YetAnotherTest() throws IOException {
		List<String> lines1 = Files.readAllLines(DATA_DIR.resolve("othersequence\\1.java"));
		List<String> lines2 = Files.readAllLines(DATA_DIR.resolve("othersequence\\2.java"));

		List<Artifact.Op<?>> artifacts1 = lines1.stream().map(line -> A(line.trim())).collect(Collectors.toList());
		List<Artifact.Op<?>> artifacts2 = lines2.stream().map(line -> A(line.trim())).collect(Collectors.toList());

		PartialOrderGraph.Op pog1 = new MemPartialOrderGraph();

		pog1.merge(artifacts1);
		pog1.merge(artifacts2);

		displayPOG(pog1);
	}


	@Test(groups = {"unit", "base", "pog"})
	public void AnotherTest() {
		List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("1"), A("2"), A("3"), A("4"), A("5"), A("6"));
		List<Artifact.Op<?>> artifacts2 = Arrays.asList(A("1"), A("2"), A("3"), A("4"), A("5"), A("7"), A("8"), A("6"));
		List<Artifact.Op<?>> artifacts3 = Arrays.asList(A("1"), A("2"), A("3"), A("4"), A("5"), A("9"), A("10"), A("6"));
		List<Artifact.Op<?>> artifacts4 = Arrays.asList(A("1"), A("2"), A("3"), A("4"), A("5"), A("11"), A("12"), A("6"));

		PartialOrderGraph.Op pog1 = new MemPartialOrderGraph();

		pog1.merge(artifacts1);
		pog1.merge(artifacts3);
		pog1.merge(artifacts2);
		pog1.merge(artifacts4);

		PartialOrderGraph.Op pog2 = new MemPartialOrderGraph();
		pog2.copy(pog1);

		displayPOG(pog2);
	}


	@Test(groups = {"unit", "base", "pog"})
	public void CloneTest() {
		List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("1"), A("8"), A("2"), A("7"));
		List<Artifact.Op<?>> artifacts2 = Arrays.asList(A("1"), A("10"), A("3"));
		List<Artifact.Op<?>> artifacts3 = Arrays.asList(A("1"), A("9"), A("2"), A("4"), A("5"));
		List<Artifact.Op<?>> artifacts4 = Arrays.asList(A("1"), A("6"), A("4"), A("3"));

		PartialOrderGraph.Op pog1 = new MemPartialOrderGraph();

		pog1.merge(artifacts1);
		pog1.merge(artifacts3);
		pog1.merge(artifacts2);
		pog1.merge(artifacts4);

		PartialOrderGraph.Op pog2 = new MemPartialOrderGraph();
		pog2.copy(pog1);

		displayPOG(pog2);
	}


	@Test(groups = {"unit", "base", "pog", "ppu"})
	public void PPU_Test() {
		List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("A"), A("B"), A("C"), A("D"), A("E"), A("F"), A("G"), A("H"), A("I"), A("J"), A("K"), A("L"), A("M"));
		List<Artifact.Op<?>> artifacts2 = Arrays.asList(A("A"), A("H"), A("I"), A("J"), A("K"), A("L"), A("N"), A("O"), A("F"), A("B"), A("C"), A("M"));

		PartialOrderGraph.Op pog = new MemPartialOrderGraph();


		pog.merge(artifacts1);
		System.out.println("--------------");

//		pog.align(artifacts2);
//		for (Artifact.Op<?> artifact : artifacts2) {
//			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
//		}

		pog.merge(artifacts2);


		displayPOG(pog);
	}


	@Test(groups = {"unit", "base", "pog"})
	public void SequenceGraphs_Full() {
		List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("1"), A("8"), A("2"), A("7"));
		List<Artifact.Op<?>> artifacts2 = Arrays.asList(A("1"), A("10"), A("3"));
		List<Artifact.Op<?>> artifacts3 = Arrays.asList(A("1"), A("9"), A("2"), A("4"), A("5"));
		List<Artifact.Op<?>> artifacts4 = Arrays.asList(A("1"), A("6"), A("4"), A("3"));

		PartialOrderGraph.Op pog1 = new MemPartialOrderGraph();
		PartialOrderGraph.Op pog2 = new MemPartialOrderGraph();


		// align sequence to sg
		pog1.merge(artifacts1);
		pog1.merge(artifacts3);
//		pog1.merge(artifacts2);
//		pog1.merge(artifacts4);

		displayPOG(pog1);


//		pog2.merge(artifacts2);
//		pog2.merge(artifacts4);
//
//
////		// align sg to sg
////		sg1.merge(sg2);
//
//		displayPOG(pog1);
//		//displayPOG(pog2);
	}


	@Test(groups = {"unit", "base", "pog"})
	public void MergeTest3() {
		List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("1"), A("8"), A("2"), A("7"));
		List<Artifact.Op<?>> artifacts2 = Arrays.asList(A("1"), A("10"), A("3"));
		List<Artifact.Op<?>> artifacts3 = Arrays.asList(A("1"), A("9"), A("2"), A("4"), A("5"));
		List<Artifact.Op<?>> artifacts4 = Arrays.asList(A("1"), A("6"), A("4"), A("3"));

		PartialOrderGraph.Op pog1 = new MemPartialOrderGraph();


		pog1.merge(artifacts1);
		pog1.merge(artifacts3);
		pog1.merge(artifacts2);

		pog1.align(artifacts4);
		for (Artifact.Op<?> artifact : artifacts4) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();
		pog1.merge(artifacts4);

		displayPOG(pog1);

		// print all orders in pog
		Collection<List<PartialOrderGraph.Node.Op>> orders = pog1.computeAllOrders();
		for (List<PartialOrderGraph.Node.Op> order : orders) {
			System.out.println(order.stream().map(node -> node.getArtifact() != null ? node.getArtifact().toString() : "NULL").collect(Collectors.joining(" - ")));
		}
	}


	@Test(groups = {"unit", "base", "pog"})
	public void MergeTest2() {
		List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("1"), A("8"), A("2"), A("7"));
		List<Artifact.Op<?>> artifacts2 = Arrays.asList(A("1"), A("10"), A("3"));
		List<Artifact.Op<?>> artifacts3 = Arrays.asList(A("1"), A("9"), A("2"), A("4"), A("5"));
		List<Artifact.Op<?>> artifacts4 = Arrays.asList(A("1"), A("6"), A("4"), A("3"));

		PartialOrderGraph.Op pog1 = new MemPartialOrderGraph();


		pog1.merge(artifacts1);
		for (Artifact.Op<?> artifact : artifacts1) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();

		pog1.align(artifacts3);
		for (Artifact.Op<?> artifact : artifacts3) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();
		pog1.merge(artifacts3);
		for (Artifact.Op<?> artifact : artifacts3) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
		System.out.println();

		displayPOG(pog1);
	}


	@Test(groups = {"unit", "base", "pog"})
	public void MergeTest1() {
		List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("1"), A("8"), A("2"), A("7"));

		PartialOrderGraph.Op pog1 = new MemPartialOrderGraph();

		pog1.merge(artifacts1);

		displayPOG(pog1);
	}


	@Test(groups = {"unit", "base", "pog"})
	public void AlignTest() {
		List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("1"), A("12"), A("22"), A("3"));

		// create pog
		MemPartialOrderGraph pog1 = new MemPartialOrderGraph();

		MemPartialOrderGraphNode pogn1 = new MemPartialOrderGraphNode(A("1", 1));
		pog1.getHead().addChild(pogn1);
		MemPartialOrderGraphNode pogn2 = new MemPartialOrderGraphNode(A("2", 2));
		pog1.getHead().addChild(pogn2);

		MemPartialOrderGraphNode pogn11 = new MemPartialOrderGraphNode(A("11", 3));
		pogn1.addChild(pogn11);
		MemPartialOrderGraphNode pogn12 = new MemPartialOrderGraphNode(A("12", 4));
		pogn11.addChild(pogn12);
		MemPartialOrderGraphNode pogn13 = new MemPartialOrderGraphNode(A("13", 5));
		pogn12.addChild(pogn13);

		MemPartialOrderGraphNode pogn21 = new MemPartialOrderGraphNode(A("21", 6));
		pogn2.addChild(pogn21);
		MemPartialOrderGraphNode pogn22 = new MemPartialOrderGraphNode(A("22", 7));
		pogn21.addChild(pogn22);
		MemPartialOrderGraphNode pogn23 = new MemPartialOrderGraphNode(A("23", 8));
		pogn22.addChild(pogn23);

		MemPartialOrderGraphNode pogn3 = new MemPartialOrderGraphNode(A("3", 9));
		pogn13.addChild(pogn3);
		pogn23.addChild(pogn3);

		pogn3.addChild(pog1.getTail());


		// align sequence to sg
		pog1.align(artifacts1);


		for (Artifact.Op<?> artifact : artifacts1) {
			System.out.println(artifact + " [" + artifact.getSequenceNumber() + "]");
		}
	}


	@Test(groups = {"unit", "base", "pog"})
	public void ShowGraphAndPrintAllOrdersTest() {
		// create pog
		MemPartialOrderGraph pog1 = new MemPartialOrderGraph();

		MemPartialOrderGraphNode pogn1 = new MemPartialOrderGraphNode(A("1"));
		pog1.getHead().addChild(pogn1);
		MemPartialOrderGraphNode pogn2 = new MemPartialOrderGraphNode(A("2"));
		pog1.getHead().addChild(pogn2);

		MemPartialOrderGraphNode pogn11 = new MemPartialOrderGraphNode(A("11"));
		pogn1.addChild(pogn11);
		MemPartialOrderGraphNode pogn12 = new MemPartialOrderGraphNode(A("12"));
		pogn11.addChild(pogn12);
		MemPartialOrderGraphNode pogn13 = new MemPartialOrderGraphNode(A("13"));
		pogn12.addChild(pogn13);

		MemPartialOrderGraphNode pogn21 = new MemPartialOrderGraphNode(A("21"));
		pogn2.addChild(pogn21);
		MemPartialOrderGraphNode pogn22 = new MemPartialOrderGraphNode(A("22"));
		pogn21.addChild(pogn22);
		MemPartialOrderGraphNode pogn23 = new MemPartialOrderGraphNode(A("23"));
		pogn22.addChild(pogn23);

		MemPartialOrderGraphNode pogn3 = new MemPartialOrderGraphNode(A("3"));
		pogn13.addChild(pogn3);
		pogn23.addChild(pogn3);

		pogn3.addChild(pog1.getTail());

		// show pog
		displayPOG(pog1);

		// print all orders in pog
		Collection<List<PartialOrderGraph.Node.Op>> orders = pog1.computeAllOrders();
		for (List<PartialOrderGraph.Node.Op> order : orders) {
			System.out.println(order.stream().map(node -> node.getArtifact() != null ? node.getArtifact().toString() : "NULL").collect(Collectors.joining(" - ")));
		}
	}


	private void displayPOG(PartialOrderGraph pog) {
//		Utility.launchApp((app, stage) -> {
//			PartialOrderGraphView partialOrderGraphView = new PartialOrderGraphView();
//			partialOrderGraphView.showGraph(pog);
//
//			stage.setWidth(300);
//			stage.setHeight(300);
//
//			Scene scene = new Scene(partialOrderGraphView);
//			stage.setScene(scene);
//
//			stage.show();
//		});


		System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");

		Graph graph = new SingleGraph("PartialOrderGraph");

		graph.setStrict(false);

		graph.addAttribute("ui.quality");
		graph.addAttribute("ui.antialias");
		graph.addAttribute("ui.stylesheet", " node.start { fill-color: green; size: 20px; } node.end { fill-color: red; size: 20px; } node {text-alignment:above;text-background-mode:plain;}");

		this.traversePartialOrderGraph(graph, pog.getHead(), null, new HashMap<>());

		Layout layout = new SpringBox(false);
		graph.addSink(layout);
		layout.addAttributeSink(graph);

		//Viewer viewer = graph.display();
		Viewer viewer = new Viewer(graph, Viewer.ThreadingModel.GRAPH_IN_ANOTHER_THREAD);
		//viewer.setCloseFramePolicy(Viewer.CloseFramePolicy.CLOSE_VIEWER);
		viewer.enableAutoLayout();

		ViewPanel view = viewer.addDefaultView(false); // false indicates "no JFrame"

		Object lock = new Object();
		JFrame frame = new JFrame();
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent arg0) {
				synchronized (lock) {
					frame.setVisible(false);
					lock.notify();
				}
			}
		});
		frame.add(view);
		frame.setSize(300, 300);
		frame.setVisible(true);

		synchronized (lock) {
			while (frame.isVisible())
				try {
					lock.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
		}

//		try {
//			Thread.sleep(Long.MAX_VALUE);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
	}

	private void traversePartialOrderGraph(Graph graph, PartialOrderGraph.Node pogNode, org.graphstream.graph.Node gsParent, Map<PartialOrderGraph.Node, Node> nodeMap) {
		org.graphstream.graph.Node gsNode = nodeMap.get(pogNode);
		if (gsNode == null) {
			gsNode = graph.addNode("N" + nodeMap.size() + 1);
			gsNode.setAttribute("label", pogNode.getArtifact() + " [" + (pogNode.getArtifact() != null ? pogNode.getArtifact().getSequenceNumber() : "-") + "]");

			if (pogNode.getPrevious().isEmpty()) {
				gsNode.setAttribute("ui.class", "start");
			}

			if (pogNode.getNext().isEmpty()) {
				gsNode.setAttribute("ui.class", "end");
			}

			nodeMap.put(pogNode, gsNode);
		}
		if (gsParent != null) {
			graph.addEdge("E" + gsParent.getId() + "-" + gsNode.getId(), gsParent, gsNode, true);
		}
		for (PartialOrderGraph.Node pogChild : pogNode.getNext()) {
			this.traversePartialOrderGraph(graph, pogChild, gsNode, nodeMap);
		}
	}


	/**
	 * Convenience method for creating artifacts.
	 *
	 * @param id The identifier of the artifact.
	 * @return The created artifact.
	 */
	private Artifact.Op<?> A(String id) {
		return new MemArtifact<>(new TestArtifactData(id));
	}

	private Artifact.Op<?> A(String id, int number) {
		Artifact.Op<?> artifact = new MemArtifact<>(new TestArtifactData(id));
		artifact.setSequenceNumber(number);
		return artifact;
	}


	@BeforeTest(alwaysRun = true)
	public void beforeTest() {
		System.out.println("BEFORE");
	}

	@AfterTest(alwaysRun = true)
	public void afterTest() {
		System.out.println("AFTER");
	}

}
