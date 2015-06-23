package ca.ubc.cs.beta.smac.executors;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import ca.ubc.cs.beta.aeatk.misc.watch.StopWatch;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParameterConfiguration;
import ca.ubc.cs.beta.aeatk.smac.SMACOptions;
import ca.ubc.cs.beta.smac.builder.SMACBuilder;
import ca.ubc.cs.beta.smac.configurator.AbstractAlgorithmFramework;
import ca.ubc.cs.beta.smac.lib.IObjectiveFunction;
import ca.ubc.cs.beta.smac.lib.aeatk.SyncJavaTargetAlgorithmEvaluatorFactory;
import ca.ubc.cs.beta.smac.lib.aeatk.SyncJavaTargetAlgorithmEvaluatorOptions;

import com.beust.jcommander.ParameterException;

/**
 * This class is provides an easier access to SMACs functionalities if the
 * function to be optimized is written in Java.
 * 
 * @author Simon Bartels
 *
 */
public class SMACJavaLib extends SMACExecutor {
	protected SMACJavaLib() {
		// this class is not meant for instantiation
	}

	/**
	 * Optimizes the given objective function and returns the best configuration
	 * found.
	 * 
	 * @param objectiveFunction
	 *            The objective function.
	 * @param maxIterations
	 *            The maximum number of SMAC iterations.
	 * @param variableNames
	 *            The name of all variables. MAY NOT contain the categorical
	 *            variable's name.
	 * @param continuousVariablesInitial
	 *            The initial values for all continuous variables. MAY be null.
	 * @param continuousVariablesMin
	 *            The minimal values for all continuous variables.
	 * @param continuousVariablesMax
	 *            The maximal values for all continuous variables. MAY be null.
	 * @param integerVariablesInitial
	 *            The initial values for all integer variables.
	 * @param integerVariablesMin
	 *            The minimal values for all integer variables.
	 * @param integerVariablesMax
	 *            The maximal values for all integer variables.
	 * @param categoricalVariables
	 *            The categorical variables and their values. MAY be null.
	 * @return The best configuration found.
	 */
//	public static ParameterConfiguration optimize(
//			IObjectiveFunction objectiveFunction, int maxIterations, List<String> variableNames,
//			double[] continuousVariablesInitial,
//			double[] continuousVariablesMin, double[] continuousVariablesMax,
//			int[] integerVariablesInitial, int[] integerVariablesMin,
//			int[] integerVariablesMax,
//			Map<String, List<String>> categoricalVariables) {
//		return optimize(
//				objectiveFunction,
//				createParameterSpace(variableNames, continuousVariablesInitial,
//						continuousVariablesMin, continuousVariablesMax,
//						integerVariablesInitial, integerVariablesMin,
//						integerVariablesMax, categoricalVariables),
//				RunObjective.QUALITY, maxIterations);
//	}

	public static ParameterConfiguration optimize(
			IObjectiveFunction objectiveFunction, File parameterConfiguration) throws ParameterException, IOException {
		String[] arguments = new String[]{
				"--algo", "java_intern",
				"--run-obj", "QUALITY", 
				"--use-instances", "false",
				"--numberOfRunsLimit", "3",
				"--tae", SyncJavaTargetAlgorithmEvaluatorFactory.NAME,
				"--pcs-file", parameterConfiguration.getAbsolutePath()
				};
		return optimize(objectiveFunction, arguments).getIncumbent();		
	}
	
	/**
	 * Overwrites the algoExecOptions and the instanceOptions.
	 * 
	 * @param objectiveFunction
	 * @param smacOpts
	 * @return The terminated SMAC.
	 * @throws IOException 
	 * @throws ParameterException 
	 */
	public static AbstractAlgorithmFramework optimize(
			IObjectiveFunction objectiveFunction, String[] arguments) throws ParameterException, IOException {
		SMACOptions options = new SMACOptions();
//		options.scenarioConfig = new ScenarioOptions();
//		options.scenarioConfig.scenarioFile = File.createTempFile("tempScenarioFile", "");
		
		options = parseCLIOptions(arguments);
		SyncJavaTargetAlgorithmEvaluatorOptions opts = new SyncJavaTargetAlgorithmEvaluatorOptions();
		opts.objectiveFunction = objectiveFunction;
		taeOptions = new HashMap<String, AbstractOptions>();
		taeOptions.put(SyncJavaTargetAlgorithmEvaluatorFactory.NAME, opts);
		SMACBuilder smacBuilder = new SMACBuilder();
		StopWatch watch = new StopWatch();
		return optimize(options, smacBuilder, watch);

	}

}
