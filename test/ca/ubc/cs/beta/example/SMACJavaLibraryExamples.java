package ca.ubc.cs.beta.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.Test;

import ca.ubc.cs.beta.aeatk.misc.associatedvalue.Pair;
import ca.ubc.cs.beta.aeatk.objectives.RunObjective;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.NormalizedRange;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParameterConfiguration;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParameterConfigurationSpace;
import ca.ubc.cs.beta.aeatk.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aeatk.smac.SMACOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.base.analytic.AnalyticFunctions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.base.java.IObjectiveFunction;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.base.java.ObjectiveFunctionResult;
import ca.ubc.cs.beta.junit.SMACLibraryTest;
import ca.ubc.cs.beta.smac.configurator.AbstractAlgorithmFramework;
import ca.ubc.cs.beta.smac.executors.SMACJavaLib;

public class SMACJavaLibraryExamples {
	
	public static void main(String[] args){
		minimalistExample();
	}

	/**
	 * A simple objective function (Branin) to demonstrate basic functionalities
	 * of SMAC.
	 */
	protected static IObjectiveFunction simpleObjectiveFunction = new IObjectiveFunction() {
		/**
		 * This objective function computes the Branin function. It assumes the
		 * parameters are called x0 and x1.
		 */
		@Override
		public ObjectiveFunctionResult computeObjectiveFunctionValue(
				ParameterConfiguration parameters,
				ProblemInstance problemInstance, Long instanceSeed) {
			/*
			 * Evaluate the Branin function.
			 */
			double[] x = new double[2];
			x[0] = Double.parseDouble(parameters.get("x0"));
			x[1] = Double.parseDouble(parameters.get("x1"));
			double value = -AnalyticFunctions.BRANINS.evaluate(x);

			/*
			 * IMPORTANT: Check if SMAC wants you to stop while computing your
			 * objective function! If you do not return SMAC will not be able to
			 * perform capping.
			 */
			if (Thread.interrupted())
				return null;

			/*
			 * If you don't have SAT instances this should not concern you.
			 */
			boolean SAT = true;

			/*
			 * The progress measure is problem specific. The only restriction is
			 * that values are in {-1} or [0, infinity].
			 */
			double progress = 1.0;

			return new ObjectiveFunctionResult(value, SAT, progress);
		}
	};

	/**
	 * This function is a minimalist example on how to optimize a function with
	 * SMAC.
	 */
	public static void minimalistExample() {
		// the number of SMAC iterations
		int maxIterations = 3;
		// the names of all variables
		List<String> variableNames = Arrays.asList(new String[] { "x0", "x1" });
		// the initial values (it's also a minimum)
		double[] continuousVariablesInitial = new double[] { 0.0898, -0.7126 };
		// lower boundaries
		double[] continuousVariablesMin = new double[] {
				-10.0, -10.0};
		// upper boundaries
		double[] continuousVariablesMax = new double[] {
				10.0, 10.0};
		// And that's all... (leave integer and categorical variables null)
		ParameterConfiguration incumbent = SMACJavaLib.optimize(
				simpleObjectiveFunction, maxIterations, variableNames,
				continuousVariablesInitial, continuousVariablesMin,
				continuousVariablesMax, null, null, null, null);
		System.out.println("The best found configuration is " + incumbent.getFormattedParameterString()
				+ ", the initial configuration " + Arrays.toString(continuousVariablesInitial));
	}

