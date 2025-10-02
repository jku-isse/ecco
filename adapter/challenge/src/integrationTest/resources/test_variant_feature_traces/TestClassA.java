package org.test;

import org.test.extern.x;
import org.test.extern.y;

public class TestClassA {

    enum testEnum {
        VALUEX,
        VALUEY,
        VALUEZ
    }

    private int intField;
    private String stringField;

    public void TestClassA(){
        // comment in constructor
        System.out.println("constructor");
    }

    public String stringMethod(int intArgument){
        // comment in method
        String stringVariable = "String";
        return stringVariable;
    }

    private void privateMethod(){
        System.out.println("private method");
    }

    public static int staticMethod(){
        return 1;
    }
}
