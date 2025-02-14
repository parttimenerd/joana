package edu.kit.joana.ifc.sdg.qifc.qif_interpreter.ir;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.dominators.Dominators;
import edu.kit.joana.ifc.sdg.qifc.qif_interpreter.ui.DotGraph;
import edu.kit.joana.ifc.sdg.qifc.qif_interpreter.ui.DotNode;

import java.util.*;
import java.util.stream.Collectors;

/**
 * wrapper class for Wala CFG w/ some utility functions
 */
public class CFG implements Graph<BBlock>, DotGraph {

	private final Method m;
	private final SSACFG walaCFG;
	private final BiMap<SSACFG.BasicBlock, BBlock> repMap = HashBiMap.create();
	private final List<BBlock> blocks;
	private final BBlock entry;
	private Dominators<BBlock> walaDoms;
	private edu.kit.joana.ifc.sdg.qifc.nildumu.Dominators<BBlock> nildumuDoms;

	private CFG(Method m) {
		this.m = m;
		this.entry = new BBlock(m.getIr().getControlFlowGraph().entry(), this);
		this.blocks = new ArrayList<>();
		this.blocks.add(entry);
		this.walaCFG = m.getIr().getControlFlowGraph();
	}

	public static CFG buildCFG(Method m) {
		CFG cfg = new CFG(m);
		cfg.entry.findSuccessorsRec(cfg);
		cfg.blocks.forEach(b -> b.findPredecessors(cfg));

		// sorting these makes testing and debugging easier. However it should not be assumed that this list is sorted in the interpreter itself
		cfg.blocks.sort(Comparator.comparingInt(BBlock::idx));

		// loop and conditionals info
		cfg.nildumuDoms = new edu.kit.joana.ifc.sdg.qifc.nildumu.Dominators<>(cfg.entry, (BBlock::succs));
		for (BBlock bb : cfg.blocks) {
			if (cfg.nildumuDoms.isPartOfLoop(bb)) {
				bb.setPartOfLoop(true);
				if (bb.equals(cfg.nildumuDoms.loopHeader(bb))) {
					bb.setLoopHeader(true);
				}
			}
			if (!bb.isLoopHeader()
					&& bb.succs().stream().filter(s -> !s.getWalaBasicBlock().isExitBlock()).count() > 1) {
				bb.setCondHeader(true);
			}
		}

		cfg.addDummyBlocks();
		cfg.walaDoms = Dominators.make(cfg, cfg.entry);
		return cfg;
	}

	public int getLevel(BBlock b) {
		if (b.isDummy()) {
			return getLevel(b.succs().get(0));
		}
		return nildumuDoms.loopDepth(b);
	}

	private void addDummyBlocks() {
		List<BBlock> decisionNodes = this.blocks.stream().filter(BBlock::splitsControlFlow)
				.collect(Collectors.toList());
		for (BBlock b : decisionNodes) {
			List<BBlock> newSuccs = new ArrayList<>();
			for (BBlock succ : b.succs()) {
				BBlock newDummy = BBlock.createDummy(this, b.idx());
				if (succ.isPartOfLoop()) {
					newDummy.setPartOfLoop(true);
				}
				this.addNode(newDummy);
				this.replaceEdge(newDummy, succ, b);
				newSuccs.add(newDummy);
			}
			this.removeOutgoingEdges(b);
			newSuccs.forEach(d -> this.addEdge(b, d));
		}
	}

	public Set<BBlock> getBasicBlocksInLoop(BBlock header) {
		assert (header.isLoopHeader());
		Set<BBlock> inLoop = new HashSet<>();
		inLoop.add(header);

		// find predecessor w/ back-edge
		Optional<BBlock> loopJmpBack = header.preds().stream().filter(pred -> isDominatedBy(pred, header)).findFirst();
		assert (loopJmpBack.isPresent());

		// add all predecessors until header is reached
		inLoop.add(loopJmpBack.get());
		addLoopNodeRec(inLoop, loopJmpBack.get());

		return inLoop;
	}

	private void addLoopNodeRec(Set<BBlock> alreadyFound, BBlock current) {
		for (BBlock b : current.preds()) {
			if (!alreadyFound.contains(b)) {
				alreadyFound.add(b);
				addLoopNodeRec(alreadyFound, b);
			}
		}
	}

