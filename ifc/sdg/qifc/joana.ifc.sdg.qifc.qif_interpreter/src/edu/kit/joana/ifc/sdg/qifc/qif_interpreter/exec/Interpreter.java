package edu.kit.joana.ifc.sdg.qifc.qif_interpreter.exec;

import com.ibm.wala.shrikeBT.IBinaryOpInstruction;
import com.ibm.wala.shrikeBT.IConditionalBranchInstruction;
import com.ibm.wala.shrikeBT.IUnaryOpInstruction;
import com.ibm.wala.ssa.*;
import edu.kit.joana.ifc.sdg.qifc.qif_interpreter.Util;
import edu.kit.joana.ifc.sdg.qifc.qif_interpreter.ir.Value;
import edu.kit.joana.ifc.sdg.qifc.qif_interpreter.ir.*;
import edu.kit.joana.ifc.sdg.qifc.qif_interpreter.oopsies.MissingValueException;
import edu.kit.joana.ifc.sdg.qifc.qif_interpreter.oopsies.OutOfScopeException;
import edu.kit.joana.ifc.sdg.qifc.qif_interpreter.oopsies.ParameterException;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.List;

public class Interpreter {

	private final Program program;
	private final PrintStream out;

	public Interpreter(Program p, PrintStream out) {
		this.program = p;
		this.out = out;
	}

	public Interpreter(Program p, List<String> args) {
		this(p, System.out);
	}


	public boolean execute(List<String> args) throws ParameterException, OutOfScopeException, MissingValueException {

		if (!applyArgs(args, program, program.getEntryMethod())) {
			throw new ParameterException("Wrong input parameter for program.");
		}

		ExecutionVisitor ev = new ExecutionVisitor(program.getEntryMethod());
		executeMethod(program.getEntryMethod(), args);

		return true;
	}

	/**
	 * @param m the method to execute
	 * @param args input parameters for the method
	 * @throws OutOfScopeException if the method contains an instruction that is not implemented for this interpreter
	 */
	public void executeMethod(Method m, List<String> args) throws OutOfScopeException {
		ExecutionVisitor ev = new ExecutionVisitor(m);

		int prevBlock = -1;
		int currentBlock = program.getEntryMethod().getCFG().entry().idx();
		int nextBlock = ev.executeBlock(program.getEntryMethod().getCFG().entry(), prevBlock);

		while (nextBlock != -1) {
			prevBlock = currentBlock;
			currentBlock = nextBlock;
			BBlock next = BBlock.getBlockForIdx(nextBlock);
			nextBlock = ev.executeBlock(next, prevBlock);
		}
		m.setReturnValue(ev.returnValue);
	}

	public boolean applyArgs(List<String> args, Program p, Method m) throws MissingValueException {
		if (!(m.getCg().getMethod().getNumberOfParameters() - 1 == args.size())) {
			return false;
		}

		// i refers to the position of the input arguments in the args array
		// in the program, the first parameter of every function is the 'this' reference
		// hence we need to access the parameters w/ i + 1
		for(int i = 0; i < args.size(); i++) {
			int paramNum = i + 1;
			Object paramVal;
			switch(m.getParamType(paramNum)) {
			case INTEGER:
				try {
					paramVal = Integer.parseInt(args.get(i));
				} catch (NumberFormatException e) {
					return false;
				}
				break;
			case CUSTOM:
			default:
				throw new IllegalStateException("Unexpected value: " + m.getParamType(paramNum));
			}

			int valNum = m.getIr().getParameter(paramNum);
			Value param = m.getValueOrConstant(valNum, m.getParamType(paramNum));
			param.setVal(paramVal);
		}
		return true;
	}

	public class ExecutionVisitor implements SSAInstruction.IVisitor {

		private static final String OUTPUT_FUNCTION = "edu.kit.joana.ifc.sdg.qifc.qif_interpreter.input.Out.print(I)V";

		private final Method m;
		private BBlock block;
		private int prevBlockIdx;
		private int nextBlockIdx;

		private boolean containsOutOfScopeInstruction;
		private SSAInstruction outOfScopeInstruction;

		private int returnValue = -1;

		public ExecutionVisitor(Method m) {
			this.m = m;
		}

