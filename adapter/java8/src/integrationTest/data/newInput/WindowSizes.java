package at.jku.mevss.ppu;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by Florian on 08.04.2017.
 */
public class WindowSizes {

    public static final int OFFSET_X = 200, OFFSET_Y = 110;

    private static class VisualisationHeight {
        static final int base = 75;
        static final int alarm = 175;
    }

    private static class SimulationHight {
        static final int base = 400;
        static final int rampTake = 50;
    }

    private static class Width {
        static final int base = 400;
        static final int stamp = 260;
    }

    private static int sumAllIntFields(Class<?> targetClass) {
        final Field[] declaredFields = targetClass.getDeclaredFields();

        List<Field> staticIntFields = new LinkedList<Field>();

        for (Field f : declaredFields) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == Integer.TYPE)
                staticIntFields.add(f);
        }
        int sum = 0;
        for (Field f : staticIntFields) {
            try {
                int i = f.getInt(null);
                if (i > 0)
                    sum += i;
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                return -1;
            }
        }
        return sum;
    }

    public static int getSimulationHeight() {
        return sumAllIntFields(SimulationHight.class);
    }

    public static int getVisualisationHeight() {
        return sumAllIntFields(VisualisationHeight.class);
    }

    public static int getWidth() {
        return sumAllIntFields(Width.class);
    }
}
