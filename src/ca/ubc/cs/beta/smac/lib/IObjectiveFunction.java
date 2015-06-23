package ca.ubc.cs.beta.smac.lib;

import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParameterConfiguration;
import ca.ubc.cs.beta.aeatk.probleminstance.ProblemInstance;

/**
 * Interface for objective functions to be optimized with the
 * {@link SMACJavaExecutor}.
 * 
 * @author Simon Bartels
 *
 */
public interface IObjectiveFunction {
	/**
	 * Objective functions MUST return an {@link AlgorithmRunResult} given an
	 * {@link AlgorithmRunConfiguration}.
	 * 
	 * IMPLEMENTATION: Be sure to check {@link Thread#interrupt()} and to abort
	 * if the flag is set.
	 * 
	 * @param parameters
	 *            The run configuration.
	 * @param problemInstance
	 *            The problem instance. MAY be NULL.
	 * @param instanceSeed
	 *            Seed to be used for random activities within the function. MAY
	 *            be NULL.
	 * @return Quality, Progress and SAT or UNSAT if applicable.
	 */
	public ObjectiveFunctionResult computeObjectiveFunctionValue(
			ParameterConfiguration parameters, ProblemInstance problemInstance,
			Long instanceSeed);
}
