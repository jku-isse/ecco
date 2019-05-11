package test;

import org.junit.Test;

import at.jku.isse.ecco.artifact.Artifact;
import at.jku.isse.ecco.exporter.PartialOrderGraphExporter;
import at.jku.isse.ecco.gui.view.graph.PartialOrderGraphView;
import at.jku.isse.ecco.importer.TraceImporter;
import at.jku.isse.ecco.pog.PartialOrderGraph;
import at.jku.isse.ecco.storage.mem.artifact.MemArtifact;
import at.jku.isse.ecco.storage.mem.pog.MemPartialOrderGraph;
import at.jku.isse.ecco.storage.mem.repository.MemRepository;
import at.jku.isse.ecco.test.TestArtifactData;
import at.jku.isse.ecco.test.Utility;
import javafx.scene.Scene;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("restriction")
public class FileToGraphTest {
	
	@Test
	public void AnotherTest() {
		TraceImporter.importTrace(new MemRepository(), Paths.get("../../RepCopy/"));
	}
}
