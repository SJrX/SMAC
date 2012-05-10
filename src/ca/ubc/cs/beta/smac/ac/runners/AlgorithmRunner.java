package ca.ubc.cs.beta.smac.ac.runners;

import java.util.List;

import ca.ubc.cs.beta.smac.ac.runs.AlgorithmRun;

/**
 * Interface for objects that can handle running AlgorithmInstanceRunConfigs and AlgorithmExecutionConfigs
 * 
 * Use the AutomaticConfiguratorFactory to get your Runner
 * @author seramage
 */
public interface AlgorithmRunner {

	/**
	 * Runs the algorithm
	 * 
	 * Order of Resulting Array is Guaranteed to be the same order as the input array
	 * 
	 * @return
	 */
	public List<AlgorithmRun> run();

}