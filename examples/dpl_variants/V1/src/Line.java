/**
 * DPL Line.java
 * @author Roberto E. Lopez-Herrejon
 * SEP SPL Course July 2010
 */

import java.awt.*;

/*
 * Note: For this class the changes you need to do relate to color handling. 
 */
public class Line {
	
	private Point startPoint, endPoint ;
		
	public void paint(Graphics g){
				g.setColor(Color.BLACK);
				g.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y);
	}

	public Line(
						Point start) {
		startPoint = start;
			}
	
	public void setEnd(Point end) {
		endPoint = end;
	}
	
	public Point getStart() { return startPoint; }
	
	public Point getEnd () { return endPoint; }
	
} // of Line
