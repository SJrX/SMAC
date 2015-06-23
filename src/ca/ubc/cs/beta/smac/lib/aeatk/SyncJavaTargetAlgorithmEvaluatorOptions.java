package ca.ubc.cs.beta.smac.lib.aeatk;

import ca.ubc.cs.beta.aeatk.misc.options.UsageTextField;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.smac.lib.IObjectiveFunction;

@UsageTextField(title = "Sync Java Target Algorithm Evaluator Options", description = "This Target Algorithm Evaluator executes calls java methods. Options can NOT be set via command line.")
public class SyncJavaTargetAlgorithmEvaluatorOptions extends AbstractOptions {
	/**
	 * Serial version UId.
	 */
	private static final long serialVersionUID = -4872388585031394212L;

	/**
	 * The objective function to be called.
	 */
	public IObjectiveFunction objectiveFunction = null;

	/**
	 * The frequency in milliseconds +25 for observer updates. A negative value
	 * will be understood as starting no observers!
	 */
	public int observerFrequency = 0;
}
