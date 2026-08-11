package dpl2;

/** The closed set of shapes this product line can draw. */
public sealed interface Shape permits Circle, Rectangle {
}
