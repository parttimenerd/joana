import edu.kit.joana.ifc.sdg.qifc.qif_interpreter.input.Out;

public class Array {

	public  static void main(String[] args) {

		Array a = new Array();
		a.f(1);

	}

	public int f(int h) {
		int l = 1 + 2;
		int[] a = new int[l];
		a[0] = h;
		l = a[0] + 1;
		Out.print(l);
		return 0;
	}

}