	public void print() {
		for (BBlock b : blocks) {
			b.print();
			StringBuilder succs = new StringBuilder("Successors: ");
			for (BBlock s : b.succs()) {
				succs.append(s.idx()).append(" ");
			}
			succs.append("\nPredecessors: ");
			for (BBlock s : b.preds()) {
				succs.append(s.idx()).append(" ");
			}
			System.out.println(succs.toString());
		}
	}

	public List<BBlock> getBlocks() {
		return blocks;
	}

	public Method getMethod() {
		return m;
	}

	public BBlock entry() {
		return BBlock.bBlock(m, m.getCFG().walaCFG.entry());
	}

	public SSACFG getWalaCFG() {
		return walaCFG;
	}

	public boolean isDominatedBy(BBlock node, BBlock potentialDom) {
		return walaDoms.isDominatedBy(node, potentialDom);
	}

	public BBlock getImmDom(BBlock node) {
		return walaDoms.getIdom(node);
	}

	@Override public void removeNodeAndEdges(BBlock n) throws UnsupportedOperationException {
		this.blocks.remove(n);
		for(BBlock b: blocks) {
			b.preds().remove(n);
			b.succs().remove(n);
		}
	}

	@Override public Iterator<BBlock> getPredNodes(BBlock n) {
		return n.preds().iterator();
	}

	@Override public int getPredNodeCount(BBlock n) {
		return n.preds().size();
	}

	@Override public Iterator<BBlock> getSuccNodes(BBlock n) {
		return n.succs().iterator();
	}

	@Override public int getSuccNodeCount(BBlock N) {
		return N.succs().size();
	}

	@Override public void addEdge(BBlock src, BBlock dst) {
		src.succs().add(dst);
		dst.preds().add(src);
	}

	@Override public void removeEdge(BBlock src, BBlock dst) throws UnsupportedOperationException {
		src.succs().remove(dst);
		dst.preds().remove(src);
	}

	public void replaceEdge(BBlock src, BBlock dst, BBlock oldSrc) {
		int pos = dst.preds().indexOf(oldSrc);
		dst.preds().remove(pos);
		dst.preds().add(pos, src);
		src.succs().add(dst);
	}

	@Override public void removeAllIncidentEdges(BBlock node) throws UnsupportedOperationException {
		removeIncomingEdges(node);
		removeOutgoingEdges(node);
	}

	@Override public void removeIncomingEdges(BBlock node) throws UnsupportedOperationException {
		node.emptyPreds();
		for (BBlock b : blocks) {
			b.succs().remove(node);
		}
	}

	@Override public void removeOutgoingEdges(BBlock node) throws UnsupportedOperationException {
		node.emptySuccs();
		for (BBlock b: blocks) {
			b.preds().remove(node);
		}
	}

	@Override public boolean hasEdge(BBlock src, BBlock dst) {
		assert(src.succs().contains(dst) == dst.preds().contains(src));
		return src.succs().contains(dst);
	}

	@Override public Iterator<BBlock> iterator() {
		return blocks.iterator();
	}

	@Override public int getNumberOfNodes() {
		return blocks.size();
	}

	@Override public void addNode(BBlock n) {
		blocks.add(n);
	}

	@Override public void removeNode(BBlock n) throws UnsupportedOperationException {
		removeAllIncidentEdges(n);
		blocks.remove(n);
	}

	@Override public boolean containsNode(BBlock n) {
		return blocks.contains(n);
	}

	public BBlock getBlock(int i) {
		return blocks.stream().filter(b -> b.idx() == i).findFirst().get();
	}

	public void addRep(SSACFG.BasicBlock a, BBlock b) {
		this.repMap.put(a, b);
	}

	public BiMap<SSACFG.BasicBlock, BBlock> repMap() {
		return this.repMap;
	}

	@Override public BBlock getRoot() {
		return this.entry;
	}

	@Override public List<DotNode> getNodes() {
		return this.blocks.stream().map(b -> (DotNode)b).collect(Collectors.toList());
	}

	@Override public String getName() {
		return m.identifier()
				.replace('.', '_')
				.replace('(', '_')
				.replace(')', '_');
	}
}
