package dpl2;

import java.util.List;

/** Turns a list of colored shapes into a human-readable report. */
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

	// Java 21 pattern-matching switch, exhaustive over Shape's sealed permits with no default
	// needed - as of this writing, adapter/java-ast's writer (StaticJavaParser at LanguageLevel
	// JAVA_18, the highest non-preview level JavaParser 3.25.8 offers) cannot parse this back out,
	// so importing/committing this variant is expected to fail loudly. Kept deliberately, as the
	// project's boundary-testing case for that known limitation - see adapter/java-ast's
	// JavaASTLanguageLevelTest.patternMatchingSwitchFailsLoudlyInsteadOfSilentlyTruncatingTheFile().
	public String describeViaPatternSwitch(Shape shape) {
		return switch (shape) {
			case Circle c -> "Circle r=" + c.radius();
			case Rectangle r -> "Rectangle " + r.width() + "x" + r.height();
			case Triangle t -> "Triangle area=" + t.area();
		};
	}

	public String describe(ColoredShape colored) {
		return describe(colored.shape()) + " in " + colored.color();
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

	public String report(List<ColoredShape> shapes) {
		var sb = new StringBuilder();
		sb.append("""
				Shape Report
				============
				""");
		for (var colored : shapes) {
			sb.append("- ").append(describe(colored))
					.append(" (").append(sizeCategory(areaOf(colored.shape()))).append(")")
					.append("\n");
		}
		return sb.toString();
	}
}
