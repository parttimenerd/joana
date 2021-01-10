package edu.kit.joana.ifc.sdg.qifc.qif_interpreter;

import com.beust.jcommander.*;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.graph.GraphIntegrity;
import edu.kit.joana.ifc.sdg.qifc.qif_interpreter.ir.SDGBuilder;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class App {

	// magic strings are evil
	private static final String JAVA_FILE_EXT = ".java";
	private static final String CLASS_FILE_EXT = ".class";
	private static final String DNNF_FILE_EXT = ".dnnf";

	public static void main(String[] args) {
		SimpleLogger.init(Level.ALL);
		Args jArgs = new Args();
		JCommander jc = JCommander.newBuilder().addObject(jArgs).build();
		jc.setProgramName("QIF Interpreter");

		try {
			jc.parse(args);
			jArgs.validate();
		} catch (ParameterException e) {
			e.printStackTrace();
			jc.usage();
			System.exit(1);
		}

		if (jArgs.help) {
			jc.usage();
		}

		// check if we got a .java file as input. If yes, we need to compile it to a .class file first
		String classFilePath;
		String programPath = jArgs.inputFiles.get(0);

		SimpleLogger.log("Starting compilation with javac");
		if (programPath.endsWith(JAVA_FILE_EXT)) {
			try {
				String cmd = String.format("javac -target 1.8 -source 1.8 -d %s %s", jArgs.outputDirectory, programPath);
				Process compilation = Runtime.getRuntime().exec(cmd);
				int exitCode = compilation.waitFor();
				// if (exitCode != 0) { throw new IOException("Error: Couldn't compile input program"); }
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
				System.exit(1);
			}
			classFilePath = jArgs.outputDirectory + "/" + FilenameUtils.getBaseName(programPath) + CLASS_FILE_EXT;
		} else {
			classFilePath = programPath;
		}
		SimpleLogger.log(String.format("Finished compilation. Generated file: %s", classFilePath));

		// get classname via filename
		String className = FilenameUtils.getBaseName(programPath);
		SimpleLogger.log("Classname: " + className);

		// create SDG
		SDGBuilder builder = new SDGBuilder(classFilePath, className);
		builder.createBaseSDGConfig();
		try {
			builder.build();
		} catch (IOException | CancelException | ClassHierarchyException | GraphIntegrity.UnsoundGraphException e) {
			e.printStackTrace();
		}
		builder.dumpGraph(jArgs.outputDirectory);


		if (jArgs.doStatic) {

		} else {
			// load data from provided dnnf file
		}

		if (!jArgs.onlyStatic) {
			// run the program
		}
	}

	public static class Args implements IParameterValidator, IStringConverter<String> {

		private static final String OUTPUT_DIR_NAME = "out_";
		@Parameter(names = "-o", description = "Specify a path where the output directory should be created (Default is the current working directory)") String outputDirectory = ".";
		@Parameter(names = "--usage", description = "Print help") private boolean help = false;
		@Parameter(names = "--static", description = "Perform only static analysis on the input program") private boolean onlyStatic = false;
		@Parameter(names = "--dump-graphs", description = "Dump graphs created by JOANA") private boolean dumpGraphs = false;
		@Parameter(description = "A program for the interpreter to execute, plus optionally the result of a previous static analysis", validateWith = Args.class, converter = Args.class) private List<String> inputFiles = new ArrayList<>();

		@Parameter(names = "-args", description = "Arguments for running the input program", variableArity = true) private List<String> args = new ArrayList<>();

		@Parameter(names = "-workingDir", description = "Directory from which the interpreter was started. Should be set automatically by run.sh", required = true) private String workingDir = System.getProperty("user.dir");

		/**
		 * sometimes we don't need to do a static analysis, bc it is already provided via input
		 */
		private boolean doStatic = true;

		/**
		 * Validates the given arguments. Expected are:
		 * - option {@code} static: A .java file containing the program to be analysed
		 * - otherwise: A .class file of the program to be analysed,
		 * optionally a .dnnf file (if this is provided the static analysis will be skipped)
		 * and the input parameters for the program execution
		 *
		 * @throws ParameterException if some paramter contraint is violated
		 */
		public void validate() throws ParameterException {

			if (help) {
				return;
			}

			// check if we have a valid path to create our output directory
			// TODO: clean up this mess
			File out = new File(outputDirectory);

			if (!out.exists() | !out.isDirectory()) {
				throw new ParameterException("Error: Couldn't find output directory.");
			} else {
				outputDirectory = OUTPUT_DIR_NAME + System.currentTimeMillis();
				out = new File(outputDirectory);
				final boolean mkdir = out.mkdir();
				if (!mkdir) {
					try {
						throw new FileSystemException("Error: Couldn't create output directory");
					} catch (FileSystemException e) {
						e.printStackTrace();
					}
				}
			}
			SimpleLogger.log(String.format("Using output directory: %s", outputDirectory));
			// we always need an input program
			if (inputFiles.size() == 0) {
				throw new ParameterException("Error: No input file found");
			}

			// if 2 input files are provided one of them is from a previous static analysis, hence we don't need to do it again
			if (inputFiles.size() == 2) {
				doStatic = false;
			} else if (inputFiles.size() != 1) {
				throw new ParameterException("Error: unexpected number of arguments");
			}
		}

		@Override public void validate(String name, String value) throws ParameterException {
			value = (value.startsWith("/")) ? value : workingDir + "/" + value;
			File f = new File(value);
			if (f.isDirectory() || !f.exists() || !f.canRead() || !hasValidExtension(value)) {
				throw new ParameterException(String.format("Input File couldn't be found: %s -- Path not valid.", value));
			}
		}

		private boolean hasValidExtension(String path) {
			return (path.endsWith(JAVA_FILE_EXT) || path.endsWith(CLASS_FILE_EXT) || path.endsWith(DNNF_FILE_EXT));
		}

		// convert all paths to absolute paths
		@Override public String convert(String path) {
			return (path.startsWith("/")) ? path : workingDir + "/" + path;
		}
	}
}
