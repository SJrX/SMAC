package ca.ubc.cs.beta.smac.executors;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.ParameterException;

import ca.ubc.cs.beta.aeatk.algorithmexecutionconfiguration.AlgorithmExecutionConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmexecutionconfiguration.java.JavaAlgorithmExecutionOptions;
import ca.ubc.cs.beta.aeatk.misc.associatedvalue.Pair;
import ca.ubc.cs.beta.aeatk.misc.watch.AutoStartStopWatch;
import ca.ubc.cs.beta.aeatk.misc.watch.StopWatch;
import ca.ubc.cs.beta.aeatk.objectives.RunObjective;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.NormalizedRange;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParameterConfiguration;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParameterConfigurationSpace;
import ca.ubc.cs.beta.aeatk.probleminstance.FileFreeProblemInstanceOptions;
import ca.ubc.cs.beta.aeatk.probleminstance.InstanceListWithSeeds;
import ca.ubc.cs.beta.aeatk.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aeatk.probleminstance.ProblemInstanceOptions.TrainTestInstances;
import ca.ubc.cs.beta.aeatk.probleminstance.ProblemInstanceSeedPair;
import ca.ubc.cs.beta.aeatk.probleminstance.seedgenerator.InstanceSeedGenerator;
import ca.ubc.cs.beta.aeatk.probleminstance.seedgenerator.RandomInstanceSeedGenerator;
import ca.ubc.cs.beta.aeatk.probleminstance.seedgenerator.SetInstanceSeedGenerator;
import ca.ubc.cs.beta.aeatk.random.SeedableRandomPool;
import ca.ubc.cs.beta.aeatk.runhistory.RunHistory;
import ca.ubc.cs.beta.aeatk.smac.SMACOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.base.analytic.AnalyticFunctions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.base.java.IObjectiveFunction;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.base.java.sync.SyncJavaTargetAlgorithmEvaluatorFactory;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.base.java.sync.SyncJavaTargetAlgorithmEvaluatorOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.init.TargetAlgorithmEvaluatorBuilder;
import ca.ubc.cs.beta.aeatk.termination.TerminationCondition;
import ca.ubc.cs.beta.aeatk.trajectoryfile.TrajectoryFile;
import ca.ubc.cs.beta.aeatk.trajectoryfile.TrajectoryFileEntry;
import ca.ubc.cs.beta.smac.builder.SMACBuilder;
import ca.ubc.cs.beta.smac.configurator.AbstractAlgorithmFramework;
import ca.ubc.cs.beta.smac.validation.ValidationResult;
import ca.ubc.cs.beta.smac.validation.Validator;

/**
 * This class is provides an easier access to SMACs functionalities if the
 * function to be optimized is written in Java.
 * 
 * @author Simon Bartels
 *
 */
public class SMACJavaLib {
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
	public static ParameterConfiguration optimize(
			IObjectiveFunction objectiveFunction, int maxIterations, List<String> variableNames,
			double[] continuousVariablesInitial,
			double[] continuousVariablesMin, double[] continuousVariablesMax,
			int[] integerVariablesInitial, int[] integerVariablesMin,
			int[] integerVariablesMax,
			Map<String, List<String>> categoricalVariables) {
		return optimize(
				objectiveFunction,
				createParameterSpace(variableNames, continuousVariablesInitial,
						continuousVariablesMin, continuousVariablesMax,
						integerVariablesInitial, integerVariablesMin,
						integerVariablesMax, categoricalVariables),
				RunObjective.QUALITY, maxIterations);
	}

