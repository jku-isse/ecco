package dpl2;

import java.util.List;

/** Turns a list of shapes into a human-readable report. */
public class ShapeDescriber {

	public String describe(Shape shape) {
		if (shape instanceof Circle c) {
			return "Circle centered at (%.1f, %.1f) with radius %.1f".formatted(c.center().x(), c.center().y(), c.radius());
		}
		return "Unknown shape";
	}

	public String sizeCategory(double area) {
		return switch ((int) (area / 10)) {
			case 0 -> "tiny";
			case 1, 2 -> "small";
			case 3, 4, 5 -> "medium";
			default -> "large";
		};
	}

	public String report(List<Shape> shapes) {
		StringBuilder sb = new StringBuilder();
		sb.append("""
				Shape Report
				============
				""");
		for (Shape shape : shapes) {
			sb.append("- ").append(describe(shape)).append("\n");
		}
		return sb.toString();
	}
}
