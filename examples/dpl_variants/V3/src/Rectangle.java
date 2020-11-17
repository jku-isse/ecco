/**
 * DPL Rectangle.java
 * @author Roberto E. Lopez-Herrejon
 * SEP SPL Course July 2010
 */


import java.awt.Color;
import java.awt.Graphics;

/*
 * Note: For this class the only changes you need to do relate to color handling. 
 */
public class Rectangle {
	// Main data of the rectangle
	private int x, y, width, height ;

	// Auxiliary variables for drawing
	private int dx, dy;  // computed deltas for each dimension
	private int x2, y2;  // x,y coordinates last read while dragging
	
		private Color color;
		
	/**
	 * Called when the component Canvas is repainted
	 * @param g
	 */
	public void paint(Graphics g){
		int cornerX = x, cornerY = y;
		if (dy < 0) { // new hight < 0
			if (dx >=0) {
				// upper right cuadrant
				cornerX = x; cornerY = y2;
			} else {
				// upper left cuadrant
				cornerX = x2; cornerY = y2;
			}
		} else { // height >=0 
			if (dx >=0) {
				// bottom right cuadrant
				cornerX = x; cornerY = y;
			}else {
				// bottom left cuadrant
				cornerX = x2; cornerY = y;
			}
		}

				g.setColor(color);
				g.drawRect(cornerX, cornerY, width, height);
	}

	public Rectangle(
						Color color, 
						int x, int y) { 
				this.color = color;
				this.x = x; this.y = y;
	}
	
	public void setEnd(int newX, int newY) {
		width = StrictMath.abs(newX-x);
		height = StrictMath.abs(newY-y);
		dx = newX - x;
		dy = newY - y;
		x2 = newX;
		y2 = newY;
	}

	/** Called after rectangle is drawn. 
	 *  Adjusts the coordinate values of x and y
	 */
	public void updateCorner() {
		int cornerX = x, cornerY = y;
		if (dy < 0) { // new hight < 0
			if (dx >=0) {
				// upper right cuadrant
				cornerX = x; cornerY = y2;
			} else {
				// upper left cuadrant
				cornerX = x2; cornerY = y2;
			}
		} else { // height >=0 
			if (dx >=0) {
				// bottom right cuadrant
				cornerX = x; cornerY = y;
			}else {
				// bottom left cuadrant
				cornerX = x2; cornerY = y;
			}
		}
		x = cornerX; y = cornerY;
	}
	
	public int getX() { return x; }
	
	public int getY() { return y; }
	
	public int getWidth() { return width; }
	
	public int getHeight() { return height; }


}
