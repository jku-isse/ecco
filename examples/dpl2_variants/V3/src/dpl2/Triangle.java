package dpl2;

/** A triangle, defined by its three vertices, which must not be collinear. */
public record Triangle(Point a, Point b, Point c) implements Shape {

	public Triangle {
		if (Math.abs(signedArea(a, b, c)) < 1e-9) {
			throw new IllegalArgumentException("vertices must not be collinear");
		}
	}

	private static double signedArea(Point a, Point b, Point c) {
		return (b.x() - a.x()) * (c.y() - a.y()) - (c.x() - a.x()) * (b.y() - a.y());
	}

	public double area() {
		return Math.abs(signedArea(a, b, c)) / 2.0;
	}
}
