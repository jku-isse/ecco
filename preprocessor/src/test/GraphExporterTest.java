package test;

import static org.junit.Assert.*;

import org.junit.Test;
import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.exporter.PartialOrderGraphExporter;
import at.jku.isse.ecco.gui.view.graph.PartialOrderGraphView;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.storage.mem.artifact.MemArtifact;
import at.jku.isse.ecco.storage.mem.pog.MemPartialOrderGraph;
import at.jku.isse.ecco.storage.mem.pog.MemPartialOrderGraphNode;
import at.jku.isse.ecco.test.TestArtifactData;
import at.jku.isse.ecco.test.Utility;
import javafx.scene.Scene;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class GraphExporterTest {

	@Test
	public void AnotherTest() {
		List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("1"), A("2"), A("3"), A("4"), A("5"), A("6"));
		List<Artifact.Op<?>> artifacts2 = Arrays.asList(A("1"), A("2"), A("3"), A("4"), A("5"), A("7"), A("8"), A("6"));
		List<Artifact.Op<?>> artifacts3 = Arrays.asList(A("1"), A("2"), A("3"), A("4"), A("5"), A("9"), A("10"),
				A("6"));
		List<Artifact.Op<?>> artifacts4 = Arrays.asList(A("1"), A("2"), A("3"), A("4"), A("5"), A("11"), A("12"),
				A("6"));

		PartialOrderGraph.Op pog1 = new MemPartialOrderGraph();

		pog1.merge(artifacts1);
		pog1.merge(artifacts3);
		pog1.merge(artifacts2);
		pog1.merge(artifacts4);


		PartialOrderGraphExporter.export(pog1, Paths.get("../../RepCopy/outAnotherTest.txt"));
	}

	@Test
	public void PPU_Test() {
		List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("A"), A("B"), A("C"), A("D"), A("E"), A("F"), A("G"), A("H"),
				A("I"), A("J"), A("K"), A("L"), A("M"));
		List<Artifact.Op<?>> artifacts2 = Arrays.asList(A("A"), A("H"), A("I"), A("J"), A("K"), A("L"), A("N"), A("O"),
				A("F"), A("B"), A("C"), A("M"));

		PartialOrderGraph.Op pog = new MemPartialOrderGraph();

		pog.merge(artifacts1);
		System.out.println("--------------");

		// pog.align(artifacts2);
		// for (Artifact.Op<?> artifact : artifacts2) {
		// System.out.println(artifact + " [" + artifact.getSequenceNumber() +
		// "]");
		// }

		pog.merge(artifacts2);
		displayPOG(pog);

		PartialOrderGraphExporter.export(pog, Paths.get("../../RepCopy/outPPU.txt"));
	}

	@Test
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
		// pog1.merge(artifacts2);
		// pog1.merge(artifacts4);

		displayPOG(pog1);

		PartialOrderGraphExporter.export(pog1, Paths.get("../../RepCopy/outFull.txt"));
	}

	@Test
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

		PartialOrderGraphExporter.export(pog1, Paths.get("../../RepCopy/outMerge3.txt"));
	}

	private void displayPOG(PartialOrderGraph pog) {
		Utility.launchApp((app, stage) -> {
			PartialOrderGraphView partialOrderGraphView = new PartialOrderGraphView();
			partialOrderGraphView.showGraph(pog);

			stage.setWidth(300);
			stage.setHeight(300);

			Scene scene = new Scene(partialOrderGraphView);
			stage.setScene(scene);

			stage.show();
		});
	}

	/**
	 * Convenience method for creating artifacts.
	 *
	 * @param id
	 * @return
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