		/**
		 * executes a single basic block and returns the index of tha basic block to be executed next. If the return value is -1, the program is terminated
		 * @param start the basic block to execute
		 * @param prevBlockIdx the idx of the basic block that was previously executed (needed to  correctly evaluate phis). If it is the first block of a program to be executed, this value should be -1
		 * @return the index of the next block to be executed or -1 if the execution is finished
		 */
		public int executeBlock(BBlock start, int prevBlockIdx) throws OutOfScopeException {
			if (start.isExitBlock()) {
				return -1;
			}

			block = start;
			this.prevBlockIdx = prevBlockIdx;
			this.nextBlockIdx = -1;

			start.getWalaBasicBLock().iteratePhis().forEachRemaining(this::visitPhi);

			for (SSAInstruction i: start.instructions()) {
				i.visit(this);
			}

			if (containsOutOfScopeInstruction) {
				throw new OutOfScopeException(outOfScopeInstruction);
			}

			if (nextBlockIdx != -1) {
				// idx of the next block has already been set by a conditional / jump instruction. We can directly return it
				return nextBlockIdx;
			} else {
				// no control flow relevant instruction has been executed, so we simply continue w/ the next basic block.
				// we can be sure it exists because {@code} start is not the exit block
				List<ISSABasicBlock> normalSuccs = Util.asList(start.getCFG().getWalaCFG().getNormalSuccessors(start.getWalaBasicBLock()));
				assert(normalSuccs.size() == 1);
				return normalSuccs.get(0).getNumber();
			}
		}

		@Override public void visitGoto(SSAGotoInstruction instruction) {
			nextBlockIdx = block.getCFG().getMethod().getBlockStartingAt(instruction.getTarget()).idx();
		}

		@Override public void visitArrayLoad(SSAArrayLoadInstruction instruction) {
			containsOutOfScopeInstruction = true;
		}

		@Override public void visitArrayStore(SSAArrayStoreInstruction instruction) {
			containsOutOfScopeInstruction = true;
		}

		@Override public void visitBinaryOp(SSABinaryOpInstruction instruction) {
			Integer op1 = null;
			Integer op2 = null;
			try {
				op1 = (Integer) m.getValueOrConstant(instruction.getUse(0),
						Type.INTEGER).getVal();
				op2 = (Integer) m.getValueOrConstant(instruction.getUse(1),
						Type.INTEGER).getVal();
			} catch (MissingValueException e) {
				e.printStackTrace();
			}
			IBinaryOpInstruction.Operator operator = (IBinaryOpInstruction.Operator) instruction.getOperator();

			assert(op1 != null);
			assert(op2 != null);

			int def;
			switch (operator) {
			case ADD:
				def = op1 + op2;
				break;
			case SUB:
				def = op1 - op2;
				break;
			case MUL:
				def = op1 * op2;
				break;
			case DIV:
				def = op1 / op2;
				break;
			case REM:
				def = op1 % op2;
				break;
			case AND:
				def = op1 & op2;
				break;
			case OR:
				def = op1 | op2;
				break;
			case XOR:
				def = op1 ^ op2;
				break;
			default:
				throw new IllegalStateException("Unexpected value: " + operator);
			}
			if (!m.hasValue(instruction.getDef())) {
				Value defVal = Value.createByType(instruction.getDef(), Type.getResultType(operator, m.type(instruction.getUse(0)), m.type(instruction.getUse(1))));
				m.addValue(instruction.getDef(), defVal);
			}
			try {
				m.setValue(instruction.getDef(), def);
			} catch (MissingValueException e) {
				e.printStackTrace();
			}
		}

		@Override public void visitUnaryOp(SSAUnaryOpInstruction instruction) {
			int use = 0;
			try {
				use = (Integer) m.getValueOrConstant(instruction.getUse(0),
						Type.INTEGER).getVal();
			} catch (MissingValueException e) {
				e.printStackTrace();
			}
			if (!instruction.getOpcode().equals(IUnaryOpInstruction.Operator.NEG)) {
				throw new IllegalStateException("Unexpected value: " + instruction.getOpcode());
			} else {
				if (!m.hasValue(instruction.getDef())) {
					Value defVal = Value.createByType(instruction.getDef(), Type.getResultType(
							(IUnaryOpInstruction.Operator) instruction.getOpcode(), m.type(instruction.getUse(0))));
					m.addValue(instruction.getDef(), defVal);
				}
				try {
					m.setValue(instruction.getDef(), -use);
				} catch (MissingValueException e) {
					e.printStackTrace();
				}
			}
		}

		@Override public void visitConversion(SSAConversionInstruction instruction) {
			containsOutOfScopeInstruction = true;
		}

		@Override public void visitComparison(SSAComparisonInstruction instruction) {
			containsOutOfScopeInstruction = true;
		}

