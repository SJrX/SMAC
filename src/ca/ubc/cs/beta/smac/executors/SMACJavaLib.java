package ca.ubc.cs.beta.smac.executors;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
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
	 * @param parameterConfiguration
	 *            A parameter configuration file.
	 * @param maxIterations
	 *            The maximum number of SMAC iterations.
	 * @return The best configuration found.
	 */
	public static ParameterConfiguration optimize(
			IObjectiveFunction objectiveFunction, File parameterConfiguration,
			int maxIterations) throws ParameterException, IOException {
		String[] arguments = new String[] { "--algo", "java_intern",
				"--run-obj", "QUALITY", "--use-instances", "false",
				"--numberOfRunsLimit", new Integer(maxIterations).toString(),
				"--tae", SyncJavaTargetAlgorithmEvaluatorFactory.NAME,
				"--pcs-file", parameterConfiguration.getAbsolutePath() };
		return optimize(objectiveFunction, arguments).getIncumbent();
	}

	/**
	 * Optimizes the given objective function and returns the best configuration
	 * found. Creates a temporary parameter configuration space file and calls
	 * {@link #optimize(IObjectiveFunction, File, int)}.
	 * 
	 * @param objectiveFunction
	 *            the objective function
	 * @param maxIterations
	 *            the maximal number of function evaluations
	 * @param variableNames
	 *            the names of the variables
	 * @param initialValues
	 *            the initial values for each variable
	 * @param minValues
	 *            the lower boundaries for each variable
	 * @param maxValues
	 *            the upper boundaries for each variable
	 * @return the best configuration found
	 * @throws ParameterException
	 * @throws IOException
	 */
	public static ParameterConfiguration optimize(
			IObjectiveFunction objectiveFunction, int maxIterations,
			String[] variableNames, double[] initialValues, double[] minValues,
			double[] maxValues) throws ParameterException, IOException {
		File parameterConfiguration = File.createTempFile("smac", ".pcs");
		PrintWriter w = new PrintWriter(parameterConfiguration);
		for (int i = 0; i < variableNames.length; i++)
			w.write(variableNames[i] + " [" + minValues[i] + ", "
					+ maxValues[i] + "] [" + initialValues[i] + "]\n");
		w.close();
		return optimize(objectiveFunction, parameterConfiguration,
				maxIterations);
	}

	/**
	 * Returns the options you have to use to execute SMAC in JAVA library mode.
	 * 
	 * @return the target algorithm evaluator options
	 */
	public static String[] getTAEoptions() {
		return new String[] { "--algo", "java_intern", "--tae",
				SyncJavaTargetAlgorithmEvaluatorFactory.NAME };
	}

	/**
	 * The most elaborate way to use SMAC which allows you to exploit all
	 * possibilities of SMAC. Just make sure to add {@link #getTAEoptions()} to
	 * your arguments string. Overwrites the algoExecOptions and the
	 * instanceOptions.
	 * 
	 * @param objectiveFunction
	 *            the objective function
	 * @param arguments
	 *            command line string of arguments (including the --)
	 * @return The terminated SMAC.
	 * @throws IOException
	 * @throws ParameterException
	 */
	public static AbstractAlgorithmFramework optimize(
			IObjectiveFunction objectiveFunction, String[] arguments)
			throws ParameterException, IOException {
		SMACOptions options = new SMACOptions();
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
