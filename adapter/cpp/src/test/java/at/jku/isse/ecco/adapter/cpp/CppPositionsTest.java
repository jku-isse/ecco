package at.jku.isse.ecco.adapter.cpp;

import at.jku.isse.ecco.adapter.SourcePosition;
import at.jku.isse.ecco.storage.ser.dao.SerEntityFactory;
import at.jku.isse.ecco.tree.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CppPositionsTest {

	@Test
	public void scopesNestedWithTheirLines(@TempDir Path base) throws Exception {
		Files.writeString(base.resolve("geo.cpp"), """
				#include <cmath>

				namespace geo {
				class Point {
				public:
					double norm() const {
						return 0;
					}
				};
				}

				int Point_count(int n) {
					return n;
				}
				""");
		CppReader reader = new CppReader(new SerEntityFactory());
		Node file = reader.read(base, new Path[]{Path.of("geo.cpp")}).iterator().next();

		assertEquals(List.of(
						new SourcePosition("namespace", "geo", 3, 10),
						new SourcePosition("class", "geo::Point", 4, 9),
						new SourcePosition("function", "geo::Point::norm", 6, 8),
						new SourcePosition("function", "Point_count", 12, 14)),
				reader.positions(file).stream().sorted(Comparator.comparingInt(SourcePosition::startLine)).toList());
	}
}
