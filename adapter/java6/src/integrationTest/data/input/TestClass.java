package tz.jku.prse;

public class TestClass {
	static int i = 0, x = 5;
	final static int SLEEP = 1;
	public static void main(String[] args) {
		System.out.println("blubb");
//		for(;i < 1000;i++) {
//		for(;;) {
		print1(i, "PRINT2 TEST1");	print1(i,"PRINT2 TEST2");

			try {
				Thread.sleep(SLEEP);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
//		}

	}
	
	public static void print1(int i, String s ) {
		System.out.println(s + " " + i);
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		x++;i=i+3;
		i = i +x;
		if(i%2 == 0) {
	//		print2(i,"PRINT1 TEST1");
			PrintClass.print2("PRINT1 TEST1",i);
		} 
		else {
			print2(i,"PRINT1 TEST2");
		}		
	}
	
	public static void print2(int i, String s ) {
		System.out.println(s + " " + i);
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		i=i+3;
		x++;
		i = i +x;
		if(i%2 == 0) {
			PrintClass.print1("PRINT1 TEST1",i);
		} 
		else {
			print1(i,"PRINT2 TEST2");
		}		
	}

}

