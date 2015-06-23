/**
 * 
 */
package ca.ubc.cs.beta.smac.lib.aeatk;

import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.smac.lib.IObjectiveFunction;
import ca.ubc.cs.beta.smac.lib.ObjectiveFunctionResult;

/**
 * {@link Runnable} that starts instances of {@link IObjectiveFunction} and
 * returns their results.
 * 
 * @author Simon Bartels
 *
 */
public class ObjectiveFunctionRunnable implements Runnable {

	final private IObjectiveFunction f;

	final private AlgorithmRunConfiguration config;

	private ObjectiveFunctionResult result = null;

	public ObjectiveFunctionRunnable(IObjectiveFunction objectiveFunction,
			AlgorithmRunConfiguration config) {
		f = objectiveFunction;
		this.config = config;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		result = f.computeObjectiveFunctionValue(config
				.getParameterConfiguration(), config
				.getProblemInstanceSeedPair().getProblemInstance(), config
				.getProblemInstanceSeedPair().getSeed());
	}

	/**
	 * Returns the result of the objective function or null if the objective
	 * function is not finished.
	 * 
	 * @return result or null
	 */
	public ObjectiveFunctionResult getResult() {
		return result;
	}

}
