package tz.jku.prse;

public class PrintClass {
	public static void print1(String s, int i) {
		System.out.println(s + " " + i);
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	//	i=i+1;
		if(i%2 == 0) {
//			TestClass.print2(i,"TEST1");
		} 
		else {
//			TestClass.print1(i,"TEST2");
		}		
	}
	
	public static void print2(String s, int i) {
		System.out.println("PRINT2" + s + " " + i);
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		i=i+1;
		if(i%2 == 0) {
			TestClass.print1(i,"TEST1");
		} 
		else {
			TestClass.print2(i,"TEST2");
		}		
	}
	
}
