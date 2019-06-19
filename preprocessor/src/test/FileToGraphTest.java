package test;

import org.junit.Test;

import at.jku.isse.ecco.importer.TraceImporter;
import at.jku.isse.ecco.storage.mem.repository.MemRepository;
import at.jku.isse.ecco.txt.TextFileLineImporter;

import java.nio.file.Paths;

public class FileToGraphTest {
	
	@Test
	public void AnotherTest() {
		TraceImporter.importFolder(new MemRepository(), Paths.get("../../RepCopy/"), ".txt", new TextFileLineImporter());
	}
}
