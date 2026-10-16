package at.jku.cdl.ecco.adapter.java;

import at.jku.isse.ecco.adapter.SourcePosition;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JavaASTPositionsTest {

	@Test
	public void classesMembersAndNestedTypesWithTheirLines(@TempDir Path base) throws Exception {
		Files.writeString(base.resolve("Zip.java"), """
				package p;

				@Deprecated
				public class Zip {
					private int level = 1;

					public Zip() {
					}

					@Override
					public String toString() {
						return "zip";
					}

					static class Entry {
						void read(int n) {
						}
					}
				}
				""");
		JavaASTReader reader = new JavaASTReader(new SerEntityFactory());
		Node file = reader.read(base, new Path[]{Path.of("Zip.java")}).iterator().next();

		assertEquals(List.of(
						new SourcePosition("class", "Zip", 3, 19),
						new SourcePosition("field", "Zip.level", 5, 5),
						new SourcePosition("constructor", "Zip.Zip", 7, 8),
						new SourcePosition("method", "Zip.toString", 10, 13),
						new SourcePosition("class", "Zip.Entry", 15, 18),
						new SourcePosition("method", "Zip.Entry.read", 16, 17)),
				reader.positions(file).stream().sorted(java.util.Comparator.comparingInt(SourcePosition::startLine).thenComparing(p -> -p.endLine())).toList());
		assertEquals("Zip.toString", SourcePosition.innermost(reader.positions(file), 12).name());
		assertEquals("Zip", SourcePosition.innermost(reader.positions(file), 9).name());
	}
}