	/**
	 * Creates a parameter space from the given information.
	 * 
	 * @param variableNames
	 *            The names of all variables in order: continuous, integer, then
	 *            categorical. MAY NOT contain the names of the categorical
	 *            variables.
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
	 * @return A basic {@link ParameterConfigurationSpace}.
	 */
	public static ParameterConfigurationSpace createParameterSpace(
			List<String> variableNames, double[] continuousVariablesInitial,
			double[] continuousVariablesMin, double[] continuousVariablesMax,
			int[] integerVariablesInitial, int[] integerVariablesMin,
			int[] integerVariablesMax,
			Map<String, List<String>> categoricalVariables) {
		/*
		 * There's no need to do any consistency checks here. It's all done in
		 * ParameterConfigurationSpace. We could check if each array has the
		 * right length but the user probably realizes anyway quickly what's the
		 * problem.
		 */
		if (continuousVariablesInitial == null)
			continuousVariablesInitial = new double[0];
		if (integerVariablesInitial == null)
			integerVariablesInitial = new int[0];
		if (categoricalVariables == null)
			categoricalVariables = new HashMap<String, List<String>>();
		if (variableNames == null)
			variableNames = new ArrayList<String>();
		if (variableNames.size() < continuousVariablesInitial.length
				+ integerVariablesInitial.length)
			throw new IllegalArgumentException(
					"The list of variable names is shorter than the number of variables!");
		return createParameterSpace_(variableNames, continuousVariablesInitial,
				continuousVariablesMin, continuousVariablesMax,
				integerVariablesInitial, integerVariablesMin,
				integerVariablesMax, categoricalVariables);
	}

	/**
	 * Provides the functionality of the public function. Performs no checks.
	 */
	protected static ParameterConfigurationSpace createParameterSpace_(
			List<String> variableNames, double[] continuousVariablesInitial,
			double[] continuousVariablesMin, double[] continuousVariablesMax,
			int[] integerVariablesInitial, int[] integerVariablesMin,
			int[] integerVariablesMax,
			Map<String, List<String>> categoricalVariables) {
		int name_idx = 0;
		Map<String, String> defaultValues = new HashMap<String, String>();
		Map<String, NormalizedRange> continuousVariables = new HashMap<String, NormalizedRange>();
		for (int i = 0; i < continuousVariablesInitial.length; i++) {
			String name = variableNames.get(name_idx);
			name_idx++;
			NormalizedRange v = new NormalizedRange(continuousVariablesMin[i],
					continuousVariablesMax[i], false, false);
			continuousVariables.put(name, v);
			defaultValues.put(name,
					new Double(continuousVariablesInitial[i]).toString());
		}
		for (int i = 0; i < integerVariablesInitial.length; i++) {
			String name = variableNames.get(name_idx);
			name_idx++;
			NormalizedRange v = new NormalizedRange(integerVariablesMin[i],
					integerVariablesMax[i], false, true);
			continuousVariables.put(name, v);
			defaultValues.put(name,
					new Integer(integerVariablesInitial[i]).toString());
		}
		for (String v : categoricalVariables.keySet())
			defaultValues.put(v, categoricalVariables.get(v).get(0));
		return new ParameterConfigurationSpace(categoricalVariables,
				continuousVariables, null, defaultValues, null);
	}

	/**
	 * The most basic entrance point to use SMAC.
	 * 
	 * @param objectiveFunction
	 *            The objective function to optimize.
	 * @param parameterSpace
	 *            The parameter space.
	 * @param runObjective
	 *            The run objective.
	 * @param maxIterations
	 *            The maximum number of SMAC iterations.
	 * @return The best configuration found.
	 */
	public static ParameterConfiguration optimize(
			IObjectiveFunction objectiveFunction,
			ParameterConfigurationSpace parameterSpace,
			RunObjective runObjective, int maxIterations) {
		return optimize(objectiveFunction, parameterSpace, runObjective,
				maxIterations, null, null).getIncumbent();
	}

	/**
	 * Convenience function that delegates to
	 * {@link #optimize(IObjectiveFunction, SMACOptions, ParameterConfigurationSpace, List, List, SetInstanceSeedGenerator, SetInstanceSeedGenerator)}
	 * . *
	 * 
	 * @param objectiveFunction
	 *            The objective function to optimize.
	 * @param parameterSpace
	 *            The parameter space.
	 * @param runObjective
	 *            The run objective.
	 * @param maxIterations
	 *            The maximum number of SMAC iterations.
	 * @param trainingInstances
	 *            The training instances.
	 * @param testInstances
	 *            The test instances.
	 * @return The terminated SMAC.
	 * 
	 */
	public static AbstractAlgorithmFramework optimize(
			IObjectiveFunction objectiveFunction,
			ParameterConfigurationSpace parameterSpace,
			RunObjective runObjective, int maxIterations,
			List<ProblemInstance> trainingInstances,
			List<ProblemInstance> testInstances) {
		/*
		 * Fixed options that don't need to be handed over.
		 */
		SMACOptions smacOpts = new SMACOptions();
		smacOpts.scenarioConfig._runObj = runObjective;
		smacOpts.scenarioConfig.limitOptions.numIterations = maxIterations;

		/*
		 * Options that I set to make things work...
		 */
		smacOpts.stateOpts.saveContextWithState = false;
		// it might be a bug in the SMACBuilder that I have to set this
		// explicitly true
		smacOpts.randomForestOptions.logModel = false;

		return optimize(objectiveFunction, smacOpts, parameterSpace,
				trainingInstances, testInstances, null, null);
	}

