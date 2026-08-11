package dpl2;

import java.util.List;

/** Turns a list of shapes into a human-readable report. */
public class ShapeDescriber {

	public String describe(Shape shape) {
		if (shape instanceof Circle c) {
			return "Circle centered at (%.1f, %.1f) with radius %.1f".formatted(c.center().x(), c.center().y(), c.radius());
		} else if (shape instanceof Rectangle r) {
			return "Rectangle at (%.1f, %.1f), %.1f x %.1f".formatted(r.topLeft().x(), r.topLeft().y(), r.width(), r.height());
		} else if (shape instanceof Triangle t) {
			return "Triangle with vertices %s, %s, %s".formatted(t.a(), t.b(), t.c());
		}
		return "Unknown shape";
	}

	public double areaOf(Shape shape) {
		if (shape instanceof Circle c) {
			return c.area();
		} else if (shape instanceof Rectangle r) {
			return r.area();
		} else if (shape instanceof Triangle t) {
			return t.area();
		}
		return 0;
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
		var sb = new StringBuilder();
		sb.append("""
				Shape Report
				============
				""");
		for (var shape : shapes) {
			sb.append("- ").append(describe(shape))
					.append(" (").append(sizeCategory(areaOf(shape))).append(")")
					.append("\n");
		}
		return sb.toString();
	}
}
