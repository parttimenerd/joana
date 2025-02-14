package edu.kit.joana.ifc.sdg.qifc.qif_interpreter.stat;

import edu.kit.joana.ifc.sdg.qifc.qif_interpreter.ir.*;
import edu.kit.joana.ifc.sdg.qifc.qif_interpreter.oopsies.OutOfScopeException;
import edu.kit.joana.ifc.sdg.qifc.qif_interpreter.oopsies.UnexpectedTypeException;
import edu.kit.joana.ifc.sdg.qifc.qif_interpreter.util.LogicUtil;
import org.logicng.formulas.Variable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

public class StaticAnalysis {

	private final Program program;
	private final Method entry;

	public StaticAnalysis(Program program) {
		this.program = program;
		this.entry = program.getEntryMethod();
	}

	public void computeSATDeps() {
		computeSATDeps(this.entry);
	}

	public void initParameterDeps(Method m) {
		// create literals for method parameters
		int[] params = m.getIr().getParameterValueNumbers();
		for (int i = 1; i < params.length; i++) {
			if (m.getParamType(i).equals(Type.ARRAY)) continue; // already initialized
			m.setDepsForvalue(params[i], LogicUtil.createVars(params[i], m.getParamType(i).bitwidth()));
			m.addVarsToValue(params[i], (Variable[]) m.getDepsForValue(params[i]));
		}
	}

	public void initConstants(Method m) {
		// initialize formula arrays for all constant values
		m.getProgramValues().values().stream().filter(Value::isConstant).forEach(c -> {
			try {
				c.setDeps(LogicUtil.asFormulaArray(LogicUtil.binaryRep(c.getVal(), c.getType())));
			} catch (UnexpectedTypeException e) {
				e.printStackTrace();
			}
		});
	}

	public void computeSATDeps(Method m) {
		computeSATDeps(m, new SATVisitor(this));
	}

	public void computeSATDeps(Method m, SATVisitor sv) {
		initParameterDeps(m);
		initConstants(m);

		// implicit IF
		ImplicitIFVisitor imIFVisitor = new ImplicitIFVisitor();
		imIFVisitor.compute(m.getCFG());

		List<Integer> visited = new ArrayList<>();
		Queue<BBlock> toVisit = new ArrayDeque<>();
		toVisit.add(m.getCFG().getBlock(0));

		while (!toVisit.isEmpty()) {
			BBlock b = toVisit.poll();
			visited.add(b.idx());

			if (b.isLoopHeader()) {
				try {
					sv.visitBlock(m, b, -1);
				} catch (OutOfScopeException e) {
					e.printStackTrace();
				}
				LoopBody l = LoopHandler.analyze(m, b, this);
				m.addLoop(l);

				// add all after-loop successors, but skip the dummy blocks
				for (BBlock succ : b.succs().stream().filter(succ -> !l.getBlocks().contains(succ))
						.collect(Collectors.toList())) {
					if (succ.isDummy()) {
						succ = succ.succs().get(0);
					}
					toVisit.add(succ);
				}
			} else {
				try {
					sv.visitBlock(m, b, -1);

					for (BBlock succ: b.succs()) {
						if (succ.isLoopHeader() || succ.preds().stream().allMatch(pred -> visited.contains(pred.idx()))) {
							toVisit.add(succ);
						}
					}

				} catch (OutOfScopeException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public void createConstant(int op1) {
		Int constant = (Int) entry.getValue(op1);
		assert constant != null;
		constant.setDeps(
				LogicUtil.asFormulaArray(LogicUtil.twosComplement((Integer) constant.getVal(), constant.getWidth())));
	}
}
