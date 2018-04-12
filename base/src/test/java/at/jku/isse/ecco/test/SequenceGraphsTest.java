package at.jku.isse.ecco.test;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.gui.view.graph.SequenceGraphView;
import at.jku.isse.ecco.sg.SequenceGraph;
import at.jku.isse.ecco.storage.mem.artifact.MemArtifact;
import at.jku.isse.ecco.storage.mem.sg.MemSequenceGraph;
import javafx.scene.Scene;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

public class SequenceGraphsTest {


	@Test(groups = {"unit", "base", "sg"})
	public void SequenceGraphs_Full() {
		List<Artifact.Op<?>> artifacts1 = Arrays.asList(A("1"), A("8"), A("2"), A("7"));
		List<Artifact.Op<?>> artifacts2 = Arrays.asList(A("1"), A("10"), A("3"));
		List<Artifact.Op<?>> artifacts3 = Arrays.asList(A("1"), A("9"), A("2"), A("4"), A("5"));
		List<Artifact.Op<?>> artifacts4 = Arrays.asList(A("1"), A("6"), A("4"), A("3"));

		SequenceGraph.Op sg1 = new MemSequenceGraph();
		SequenceGraph.Op sg2 = new MemSequenceGraph();


		// align sequence to sg
		sg1.sequence(artifacts1);
		sg1.sequence(artifacts3);
//		sg1.sequence(artifacts2);
//		sg1.sequence(artifacts4);


		sg2.sequence(artifacts2);
		sg2.sequence(artifacts4);


		// align sg to sg
		sg1.sequence(sg2);


		// get all orders from sg -> must be merge of all orders that were added before
		// do i need this?


		// display sg for manual inspection
		//displaySG(sg1);
	}


	private void displaySG(SequenceGraph sg) {
		Utility.launchApp((app, stage) -> {
			SequenceGraphView sequenceGraphView = new SequenceGraphView();
			sequenceGraphView.showGraph(sg);

			stage.setWidth(300);
			stage.setHeight(300);

			Scene scene = new Scene(sequenceGraphView);
			stage.setScene(scene);

			stage.show();
		}, new String[]{});
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


	@BeforeTest(alwaysRun = true)
	public void beforeTest() {
		System.out.println("BEFORE");
	}

	@AfterTest(alwaysRun = true)
	public void afterTest() {
		System.out.println("AFTER");
	}

}
