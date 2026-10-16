package at.jku.isse.ecco.adapter.c;

import at.jku.isse.ecco.adapter.SourcePosition;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CPositionsTest {

	@Test
	public void functionsWithTheirLines(@TempDir Path base) throws Exception {
		Files.writeString(base.resolve("main.c"), """
				#include <stdio.h>

				static int add(int a, int b)
				{
					return a + b;
				}

				int main(void) {
					return add(1, 2);
				}
				""");
		CReader reader = new CReader(new SerEntityFactory());
		Node file = reader.read(base, new Path[]{Path.of("main.c")}).iterator().next();

		assertEquals(List.of(new SourcePosition("function", "add", 3, 6), new SourcePosition("function", "main", 8, 10)), reader.positions(file));
	}
}
