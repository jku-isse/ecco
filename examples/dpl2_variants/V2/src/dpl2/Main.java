package dpl2;

import java.util.List;

public class Main {

	public static void main(String[] args) {
		List<Shape> shapes = List.of(
				new Circle(new Point(0, 0), 5),
				new Rectangle(new Point(10, 10), 4, 6)
		);

		ShapeDescriber describer = new ShapeDescriber();
		System.out.println(describer.report(shapes));
	}
}
