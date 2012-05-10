package ca.ubc.cs.beta.smac.ac.runners;

import java.util.List;

import ca.ubc.cs.beta.ac.config.AlgorithmExecutionConfig;
import ca.ubc.cs.beta.ac.config.RunConfig;
import ca.ubc.cs.beta.smac.ac.runs.AlgorithmRun;

public class SingleThreadedAlgorithmRunner extends AbstractAlgorithmRunner
{

	public SingleThreadedAlgorithmRunner(AlgorithmExecutionConfig execConfig,
			List<RunConfig> instanceConfigs) {
		super(execConfig, instanceConfigs);
		
	}

	@Override
	public List<AlgorithmRun> run() 	
	{
		for(AlgorithmRun run : runs)
		{
			run.run();
		}
			
		return runs;
	
		
	}

}
