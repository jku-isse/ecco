package at.jku.isse.ecco.test;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.gui.view.graph.PartialOrderGraphView;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.storage.mem.artifact.MemArtifact;
import at.jku.isse.ecco.storage.mem.pog.MemPartialOrderGraph;
import at.jku.isse.ecco.storage.mem.pog.MemPartialOrderGraphNode;
import javafx.scene.Scene;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class PartialOrderGraphTest {

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
