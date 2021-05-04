package edu.kit.joana.ifc.sdg.qifc.qif_interpreter.ir;

import com.ibm.wala.ssa.SSAInvokeInstruction;
import edu.kit.joana.ifc.sdg.qifc.qif_interpreter.util.LogicUtil;
import edu.kit.joana.ifc.sdg.qifc.qif_interpreter.util.Substitution;
import org.logicng.formulas.Formula;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public abstract class RecursiveReturnValue<T> implements IReturnValue<T>, IRecursiveReturnValue<T> {

	private Method m;
	private SSAInvokeInstruction recCall;
	private int[] paramValNums;
	private int[] argValNums;
	private T returnValVars;

	private Map<Integer, Formula[]> primitiveArgsForNextCall;
	private Map<Integer, Formula[][]> arrayArgsForNextCall;

	public void registerRecCall(Method m, SSAInvokeInstruction recCall, T returnValVars) {
		this.m = m;
		this.recCall = recCall;
		this.paramValNums = Arrays.copyOfRange(m.getIr().getParameterValueNumbers(), 1, m.getParamNum());
		this.argValNums = new int[recCall.getNumberOfUses() - 1];
		for (int i = 1; i < recCall.getNumberOfUses(); i++) {
			argValNums[i - 1] = recCall.getUse(i);
		}
		this.returnValVars = returnValVars;
	}

	/**
	 * computes formulas that represents the return value of the called method {@code m} @ callsite {@code callsite} by {@code caller}.
	 * <p>
	 * Result is computed by obtaining the used arguments from {@code caller} and substituting those values in for the parameter variables of {@code returnDeps}
	 *
	 * @param callSite invokeInstruction where the return value is used
	 * @param caller   Method that calls the function. Has to contain value dependencies for the arguments used in {@code callSite}
	 * @return formulas that represents the return value of the called method
	 */
	@Override public T getReturnValueForCallSite(SSAInvokeInstruction callSite, Method caller) {
		if (this.isRecursive() && isRecursiveCall(callSite)) {
			return this.getRecursiveReturnValueForCallSite(callSite, caller);
		} else {
			return getReturnValueNonRecursiveCallsite(callSite, caller);
		}
	}

	@Override public T getRecursiveReturnValueForCallSite(SSAInvokeInstruction instruction, Method caller) {

		// args w/ which the recursive function was called
		int[] argValnums = new int[instruction.getNumberOfUses() - 1];
		for (int j = 0; j < argValnums.length; j++) {
			if (caller.isArrayType(instruction.getUse(j + 1))) {
				primitiveArgsForNextCall.put(paramValNums[j], caller.getDepsForValue(instruction.getUse(j + 1)));
			} else {
				arrayArgsForNextCall
						.put(paramValNums[j], caller.getArray(instruction.getUse(j + 1)).getValueDependencies());
			}
		}

		T singleCall = substituteAll(this.getReturnValue(), primitiveArgsForNextCall, arrayArgsForNextCall);

		for (int i = 0; i < this.m.getProg().getConfig().recDepthMax(); i++) {

			computeNextArgs();
			T nextCall = substituteAll(this.getReturnValue(), primitiveArgsForNextCall, arrayArgsForNextCall);

			Substitution s = new Substitution();
			singleCall = substituteReturnValue(singleCall, nextCall, returnValVars);
		}

		computeNextArgs();
		T lastRun = substituteAll(this.getReturnValueNoRecursion(), primitiveArgsForNextCall, arrayArgsForNextCall);
		return substituteReturnValue(singleCall, lastRun, returnValVars);
	}

	/*
	We have the arguments used for the current call, saved in the maps
	{@code primitiveArgsForNextCall} and {@code arrayArgsForNextCall}

	The method computes the arguments used for the next recursive call, and updates the two maps accordingly
	 */
	private void computeNextArgs() {
		HashMap<Integer, Formula[]> newPrimArgs = new HashMap<>();
		HashMap<Integer, Formula[][]> newArrArgs = new HashMap<>();

		for (int i = 0; i < paramValNums.length; i++) {
			if (this.primitiveArgsForNextCall.keySet().contains(paramValNums[i])) {
				Formula[] arg = m.getDepsForValue(argValNums[i]);
				newPrimArgs.put(paramValNums[i], substituteAll(arg, primitiveArgsForNextCall, arrayArgsForNextCall));
			} else {
				Formula[][] arg = m.getArray(argValNums[i]).getValueDependencies();
				newArrArgs.put(paramValNums[i], substituteAll(arg, primitiveArgsForNextCall, arrayArgsForNextCall));
			}
		}
		primitiveArgsForNextCall = newPrimArgs;
		arrayArgsForNextCall = newArrArgs;
	}

	private Formula[] substituteAll(Formula[] f, Map<Integer, Formula[]> primArgs, Map<Integer, Formula[][]> arrArgs) {
		return LogicUtil.applySubstitution(f, createSubstitution(primArgs, arrArgs));
	}

	private Formula[][] substituteAll(Formula[][] f, Map<Integer, Formula[]> primArgs,
			Map<Integer, Formula[][]> arrArgs) {
		return LogicUtil.applySubstitution(f, createSubstitution(primArgs, arrArgs));
	}

	private Substitution createSubstitution(Map<Integer, Formula[]> primArgs, Map<Integer, Formula[][]> arrArgs) {
		Substitution s = new Substitution();
		for (int i : primArgs.keySet()) {
			s.addMapping(m.getVarsForValue(i), primArgs.get(i));
		}
		for (int i : arrArgs.keySet()) {
			s.addMapping(m.getArray(i).getArrayVars(), arrArgs.get(i));
		}
		return s;
	}

	@Override public boolean isRecursiveCall(SSAInvokeInstruction call) {
		return this.isRecursive() && this.recCall.equals(call);
	}

	@Override public boolean isRecursive() {
		return recCall != null;
	}

	protected abstract T substituteAll(T returnValueNoRecursion, Map<Integer, Formula[]> primitiveArgsForNextCall,
			Map<Integer, Formula[][]> arrayArgsForNextCall);

	protected abstract T substituteReturnValue(T containsRecCall, T recCallReturnValue, T vars);
}
