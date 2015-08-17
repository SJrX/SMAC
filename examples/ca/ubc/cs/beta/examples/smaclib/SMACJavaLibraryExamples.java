package ca.ubc.cs.beta.examples.smaclib;

import java.io.File;
import java.io.IOException;

import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParameterConfiguration;
import ca.ubc.cs.beta.aeatk.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.base.analytic.AnalyticFunctions;
import ca.ubc.cs.beta.smac.executors.SMACJavaLib;
import ca.ubc.cs.beta.smac.lib.IObjectiveFunction;
import ca.ubc.cs.beta.smac.lib.ObjectiveFunctionResult;

import com.beust.jcommander.ParameterException;

public class SMACJavaLibraryExamples {
	
	public static void main(String[] args) throws ParameterException, IOException{
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
			double value = AnalyticFunctions.BRANINS.evaluate(x);

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
	 * @throws IOException 
	 * @throws ParameterException 
	 */
	public static void minimalistExample() throws ParameterException, IOException {
		// the number of SMAC iterations
		int maxIterations = 30;
		// the names of all variables
		String[] variableNames = new String[] { "x0", "x1" };
		// the initial values (it's also a minimum)
		double[] continuousVariablesInitial = new double[] { 0.0898, -0.7126 };
		// lower boundaries
		double[] continuousVariablesMin = new double[] {
				-10.0, -10.0};
		// upper boundaries
		double[] continuousVariablesMax = new double[] {
				10.0, 10.0};
		
		ParameterConfiguration incumbent = SMACJavaLib.optimize(
				simpleObjectiveFunction, maxIterations, variableNames, continuousVariablesInitial, continuousVariablesMin, continuousVariablesMax);
		System.out.println("The best found configuration is " + incumbent.getFormattedParameterString());
	}

	/**
	 * This function shows how to optimize SMAC with a parameter configuration space file.
	 * @throws IOException 
	 * @throws ParameterException 
	 */
	public static void minimalistExample2() throws ParameterException, IOException {
		
		if(!System.getProperty("user.dir").endsWith(SMACJavaLibraryExamples.class.getPackage().getName().replace('.', File.separatorChar))){
			System.err.println("You have to use this examples' directory as executing directory.");
			return;
		}
		// the number of SMAC iterations
		int maxIterations = 30;

		ParameterConfiguration incumbent = SMACJavaLib.optimize(
				simpleObjectiveFunction, new File("params.pcs"), maxIterations);
		System.out.println("The best found configuration is " + incumbent.getFormattedParameterString());
	}
}
