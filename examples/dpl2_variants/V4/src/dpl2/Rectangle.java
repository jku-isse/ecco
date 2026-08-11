package dpl2;

/** An axis-aligned rectangle, defined by its top-left corner, width, and height. */
public record Rectangle(Point topLeft, double width, double height) implements Shape {

	public Rectangle {
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("width and height must be positive");
		}
	}

	public double area() {
		return width * height;
	}
}
