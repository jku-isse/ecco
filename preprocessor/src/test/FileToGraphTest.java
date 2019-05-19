package test;

import org.junit.Test;

import at.jku.isse.ecco.importer.TraceImporter;
import at.jku.isse.ecco.storage.mem.repository.MemRepository;
import java.nio.file.Paths;

public class FileToGraphTest {
	
	@Test
	public void AnotherTest() {
		TraceImporter.importTrace(new MemRepository(), Paths.get("../../RepCopy/"));
	}
}
