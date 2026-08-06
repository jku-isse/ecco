package at.jku.isse.ecco.java.test;

import com.sun.istack.internal.NotNull;

public class SwitchCase {
    public void test(@NotNull String s) {
        int i = 1, s = (-4 + 1);
        switch (i) {
            case 0:
            case 1:
                System.out.println('a');
                break;
            case 2:
                break;
            case 5:
                System.out.println("5");
            case 3:
                System.out.println("TestAnnotation");
                break;
            case 4:
            default:
                throw new Exception("TestData");
        }

    }

    public void test2() {
        boolean a = null instanceof Boolean, b = true, c = false;
        if (a)
            doSomeThing();
        else if (b)
            soSomeotherThing();
        else if (!c)
            doSomeYetOtherThing();
        else
            nono();

    }
}