	/**
	 * Reference implementation of {@link IObjectiveFunction}.
	 */
	protected static IObjectiveFunction complexObjectiveFunction = new IObjectiveFunction() {

		/**
		 * This function is (a bit stupid) example implementation for an
		 * objective function. All {@link AnalyticFunctions} (e.g. Branin) are
		 * used as problem instances which does not make much sense of course.
		 * First the result of the respective function is computed and then we
		 * wait some time to simulate computational demand.
		 * 
		 * NOTE: In the while loop we check the interrupted flag. It is of
		 * UTTERMOST importance that you check this flag in your implementation
		 * and to abort execution if the flag is set.
		 * 
		 * To see how to configure SMAC to optimize this function see
		 * {@link SMACLibraryTest#testExampleCall()}.
		 */
		@Override
		public ObjectiveFunctionResult computeObjectiveFunctionValue(
				ParameterConfiguration parameters, ProblemInstance p, Long seed) {
			// add some noise - just for fun.
			double noise = new Random(seed).nextGaussian();

			// first input value to our function
			X_VALS[0] = Double.parseDouble(parameters.get(VARIABLE_1_NAME));

			/*
			 * The second parameter is categorical with the values good, bad and
			 * fatal.
			 */
			String var2_value = parameters.get(VARIABLE_2_NAME);
			if (VARIABLE_2_VALUE_GOOD.equals(var2_value))
				X_VALS[1] = 2.275;
			else {
				throw new RuntimeException(
						"The program decided to crash. Shit happens.");
			}

			/*
			 * I use the instance name to determine the actual function (e.g.
			 * Branin).
			 */
			double quality = AnalyticFunctions.valueOf(p.getInstanceName())
					.evaluate(X_VALS) + noise;
			/*
			 * If you don't have SAT instances this should not concern you.
			 */
			boolean SAT = true;

			/*
			 * The progress measure is problem specific. The only restriction is
			 * that values are in {-1} or [0, infinity].
			 */
			double progress = 1.0;
			ObjectiveFunctionResult result = new ObjectiveFunctionResult(
					quality, SAT, progress);

			/*
			 * Now we simulate some computations. NOTE: In the following while
			 * loop we check if we are supposed to stop or not.
			 */
			int min_waiting_time_in_ms = 500;
			int additional_waiting_time_in_ms = new Random(seed).nextInt(12000);
			long wait_until = min_waiting_time_in_ms
					+ additional_waiting_time_in_ms
					+ System.currentTimeMillis();
			/*
			 * A value we read and write so the compiler does not optimize the
			 * next code.
			 */
			int foobar = new Random(seed).nextInt(Integer.MAX_VALUE) + 1;
			while (wait_until > System.currentTimeMillis()) {
				/*
				 * Just do do something to keep our CPU busy in such a way that
				 * the compiler does not optimize it away.
				 */
				foobar = new Random(foobar).nextInt(Integer.MAX_VALUE) + 1;
				/*
				 * ###########################################################
				 * 
				 * THIS CHECK IS IMPORTANT! If you don't return if your thread
				 * was interrupted SMAC is unable to perform capping.
				 * 
				 * ###########################################################
				 */
				if (Thread.interrupted()) {
					// You could also return null but this is of course better.
					/*
					 * Even though the parameter is called quality it is
					 * actually a loss.
					 */
					quality = Double.POSITIVE_INFINITY;
					SAT = false;
					progress = -1.0;
					return new ObjectiveFunctionResult(quality, SAT, progress);
				}
			}
			return result;
		}
	};

	/**
	 * Input values for {@link AnalyticFunctions}.
	 */
	private static final double[] X_VALS = new double[2];

	/**
	 * Name of the first variable of the referenceObjectiveFunction.
	 */
	private static final String VARIABLE_1_NAME = "param1";

	/**
	 * Name of the second variable of the referenceObjectiveFunction.
	 */
	private static final String VARIABLE_2_NAME = "param2";

	/**
	 * First value of the categorical variable #2: "good".
	 */
	private static final String VARIABLE_2_VALUE_GOOD = "good";

	/**
	 * Second value of the categorical variable #2: "bad".
	 */
	private static final String VARIABLE_2_VALUE_BAD = "bad";

	/**
	 * Third value of the categorical variable #2: "fatal".
	 */
	private static final String VARIABLE_2_VALUE_FATAL = "fatal";

