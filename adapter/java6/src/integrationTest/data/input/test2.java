package tz.jku.prse;

public class PrintClass {

	public static int f;

	public void print1(String s, int i) {

		System.out.println(s + " " + i);
		int x;
		TestClass.i = TestClass.i + 2;
		InnerClass ic;
		x=i;
		//	i=i+1;
		ic = new InnerClass();
		ic.innerMethod();
		if(x%2 == 0) {
			print2("TEST1", i);
		}
		else {
			TestClass.print1(i,"TEST2");
		}
	}
	public static void xy() {
		System.out.println("asdf");
	}

	public static void print2(String s, int i) {
		System.out.println("PRINT2" + s + " " + i);
		TestClass.i+= 4;
		try {
			if(i < 50) {

				i=i+1;


				if(i%2 == 0) {
					TestClass.print1(i,"ZZZZZZZZZZZZZZZZZZZZ");
				}
				else {
					TestClass.print2(i,"TEST2");
				}
			} else {
				System.exit(1);
			}}
		catch(Exception e) { e.printStackTrace();}

		System.out.println("print2 end");
	}


	public void ufga() {
		System.out.println("asdf");
	}

	public class InnerClass {
		public void innerMethod2() {
			System.out.println("inner class method 2");
		}
		public void innerMethod() {
			System.out.println("inner class method");
		}
	}

}