	/**
	 * Convenience function that delegates to
	 * {@link #optimize(IObjectiveFunction, SMACOptions, ParameterConfigurationSpace, List, List, SetInstanceSeedGenerator, SetInstanceSeedGenerator)}
	 * where the {@link SetInstanceSeedGenerator} are initialized NULL.
	 * 
	 * @param objectiveFunction
	 * @param parameterSpace
	 * @param smacOpts
	 * @param trainingInstances
	 * @param testInstances
	 * @return The terminated SMAC.
	 */
	public static AbstractAlgorithmFramework optimize(
			IObjectiveFunction objectiveFunction,
			ParameterConfigurationSpace parameterSpace, SMACOptions smacOpts,
			List<ProblemInstance> trainingInstances,
			List<ProblemInstance> testInstances) {
		/*
		 * Options that I set to make things work...
		 */
		smacOpts.stateOpts.saveContextWithState = false;
		// it might be a bug in the SMACBuilder that I have to set this
		// explicitly
		smacOpts.randomForestOptions.logModel = false;

		return optimize(objectiveFunction, smacOpts, parameterSpace,
				trainingInstances, testInstances, null, null);
	}

	/**
	 * Overwrites the algoExecOptions and the instanceOptions.
	 * 
	 * @param objectiveFunction
	 * @param smacOpts
	 * @param parameterSpace
	 * @param trainingInstances
	 *            MAY be NULL.
	 * @param testInstances
	 *            If trainingInstances is NULL testInstances MUST be NULL, too.
	 * @param trainingInstanceSeedGenerator
	 *            MAY be NULL. Provides seeds for all training instances.
	 * @param testingInstanceSeedGenerator
	 *            MAY be NULL. Provides seeds for all testing instances.
	 * @return The terminated SMAC.
	 */
	public static AbstractAlgorithmFramework optimize(
			IObjectiveFunction objectiveFunction, SMACOptions smacOpts,
			ParameterConfigurationSpace parameterSpace,
			List<ProblemInstance> trainingInstances,
			List<ProblemInstance> testInstances,
			SetInstanceSeedGenerator trainingInstanceSeedGenerator,
			SetInstanceSeedGenerator testingInstanceSeedGenerator) {
		smacOpts.scenarioConfig.algoExecOptions = new JavaAlgorithmExecutionOptions(
				parameterSpace);
		smacOpts.scenarioConfig.algoExecOptions.taeOpts.targetAlgorithmEvaluator = SyncJavaTargetAlgorithmEvaluatorFactory.NAME;
		smacOpts.scenarioConfig.instanceOptions = new FileFreeProblemInstanceOptions(
				trainingInstances, testInstances,
				trainingInstanceSeedGenerator, testingInstanceSeedGenerator);

		String outputDir = smacOpts.scenarioConfig.outputDirectory;
		// TODO (Simon): assert output dir exists
		SMACBuilder smacBuilder = null;
		Map<String, AbstractOptions> taeOptions = createTAEOptions(objectiveFunction);
		try {
			smacBuilder = new SMACBuilder();
			Logger log = LoggerFactory.getLogger(SMACJavaLib.class);
			SeedableRandomPool pool = smacOpts.seedOptions
					.getSeedableRandomPool();
			TrainTestInstances tti = smacOpts
					.getTrainingAndTestProblemInstances(
							pool,
							new SeedableRandomPool(smacOpts.validationSeed
									+ smacOpts.seedOptions.seedOffset, pool
									.getInitialSeeds()));
			InstanceListWithSeeds trainingILWS = tti.getTrainingInstances();
			AlgorithmExecutionConfiguration execConfig = smacOpts
					.getAlgorithmExecutionConfig();
			AbstractAlgorithmFramework smac = smacBuilder
					.getAutomaticConfigurator(execConfig, trainingILWS,
							smacOpts, taeOptions, outputDir, pool);
			Long watchtime = runSmac(smacBuilder, smac, log, pool);
			// TODO: check return value
			performValidationIfNecessary(smacOpts, smacBuilder, smac,
					watchtime, log, tti.getTestInstances(), taeOptions,
					outputDir);
			return smac;
		} catch (Throwable t) {
			// TODO (Simon): check return value
			// performErrorHandling(t);
			t.printStackTrace();
		}
		return null;
	}

