package ca.ubc.cs.beta.smac.lib.aeatk;

import org.mangosdk.spi.ProviderFor;

import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.AbstractTargetAlgorithmEvaluatorFactory;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorFactory;

@ProviderFor(TargetAlgorithmEvaluatorFactory.class)
public class SyncJavaTargetAlgorithmEvaluatorFactory extends
		AbstractTargetAlgorithmEvaluatorFactory {
	
	public static final String NAME = "SyncJava";

	@Override
	public TargetAlgorithmEvaluator getTargetAlgorithmEvaluator(
			AbstractOptions options) {
		if (options instanceof SyncJavaTargetAlgorithmEvaluatorOptions)
			return new SyncJavaTargetAlgorithmEvaluator((SyncJavaTargetAlgorithmEvaluatorOptions) options);
		else
			throw new RuntimeException("The " + this.getClass().getName()
					+ " expects "
					+ SyncJavaTargetAlgorithmEvaluatorOptions.class.getName()
					+ " but got " + options.getClass().getName());
	}

	@Override
	public AbstractOptions getOptionObject() {
		return new SyncJavaTargetAlgorithmEvaluatorOptions();
	}

	@Override
	public String getName() {
		return NAME;
	}
}
