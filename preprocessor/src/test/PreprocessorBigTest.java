package test;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import at.jku.isse.ecco.EccoException;
import at.jku.isse.ecco.EccoService;
import at.jku.isse.ecco.adapter.dispatch.DirectoryArtifactData;
import at.jku.isse.ecco.adapter.text.LineArtifactData;
import at.jku.isse.ecco.core.Association;
import at.jku.isse.ecco.dao.TransactionStrategy.TRANSACTION;
import at.jku.isse.ecco.exporter.TraceExporter;
import at.jku.isse.ecco.importer.TraceImporterV2;
import at.jku.isse.ecco.module.Condition;
import at.jku.isse.ecco.storage.mem.artifact.MemArtifact;
import at.jku.isse.ecco.storage.mem.tree.MemNode;
import at.jku.isse.ecco.tree.Node;
import at.jku.isse.ecco.txt.TextFileLineImporter;

public class PreprocessorBigTest {

	private static final String REPO1_PATH = "testproject/MarlinRepository1";
	private static final String REPO2_PATH = "testproject/MarlinRepository2";
	private static final String IMPORT_FROM = "testproject/Marlin";
	private static final String EXPORT_TO = "testproject/MarlinExport";
	
	private static EccoService service1;
	private static EccoService service2;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		//create new repo
		try {
			new File(REPO1_PATH).mkdir();
			new File(REPO2_PATH).mkdir();
			new File(EXPORT_TO).mkdir();
			if(Files.notExists(Paths.get(IMPORT_FROM))) throw new IllegalStateException("The testproject in testproject/Marlin does not exist.");
			service1 = new EccoService(Paths.get(REPO1_PATH));
			if(service1.repositoryDirectoryExists())
				service1.open();
			else service1.init();
			importFiles(service1, IMPORT_FROM, REPO1_PATH);
			TraceExporter.exportAssociations(service1.getRepository().getAssociations(), Paths.get(EXPORT_TO));
			importFiles(service2, EXPORT_TO, REPO2_PATH);
			System.out.println("Initialized");
		} catch (EccoException e) {
			e.printStackTrace();
		}
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		System.out.println("Tests completed");
		System.out.println("Clean up...");
		FileUtils.deleteDirectory(new File(REPO1_PATH));
		FileUtils.deleteDirectory(new File(REPO2_PATH));
		FileUtils.deleteDirectory(new File(EXPORT_TO));
		System.out.println("Directory cleared");
		service1.close();
		service2.close();
		System.out.println("Clean up done");
	}

	@Test
	public static void compairTest() {
		Collection<? extends Association.Op> associations1 = service1.getRepository().getAssociations();
		Collection<? extends Association.Op> associations2 = service2.getRepository().getAssociations();
		assertEquals(associations1.size(), associations2.size());
		for (Association.Op association1 : associations1) {
			assertTrue(associations2
					.stream()
					.anyMatch((association2) -> matchesCondition(association1.computeCondition(), association2.computeCondition())));
		}
		
		for (Association.Op association1 : associations1) {
			assertTrue(associations2
					.stream()
					.anyMatch((association2) -> matchesNodes(association1.getRootNode(), association2.getRootNode())));
		}
	}	
	
	public static void importFiles(EccoService service, String importFrom, String repoPath) {
		service.transactionStrategy.begin(TRANSACTION.READ_WRITE);
		new TraceImporterV2(
				service.getRepository(), 
				Paths.get(importFrom), 
				Paths.get(repoPath), 
				".h", 
				new TextFileLineImporter())
		.importFolder();
		service.transactionStrategy.end();
		service.commit();
	}
	
	private static boolean matchesNodes(Node.Op a, Node.Op b) {
		return a.isUnique() == b.isUnique() && 
			   (a.getArtifact() == b.getArtifact() || a.getArtifact().equalsIgnoreSequenceNumber(b.getArtifact())) &&
			   a.getChildren().size() == b.getChildren().size() &&
			   a.getChildren().stream().allMatch((nodeA) -> b.getChildren().stream().anyMatch((nodeB) -> matchesNodes(nodeA, nodeB)));
	}

	private static boolean matchesCondition(Condition a, Condition b) {
		if (a.getPreprocessorConditionString().equals(b.getPreprocessorConditionString()))
			return true;
		else {
			// TODO better test
			return false;
		}
	}
	
	@Test
	public void testMatchesNode() {
		MemNode nodeA = new MemNode(new MemArtifact<>(new DirectoryArtifactData(Paths.get("test"))));
		MemNode nodeB = new MemNode(new MemArtifact<>(new DirectoryArtifactData(Paths.get("test"))));
		assertTrue(matchesNodes(nodeA, nodeB));
		nodeA.addChildren(new MemNode(new MemArtifact<>(new LineArtifactData("line1"))));
		nodeB.addChildren(new MemNode(new MemArtifact<>(new LineArtifactData("line1"))));
		nodeA.addChildren(new MemNode(new MemArtifact<>(new LineArtifactData("line2"))));
		nodeB.addChildren(new MemNode(new MemArtifact<>(new LineArtifactData("line2"))));
		nodeA.addChildren(new MemNode(new MemArtifact<>(new LineArtifactData("line3"))));
		nodeB.addChildren(new MemNode(new MemArtifact<>(new LineArtifactData("line3"))));
		assertTrue(matchesNodes(nodeA, nodeB));
		nodeA.addChildren(new MemNode(new MemArtifact<>(new LineArtifactData("line4a"))));
		nodeB.addChildren(new MemNode(new MemArtifact<>(new LineArtifactData("line4b"))));
		assertFalse(matchesNodes(nodeA, nodeB));
		nodeA.addChildren(new MemNode(new MemArtifact<>(new LineArtifactData("line4b"))));
		nodeB.addChildren(new MemNode(new MemArtifact<>(new LineArtifactData("line4a"))));
		assertTrue(matchesNodes(nodeA, nodeB));		
		nodeA.addChildren(new MemNode(new MemArtifact<>(new LineArtifactData("line5"))));
		assertFalse(matchesNodes(nodeA, nodeB));
		nodeB.addChildren(new MemNode(new MemArtifact<>(new LineArtifactData("line5"))));
		assertTrue(matchesNodes(nodeA, nodeB));
		nodeA.addChildren(new MemNode(new MemArtifact<>(new LineArtifactData("ine1"))));
		nodeB.addChildren(new MemNode(new MemArtifact<>(new LineArtifactData("ine3"))));
		nodeA.addChildren(new MemNode(new MemArtifact<>(new LineArtifactData("ine2"))));
		nodeB.addChildren(new MemNode(new MemArtifact<>(new LineArtifactData("ine1"))));
		nodeA.addChildren(new MemNode(new MemArtifact<>(new LineArtifactData("ine3"))));
		nodeB.addChildren(new MemNode(new MemArtifact<>(new LineArtifactData("ine2"))));
		assertTrue(matchesNodes(nodeA, nodeB));
	}

}
