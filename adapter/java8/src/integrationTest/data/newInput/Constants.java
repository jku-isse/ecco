package at.jku.mevss.ppu.simulation;

import at.jku.mevss.ppu.simulation.machine.parts.Element;

/**
 * Created by Florian on 15.04.2017.
 */
public class Constants {
    //#if stack  && stackCylinder
    public static final int STACKCYLINDER_PUSH_SIZE_SMALL = 10;
    //#endif
    public static final int SIZE_HANDOVERZONE = 32;
    public static final int SIZE_HALF_HANDOVERZONE = SIZE_HANDOVERZONE / 2;
    public static final int OFFSET_ELEMENT_HANDOVERZONE = (SIZE_HANDOVERZONE - Element.SIZE) / 2;

    public static final int TICK_INTERVAL_MS = 50;

    public static final int GLOBAL_OFFSET_X, GLOBAL_OFFSET_Y;

    static {
        int x = 0, y = 0;
        //#if sorting && !woodenMetallicPieces
        x += 150;
        //#endif
        GLOBAL_OFFSET_X = x;
        GLOBAL_OFFSET_Y = y;
    }

}