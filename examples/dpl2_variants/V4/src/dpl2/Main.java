package dpl2;

import java.util.List;

public class Main {

	public static void main(String[] args) {
		var shapes = List.of(
				new ColoredShape(new Circle(new Point(0, 0), 5), Color.RED),
				new ColoredShape(new Rectangle(new Point(10, 10), 4, 6), Color.BLUE),
				new ColoredShape(new Triangle(new Point(0, 0), new Point(4, 0), new Point(0, 3)), Color.GREEN)
		);

		var describer = new ShapeDescriber();
		System.out.println(describer.report(shapes));
	}
}
