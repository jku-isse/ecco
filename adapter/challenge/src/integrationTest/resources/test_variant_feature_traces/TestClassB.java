package org.test;

import org.test.extern.x;
// comment
import org.test.extern.y;

public class TestClassB {

    private int intField;
    private String stringField;

    public void TestClassB(){
        System.out.println("constructor");
    }

    public String stringMethod(int intArgument){
        String stringVariable = "String";
        return stringVariable;
    }

    private class InnerClass {

        private boolean booleanField;

        public InnerClass(){
            System.out.println("constructor inner class");
        }

        private void innerClassMethod(boolean booleanArgument){ System.out.println("inner class method"); }
    }
}