	/**
	 * This is an example how SMAC could be used as a JAVA library. It shows how
	 * to initialize SMAC to optimize the referenceObjectiveFunction.
	 * 
	 * This example tries to show all possibilities at once. Be sure to check
	 * out the other "optimize" functions of {@link SMACJavaLib} as they provide
	 * an easier access.
	 */
	@Test
	public static void fullExample() {
		SMACOptions smacOpts = new SMACOptions();
		/*
		 * Sets the objective which is either quality or runtime.
		 */
		smacOpts.scenarioConfig._runObj = RunObjective.RUNTIME;

		/*
		 * Fixes the number of SMAC iterations.
		 */
		final int iters = 10;
		smacOpts.scenarioConfig.limitOptions.numIterations = iters;

		/*
		 * Validates the incumbent with the specified number of runs.
		 */
		smacOpts.doValidation = true;
		smacOpts.validationOptions.numberOfValidationRuns = 3;

		/*
		 * ##############################################################
		 * 
		 * This part specifies our parameter space. The minimum we need to
		 * provide is at least one categorical or continuous variable and a
		 * default value. However, here we will provide a bit more.
		 */
		Map<String, String> defaultValues = new HashMap<String, String>();

		/*
		 * This is how continuous variables are initialized.
		 */
		Map<String, NormalizedRange> continuousVariables = new HashMap<String, NormalizedRange>();
		continuousVariables.put(VARIABLE_1_NAME, new NormalizedRange(-5, 5,
				false, false));
		defaultValues.put(VARIABLE_1_NAME, "0.0");

		/*
		 * Here we initialize categorical variables.
		 */
		Map<String, List<String>> categoricalVariables = new HashMap<String, List<String>>();
		List<String> variable2_Values = new ArrayList<String>();
		variable2_Values.add(VARIABLE_2_VALUE_GOOD);
		variable2_Values.add(VARIABLE_2_VALUE_BAD);
		variable2_Values.add(VARIABLE_2_VALUE_FATAL);
		categoricalVariables.put(VARIABLE_2_NAME, variable2_Values);
		defaultValues.put(VARIABLE_2_NAME, VARIABLE_2_VALUE_GOOD);

		/*
		 * Forbidden configurations MUST NOT refer to continuous variables. One
		 * forbidden configuration is a list of variables and corresponding
		 * forbidden values.
		 */
		List<List<Pair<String, String>>> forbiddenConfigurations = new ArrayList<List<Pair<String, String>>>();
		List<Pair<String, String>> forbiddenConfiguration = new ArrayList<Pair<String, String>>();
		forbiddenConfiguration.add(new Pair<String, String>(VARIABLE_2_NAME,
				VARIABLE_2_VALUE_FATAL));
		forbiddenConfigurations.add(forbiddenConfiguration);

		// No conditional variables.
		Map<String, Map<String, List<String>>> dependentValues = null;

		// Now we have what we need to instantiate a parameter space.
		ParameterConfigurationSpace space = new ParameterConfigurationSpace(
				categoricalVariables, continuousVariables, dependentValues,
				defaultValues, forbiddenConfigurations);

		/*
		 * ##############################################################
		 * 
		 * OPTIONALLY we can define instances. These objects are allowed to be
		 * NULL. We will define two instances: Branin and Camelback. They will
		 * be our training as well as our test instances.
		 */
		List<ProblemInstance> trainingInstances = new ArrayList<ProblemInstance>();
		// The UNIQUE name of the instance.
		String name = AnalyticFunctions.BRANINS.name();
		/*
		 * We could also provide environmental variables but not so in this
		 * example.
		 */
		Map<String, Double> features = null;
		/*
		 * If applicable we can tell SMAC if this instance is satisfiable or not
		 * but it is not necessary. This string may contain anything.
		 */
		String additionalInformation = "unSAT";
		ProblemInstance branin = new ProblemInstance(name, features,
				additionalInformation);
		ProblemInstance camel = new ProblemInstance(
				AnalyticFunctions.CAMELBACK.name(), null, null);
		trainingInstances.add(branin);
		trainingInstances.add(camel);

		// In this case test and training instances are the same.
		List<ProblemInstance> testInstances = trainingInstances;

		// Now we have everything to start optimization.
		AbstractAlgorithmFramework smac = SMACJavaLib.optimize(
				complexObjectiveFunction, space, smacOpts, trainingInstances,
				testInstances);
		// TODO: maybe return best value or something
		System.out.println(smac.getIncumbent());
	}

}
