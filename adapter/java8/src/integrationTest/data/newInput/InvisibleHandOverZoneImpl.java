package at.jku.mevss.ppu.simulation.machine.parts;

import at.jku.mevss.ppu.control.VariablePool;
import at.jku.mevss.ppu.j6.Optional;

import static at.jku.mevss.ppu.simulation.Constants.OFFSET_ELEMENT_HANDOVERZONE;
import static at.jku.mevss.ppu.simulation.GraphicUtils.drawAndFillRectangle;

/**
 * Created by Florian on 28.03.2017.
 */
public class InvisibleHandOverZoneImpl extends HandOverZone {

    private Element curElement = null;
    private final int x, y;

    public InvisibleHandOverZoneImpl(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    public Optional<Element> take() {
        Element cur = curElement;
        curElement = null;
        return Optional.ofNullable(cur);
    }

    @Override
    public Optional<Element> peek() {
        return Optional.ofNullable(curElement);
    }

    @Override
    public void put(Element element) {
        if (curElement != null)
            throw new IllegalStateException("HandOverZone is not empty. Only on empty HandOverZones 'put' is allowed!");
        curElement = element;
    }

    @Override
    public boolean isEmpty() {
        return curElement == null;
    }

    @Override
    public void drawTop(Graphics2D graphics, VariablePool variablePool) {
        if (curElement != null)
            drawAndFillRectangle(graphics, Color.BLACK, curElement.getColor(), x + OFFSET_ELEMENT_HANDOVERZONE, y + OFFSET_ELEMENT_HANDOVERZONE, Element.SIZE, Element.SIZE);
    }
}