	private static Map<String, AbstractOptions> createTAEOptions(
			IObjectiveFunction objectiveFunction) {
		Map<String, AbstractOptions> taeOptions = new HashMap<String, AbstractOptions>();
		SyncJavaTargetAlgorithmEvaluatorOptions opts = new SyncJavaTargetAlgorithmEvaluatorOptions();
		opts.objectiveFunction = objectiveFunction;
		taeOptions.put(SyncJavaTargetAlgorithmEvaluatorFactory.NAME, opts);
		return taeOptions;
	}

	protected static Long runSmac(SMACBuilder smacBuilder,
			AbstractAlgorithmFramework smac, Logger log, SeedableRandomPool pool) {
		// TODO: this is just a partial copy of oldMain(). REFACTOR?
		StopWatch watch = new AutoStartStopWatch();

		smac.run();

		watch.stop();
		long watchtime = watch.time();
		smacBuilder.getLogRuntimeStatistics().logLastRuntimeStatistics();

		pool.logUsage();

		ParameterConfiguration incumbent = smac.getIncumbent();
		RunHistory runHistory = smac.runHistory();
		TerminationCondition tc = smac.getTerminationCondition();

		final DecimalFormat df0 = new DecimalFormat("0");
		String callString = smac.getCallString();
		log.info(
				"\n=======================================================================================\n"
						+ "SMAC has finished. Reason: {}\n"
						+ "Total number of runs performed: {}, total configurations tried: {}.\n"
						+ "Total CPU time used: {} s, total wallclock time used: {} s.\n"
						+ "SMAC's final incumbent: config {} (internal ID: {}), with estimated {}: {}, based on {} run(s) on {} training instance(s).\n"
						+ "Sample call for this final incumbent:\n{}\n"
						// +
						// "Total number of runs performed: {}, total CPU time used: {} s, total wallclock time used: {} s, total configurations tried: {}.\n"
						+ "=======================================================================================",
				smac.getTerminationReason(), runHistory
						.getAlgorithmRunsIncludingRedundant().size(),
				runHistory.getAllParameterConfigurationsRan().size(), df0
						.format(tc.getTunerTime()), df0
						.format(tc.getWallTime()), runHistory
						.getThetaIdx(incumbent), incumbent, smac
						.getObjectiveToReport(), smac
						.getEmpericalPerformance(incumbent), runHistory
						.getAlgorithmRunsExcludingRedundant(incumbent).size(),
				runHistory.getProblemInstancesRan(incumbent).size(), callString
						.trim());

		return watchtime;
	}