		@Override public void visitConditionalBranch(SSAConditionalBranchInstruction instruction) {
			Integer op1 = null;
			Integer op2 = null;
			try {
				op1 = (Integer) m.getValueOrConstant(instruction.getUse(0),
						Type.INTEGER).getVal();
				op2 = (Integer) m.getValueOrConstant(instruction.getUse(1),
						Type.INTEGER).getVal();
			} catch (MissingValueException e) {
				e.printStackTrace();
			}

			IConditionalBranchInstruction.Operator operator = (IConditionalBranchInstruction.Operator) instruction.getOperator();
			boolean result;

			switch (operator) {
			case EQ:
				result = op1.equals(op2);
				break;
			case NE:
				result = !op1.equals(op2);
				break;
			case LT:
				result = op1 < op2;
				break;
			case GE:
				result = op1 >= op2;
				break;
			case GT:
				result = op1 > op2;
				break;
			case LE:
				result = op1 <= op2;
				break;
			default:
				throw new IllegalStateException("Unexpected value: " + operator);
			}

			List<ISSABasicBlock> succs = Util.asList(block.getCFG().getWalaCFG().getNormalSuccessors(block.getWalaBasicBLock()));
			assert(succs.size() == 2);


			BBlock trueTargetBlock = block.getCFG().getMethod().getBlockStartingAt(instruction.getTarget());
			succs.removeIf(b -> b.getNumber() == trueTargetBlock.idx());
			assert(succs.size() == 1);

			nextBlockIdx = (result) ? trueTargetBlock.idx() : succs.get(0).getNumber();
		}

		@Override public void visitSwitch(SSASwitchInstruction instruction) {
			containsOutOfScopeInstruction = true;
			outOfScopeInstruction = instruction;
		}

		@Override public void visitReturn(SSAReturnInstruction instruction) {
			this.returnValue = instruction.getResult();
		}

		@Override public void visitGet(SSAGetInstruction instruction) {
			containsOutOfScopeInstruction = true;
			outOfScopeInstruction = instruction;
		}

		@Override public void visitPut(SSAPutInstruction instruction) {
			containsOutOfScopeInstruction = true;
			outOfScopeInstruction = instruction;
		}

		@Override public void visitPhi(SSAPhiInstruction instruction) {
			Iterator<ISSABasicBlock> orderedPredsIter = block.getCFG().getWalaCFG().getPredNodes(block.getWalaBasicBLock());

			int i = 0;
			while(orderedPredsIter.hasNext()) {
				int blockNum = orderedPredsIter.next().getNumber();
				if (blockNum == prevBlockIdx) {
					break;
				}
				i++;
			}
			Integer op = null;
			try {
				op = (Integer) m.getValueOrConstant(instruction.getUse(i), Type.INTEGER).getVal();
				if (!m.hasValue(instruction.getDef())) {
					Value defVal = Value.createByType(instruction.getDef(), m.getValue(instruction.getUse(i)).getType());
					m.addValue(instruction.getDef(), defVal);
				}
				m.setValue(instruction.getDef(), op);
			} catch (MissingValueException e) {
				e.printStackTrace();
			}
		}

		@Override public void visitPi(SSAPiInstruction instruction) {
			containsOutOfScopeInstruction = true;
			outOfScopeInstruction = instruction;
		}

		@Override public void visitGetCaughtException(SSAGetCaughtExceptionInstruction instruction) {
			containsOutOfScopeInstruction = true;
			outOfScopeInstruction = instruction;
		}

		@Override public void visitLoadMetadata(SSALoadMetadataInstruction instruction) {
			containsOutOfScopeInstruction = true;
			outOfScopeInstruction = instruction;
		}

		@Override
		public void visitInvoke(SSAInvokeInstruction instruction) {

			if (instruction.getCallSite().getDeclaredTarget().getSignature().equals(OUTPUT_FUNCTION)) {
				try {
					out.println(m.getValueOrConstant(instruction.getUse(0), Type.INTEGER).getVal());
				} catch (MissingValueException e) {
					e.printStackTrace();
				}
			}
		}

		@Override public void visitNew(SSANewInstruction instruction) {
			containsOutOfScopeInstruction = true;
			outOfScopeInstruction = instruction;
		}

		@Override public void visitArrayLength(SSAArrayLengthInstruction instruction) {
			containsOutOfScopeInstruction = true;
			outOfScopeInstruction = instruction;
		}

		@Override public void visitThrow(SSAThrowInstruction instruction) {
			containsOutOfScopeInstruction = true;
			outOfScopeInstruction = instruction;
		}

		@Override public void visitMonitor(SSAMonitorInstruction instruction) {
			containsOutOfScopeInstruction = true;
			outOfScopeInstruction = instruction;
		}

		@Override public void visitCheckCast(SSACheckCastInstruction instruction) {
			containsOutOfScopeInstruction = true;
			outOfScopeInstruction = instruction;
		}

		@Override public void visitInstanceof(SSAInstanceofInstruction instruction) {
			containsOutOfScopeInstruction = true;
			outOfScopeInstruction = instruction;
		}
	}
}
