package ca.ubc.cs.beta.smac.ac.runners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ca.ubc.cs.beta.ac.config.AlgorithmExecutionConfig;
import ca.ubc.cs.beta.ac.config.RunConfig;
import ca.ubc.cs.beta.smac.ac.runs.AlgorithmRun;
import ca.ubc.cs.beta.smac.ac.runs.CommandLineAlgorithmRun;

public abstract class AbstractAlgorithmRunner implements AlgorithmRunner {

	protected final AlgorithmExecutionConfig execConfig;
	protected final List<RunConfig> instanceConfigs;

	protected final List<AlgorithmRun> runs;
	public AbstractAlgorithmRunner(AlgorithmExecutionConfig execConfig, List<RunConfig> instanceConfigs)
	{
		if(execConfig == null || instanceConfigs == null)
		{
			throw new IllegalArgumentException("Arguments cannot be null");
		}
		
		this.execConfig = execConfig;
		this.instanceConfigs = instanceConfigs;
		List<AlgorithmRun> runs = new ArrayList<AlgorithmRun>(instanceConfigs.size());
		
		for(RunConfig instConf: instanceConfigs)
		{
			runs.add(new CommandLineAlgorithmRun(execConfig, instConf));
		}
		
		
		this.runs = Collections.unmodifiableList(runs);

	}
	
	/* (non-Javadoc)
	 * @see ca.ubc.cs.beta.smac.ac.AlgorithmRunnerX#run()
	 */
	@Override
	public abstract List<AlgorithmRun> run();
	
	
	
}
