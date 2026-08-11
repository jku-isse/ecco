package dpl2;

import java.util.List;

public class Main {

	public static void main(String[] args) {
		var shapes = List.<Shape>of(
				new Circle(new Point(0, 0), 5),
				new Rectangle(new Point(10, 10), 4, 6),
				new Triangle(new Point(0, 0), new Point(4, 0), new Point(0, 3))
		);

		var describer = new ShapeDescriber();
		System.out.println(describer.report(shapes));

		for (var shape : shapes) {
			System.out.println(describer.describe(shape) + " -> area " + describer.areaOf(shape));
		}
	}
}
