package edu.kit.joana.ifc.sdg.qifc.qif_interpreter.ir;

import com.ibm.wala.ssa.SSANewInstruction;
import edu.kit.joana.ifc.sdg.qifc.qif_interpreter.oopsies.UnexpectedTypeException;
import edu.kit.joana.ifc.sdg.qifc.qif_interpreter.util.LogicUtil;
import edu.kit.joana.util.Triple;
import org.logicng.formulas.Formula;

import java.util.Arrays;
import java.util.Stack;
import java.util.stream.IntStream;

public class Array<T extends Value> extends Value {

	private Type elementType;
	private int length;
	private T[] arr;
	private Formula[][] valueDependencies;

	public Array(int valNum) {
		super(valNum);
		this.setType(Type.ARRAY);
		this.setWidth(Type.ARRAY.bitwidth());
	}

	public static Array<? extends Value> newArray(Type t, int length, int valNum, boolean initWithVars) throws UnexpectedTypeException {
		if (t == Type.INTEGER) {
			Array<Int> array = new Array<>(valNum);
			array.length = length;
			array.arr = new Int[length];
			array.elementType = t;
			IntStream.range(0, length).forEach(i -> array.arr[i] = new Int(i));
			array.valueDependencies = (initWithVars) ? array.initVars(length) : array.initZeros(length);
			return array;
		}
		throw new UnexpectedTypeException(t);
	}

	private Formula[][] initVars(int length) {
		Formula[][] initialValues = new Formula[length][this.elementType.bitwidth()];
		for (int i = 0; i < length; i++) {
			Formula[] initValue = LogicUtil.createVars(this.getValNum(), this.elementType.bitwidth(), "a"+ i);
			initialValues[i] = initValue;
		}
		return initialValues;
	}

	private Formula[][] initZeros(int length) {
		Formula[][] initialValues = new Formula[length][this.elementType.bitwidth()];
		IntStream.range(0, length).forEach(k -> Arrays.fill(initialValues[k], LogicUtil.ff.constant(false)));
		return initialValues;
	}

	public static Array<? extends Value> newArray(SSANewInstruction instruction, Method m, boolean initWithVars) throws UnexpectedTypeException {
		Value length = m.getValue(instruction.getUse(0));
		Type content = Type.from(instruction.getConcreteType().getArrayElementType());
		assert (length.isConstant() & length instanceof Int);
		assert (content.isPrimitive());
		return Array.newArray(content, (Integer) length.getVal(), instruction.getDef(), initWithVars);
	}

	@Override public boolean verifyType(Object val) {
		return val instanceof Array;
	}

	@Override public boolean isArrayType() {
		return true;
	}

	public int length() {
		return this.length;
	}

	public Formula[] boolLength() {
		return LogicUtil.asFormulaArray(LogicUtil.twosComplement(length, this.getWidth()));
	}

	public Type elementType() {
		return this.elementType;
	}

	public T access(int idx) {
		return this.arr[idx];
	}

	public void store(Object val, int idx, int recursionDepth) {
		Value dest = arr[idx];
		assert (dest.verifyType(val));
		dest.setVal(val, recursionDepth);
	}

	public void addAssignment(Formula implicitIF, int idx, Formula assignmentCond, Formula[] assignedValue) {
		this.valueDependencies[idx] = LogicUtil.ternaryOp(LogicUtil.ff.and(implicitIF, assignmentCond), assignedValue, this.valueDependencies[idx]);
	}

	public Formula[] currentlyAssigned(int idx) {
		return valueDependencies[idx];
	}

	public Formula[][] getValueDependencies() {
		return valueDependencies;
	}

	public void setValueDependencies(Formula[][] deps) {
		this.valueDependencies = deps;
	}

	public void setValueDependencies(int idx, Formula[] deps) {
		this.valueDependencies[idx] = deps;
	}
}