	/**
	 * 
	 * @param options
	 * @param smacBuilder
	 * @param smac
	 * @param watchtime
	 * @param outputDir
	 * @param taeOptions
	 * @param instanceListWithSeeds
	 * @return
	 */
	/*
	 * TODO: This method is a partial copy of {@link
	 * SMACExecutor#oldMain(String[])}. Reusing would be better.
	 */
	protected static SortedMap<TrajectoryFileEntry, ValidationResult> performValidationIfNecessary(
			SMACOptions options, SMACBuilder smacBuilder,
			AbstractAlgorithmFramework smac, long watchtime, Logger log,
			InstanceListWithSeeds testingILWS,
			Map<String, AbstractOptions> taeOptions, String outputDir) {
		// TODO: We need a try block here since oldMain() does not takes care of
		// that here.
		String validationMessage = "";
		ParameterConfiguration incumbent = smac.getIncumbent();
		RunHistory runHistory = smac.runHistory();
		TerminationCondition tc = smac.getTerminationCondition();

		List<TrajectoryFileEntry> tfes = smacBuilder.getTrajectoryFileLogger()
				.getTrajectoryFileEntries();

		SortedMap<TrajectoryFileEntry, ValidationResult> performance;
		options.doValidation = (options.validationOptions.numberOfValidationRuns > 0) ? options.doValidation
				: false;
		if (options.doValidation) {

			log.info("Now starting offline validation.");

			// Don't use the same TargetAlgorithmEvaluator as above as it may
			// have runhashcode and other crap that is probably not applicable
			// for validation

			if (options.validationOptions.maxTimestamp == -1) {
				if (options.validationOptions.useWallClockTime) {
					if (options.scenarioConfig.limitOptions.runtimeLimit < Integer.MAX_VALUE) {
						options.validationOptions.maxTimestamp = options.scenarioConfig.limitOptions.runtimeLimit;
					} else {
						options.validationOptions.maxTimestamp = watchtime / 1000.0;
					}
				} else {
					options.validationOptions.maxTimestamp = options.scenarioConfig.limitOptions.tunerTimeout;
				}

			}

			options.scenarioConfig.algoExecOptions.taeOpts.turnOffCrashes();

			int coreHint = 1;
			if (options.validationCores != null && options.validationCores > 0) {
				log.debug("Validation will use {} cores",
						options.validationCores);
				options.scenarioConfig.algoExecOptions.taeOpts.maxConcurrentAlgoExecs = options.validationCores;
				// TODO: The JAVA TAE would not like that.
				// ((CommandLineTargetAlgorithmEvaluatorOptions) taeOptions
				// .get(CommandLineTargetAlgorithmEvaluatorFactory.NAME)).cores
				// = options.validationCores;
				coreHint = options.validationCores;
			}

			TargetAlgorithmEvaluator validatingTae = TargetAlgorithmEvaluatorBuilder
					.getTargetAlgorithmEvaluator(
							options.scenarioConfig.algoExecOptions.taeOpts,
							false, taeOptions);
			try {

				List<ProblemInstance> testInstances = testingILWS
						.getInstances();
				InstanceSeedGenerator testInstanceSeedGen = testingILWS
						.getSeedGen();

				TrajectoryFile trajFile = new TrajectoryFile(new File(outputDir
						+ File.separator + "traj-run-"
						+ options.seedOptions.numRun + ".txt"), tfes);
				performance = (new Validator()).simpleValidate(testInstances,
						options.validationOptions,
						options.scenarioConfig.algoExecOptions.cutoffTime,
						testInstanceSeedGen, validatingTae, outputDir,
						options.scenarioConfig.getRunObjective(),
						options.scenarioConfig.getIntraInstanceObjective(),
						options.scenarioConfig.interInstanceObj, trajFile,
						true, coreHint, options.getAlgorithmExecutionConfig());
			} finally {
				validatingTae.notifyShutdown();
			}

			if (options.validationOptions.validateOnlyLastIncumbent) {
				Set<ProblemInstance> pis = new HashSet<ProblemInstance>();
				int pispCount = 0;
				for (ProblemInstanceSeedPair pisp : performance.get(
						performance.lastKey()).getPISPS()) {
					pispCount++;
					pis.add(pisp.getProblemInstance());
				}

				validationMessage = "Estimated mean quality of final incumbent config "
						+ runHistory.getThetaIdx(incumbent)
						+ " (internal ID: "
						+ incumbent
						+ ") on test set: "
						+ performance.get(performance.lastKey())
								.getPerformance()
						+ ", based on "
						+ pispCount
						+ " run(s) on " + pis.size() + " test instance(s).\n";
			} else {
				validationMessage = smac.logIncumbentPerformance(performance);
			}
			log.info(
					"\n----------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n"
							+ "{}Sample call for the final incumbent:\n{}\n"
							+ "Additional information about run {} in:{}\n"
							+ "----------------------------------------------------------------------------------------------------------------------------------------------------------------------------",
					validationMessage, smac.getCallString(),
					options.seedOptions.numRun, outputDir);
		} else {
			performance = new TreeMap<TrajectoryFileEntry, ValidationResult>();
			performance.put(
					tfes.get(tfes.size() - 1),
					new ValidationResult(Double.POSITIVE_INFINITY, Collections
							.<ProblemInstanceSeedPair> emptyList()));
			log.info(
					"\n----------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n"
							+ "Additional information about run {} in:{}\n"
							+ "----------------------------------------------------------------------------------------------------------------------------------------------------------------------------",
					options.seedOptions.numRun, outputDir);

		}
		smacBuilder.getEventManager().shutdown();
		return performance;
	}

}
