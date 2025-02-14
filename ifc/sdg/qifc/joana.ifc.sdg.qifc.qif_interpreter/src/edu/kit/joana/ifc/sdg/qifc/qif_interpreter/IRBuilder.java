package edu.kit.joana.ifc.sdg.qifc.qif_interpreter;

import com.ibm.wala.cfg.exc.intra.MethodState;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.NullProgressMonitor;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.GraphIntegrity;
import edu.kit.joana.api.IFCAnalysis;
import edu.kit.joana.api.sdg.SDGBuildPreparation;
import edu.kit.joana.api.sdg.SDGConfig;
import edu.kit.joana.api.sdg.SDGProgram;
import edu.kit.joana.ifc.sdg.graph.SDG;
import edu.kit.joana.ifc.sdg.graph.SDGSerializer;
import edu.kit.joana.ifc.sdg.graph.slicer.graph.threads.MHPAnalysis;
import edu.kit.joana.ifc.sdg.mhpoptimization.MHPType;
import edu.kit.joana.ifc.sdg.qifc.qif_interpreter.ir.Program;
import edu.kit.joana.ifc.sdg.util.JavaMethodSignature;
import edu.kit.joana.util.Stubs;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class IRBuilder {

	// some default settings
	private static final Stubs DEFAULT_STUBS = Stubs.JRE_17;
	private static final String SDG_FILE_SUFFIX = ".pdg";

	private final File classFile;
	private final String className;
	private SDGProgram sdgProg;
	private SDG sdg;
	private edu.kit.joana.wala.core.SDGBuilder builder;
	private IClassHierarchy ch;
	private CallGraph cg;
	private SDGConfig config;
	private IFCAnalysis ana;


	public IRBuilder(String classFilePath, String className) {
		this.classFile = new File(classFilePath);
		this.className = className;
	}

	public void createBaseSDGConfig() {
		String mainMethod = JavaMethodSignature.mainMethodOfClass(className).toBCString();
		this.config = new SDGConfig(classFile.getAbsolutePath(), mainMethod, DEFAULT_STUBS);

		// some default settings
		config.setComputeInterferences(false);
		config.setExceptionAnalysis(edu.kit.joana.wala.core.SDGBuilder.ExceptionAnalysis.IGNORE_ALL); // no exceptions for now
		config.setFieldPropagation(edu.kit.joana.wala.core.SDGBuilder.FieldPropagation.OBJ_GRAPH);
		config.setPointsToPrecision(edu.kit.joana.wala.core.SDGBuilder.PointsToPrecision.OBJECT_SENSITIVE);
		config.setDefaultExceptionMethodState(MethodState.DEFAULT);
		config.setMhpType(MHPType.NONE); // no parallelism for now
	}

	public void build()
			throws IOException, CancelException, ClassHierarchyException, GraphIntegrity.UnsoundGraphException {
		if (config == null) throw new IllegalStateException("Error: Missing Configuration");

		this.sdgProg = SDGProgram.createSDGProgram(config);
	}

	public void buildAndKeepBuilder()
			throws IOException, CancelException, ClassHierarchyException, GraphIntegrity.UnsoundGraphException {

		// PrintStream for Joana output
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final String utf8 = StandardCharsets.UTF_8.name();
		PrintStream ps = new PrintStream(baos, true, utf8);

		Pair<SDG, edu.kit.joana.wala.core.SDGBuilder> p = SDGBuildPreparation
				.computeAndKeepBuilder(ps, SDGProgram.makeBuildPreparationConfig(config), new NullProgressMonitor());
		this.sdg = p.fst;
		this.builder = p.snd;

		MHPAnalysis mhp = config.getMhpType().getMhpAnalysisConstructor().apply(sdg);
		this.sdgProg = new SDGProgram(sdg, mhp);
		this.ch = builder.getClassHierarchy();
		this.cg = builder.getWalaCallGraph();
		sdgProg.fillWithAnnotations(ch, SDGProgram.findClassesRelevantForAnnotation(ch, cg));

		this.ana = new IFCAnalysis(sdgProg);
		this.ana.addAllJavaSourceAnnotations();

		// write Joana output to logFIle
		SimpleLogger.log("Joana output:\n" + baos.toString(utf8) + "\n");
	}

	public void dumpGraph(String path) {
		SDG sdg = sdgProg.getSDG();
		String fileName = path + "/" + sdg.getName() + SDG_FILE_SUFFIX;
		try {
			SDGSerializer.toPDGFormat(sdg, new FileOutputStream(fileName));
			SimpleLogger.log("Dumping graph to " + fileName);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public Program getProgram() {
		return new Program(sdgProg, this.sdg, this.className, builder, cg, this.ana);
	}
}
