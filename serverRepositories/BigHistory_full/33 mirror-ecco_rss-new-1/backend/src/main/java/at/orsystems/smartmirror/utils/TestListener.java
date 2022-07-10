package at.orsystems.smartmirror.utils;

/**
 * @author Michael
 * @since 2021
 */
public interface TestListener {
    void doNotify();

    default void testListener(){
        System.out.println("Do something");
    }
}
