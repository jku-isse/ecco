package testp;

public class test2 {

}

public class testc {

	private int i;
	private int e;


	public test(int i, int e) {
		this.i = i;
		this.e = e;

		for (int i = 0; i < 10; i++) {
			System.out.println("test: " + i);
		}
	}

	public int getI() {
		return i;
	}

	public void setI(int i) {
		this.i = i;
	}

	public int getE() {
		return e;
	}

	public void setE(int e) {
		this.e = e;
	}

}
