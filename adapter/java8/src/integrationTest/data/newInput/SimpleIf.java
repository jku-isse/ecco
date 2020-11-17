package at.jku.isse.ecco.adapter.java.jdtast;

public class SimpleIf {

    public void test() {
        boolean a = true, b = false, c = true;

        if (a) {
            System.currentTimeMillis();
        } else if (b)
            System.out.println("Test");

        if (c)
            System.clearProperty("Test");
    }
}