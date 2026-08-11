package dpl2;

/** A circle, defined by its center and a strictly positive radius. */
public record Circle(Point center, double radius) implements Shape {

	public Circle {
		if (radius <= 0) {
			throw new IllegalArgumentException("radius must be positive, was " + radius);
		}
	}

	public double area() {
		return Math.PI * radius * radius;
	}
}
