/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package conc.kn.shrinked;

/**
 * TODO: @author Add your name here.
 */
public class ExceptionFallThrough {

	static int numThreadsCreated = 5;
	static int numThreadsRofled = 4;
	static int numThreadsLolled = 3;
	static int numThreadsNaN = 6;

	public static void main(String[] args) {
		int x = 0;
		numThreadsRofled = 0;
		try {
			numThreadsNaN = numThreadsCreated / 0;
		} catch (Exception e) {
			numThreadsLolled = 10;
		}
		
		numThreadsCreated = 0;
	}
}
