package ca.ubc.cs.beta.smac.ac.factory;

import java.util.List;

import ca.ubc.cs.beta.ac.config.AlgorithmExecutionConfig;
import ca.ubc.cs.beta.ac.config.RunConfig;
import ca.ubc.cs.beta.smac.ac.runners.AlgorithmRunner;
import ca.ubc.cs.beta.smac.ac.runners.ConcurrentAlgorithmRunner;
import ca.ubc.cs.beta.smac.ac.runners.SingleThreadedAlgorithmRunner;

public class AutomaticConfiguratorFactory {

	public static AlgorithmRunner getSingleThreadedAlgorithmRunner(AlgorithmExecutionConfig execConfig, List<RunConfig> instanceConfigs)
	{
		return new SingleThreadedAlgorithmRunner(execConfig, instanceConfigs);
	}
	
	
	public static AlgorithmRunner getConcurrentAlgorithmRunner(AlgorithmExecutionConfig execConfig, List<RunConfig> instanceConfigs)
	{
		if(instanceConfigs.size() == 1)
		{
			return getSingleThreadedAlgorithmRunner(execConfig, instanceConfigs);
		}
		return getConcurrentAlgorithmRunner(execConfig, instanceConfigs, Runtime.getRuntime().availableProcessors());
	}
	
	public static AlgorithmRunner getConcurrentAlgorithmRunner(AlgorithmExecutionConfig execConfig, List<RunConfig> instanceConfigs, int nThreads)
	{
		if(nThreads > Runtime.getRuntime().availableProcessors())
		{
			System.out.println("[WARN]: You have more threads set to be executing that processors, this may change results");
		}
		return new ConcurrentAlgorithmRunner(execConfig, instanceConfigs, nThreads);
	}
}
