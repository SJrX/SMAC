package ca.ubc.cs.beta.smac;

import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.cs.beta.ac.config.AlgorithmExecutionConfig;
import ca.ubc.cs.beta.ac.config.RunConfig;
import ca.ubc.cs.beta.smac.ac.factory.AutomaticConfiguratorFactory;
import ca.ubc.cs.beta.smac.ac.runners.AlgorithmRunner;
import ca.ubc.cs.beta.smac.ac.runs.AlgorithmRun;

public class TargetAlgorithmEvalutor {
	
	
	private final AlgorithmExecutionConfig execConfig;
	private int runHashCodes = 0;
	private int runCount = 1;
	
	private final Logger log = LoggerFactory.getLogger(getClass());

	public TargetAlgorithmEvalutor(AlgorithmExecutionConfig execConfig)
	{
		log.debug("Initalized with the following Execution Configuration {}" , execConfig);
		this.execConfig = execConfig;

	}
	
	
	/**
	 * @param run - Run Configurations to evaluate
	 * @return
	 */
	public List<AlgorithmRun> evaluateRun(RunConfig run) 
	{
		return evaluateRun(Collections.singletonList(run));
	}

	public List<AlgorithmRun> evaluateRun(List<RunConfig> runConfigs)
	{
		AlgorithmRunner runner = getAlgorithmRunner(runConfigs);
		List<AlgorithmRun> runs =  runner.run();
		return runs;
	}
	
	
	private AlgorithmRunner getAlgorithmRunner(List<RunConfig> runConfigs)
	{
		boolean concurrentExecution = true;
		if(concurrentExecution)
		{
			log.info("Using Concurrent Algorithm Runner");
			return AutomaticConfiguratorFactory.getConcurrentAlgorithmRunner(execConfig,runConfigs);
			
		} else
		{
			log.info("Using Single Threaded Algorithm Runner");
			return AutomaticConfiguratorFactory.getSingleThreadedAlgorithmRunner(execConfig,runConfigs);
		}
	}
	
	/**
	 * Returns the number of target algorithm runs that we have executed
	 * @return
	 */
	public int getRunCount()
	{
		return runCount;
	}
	
	/**
	 * 'May' return a unique number that should roughly correspond to a unique sequence of run requests.
	 *  
	 * [i.e. A user seeing the same sequence of run codes, should be confident that the runs by the Automatic Configurator are 
	 * identical]. Note: This method is optional and you may just return zero. 
	 * 
	 * @return
	 */
	public int getRunHash()
	{
		return runHashCodes;
	}


	public void seek(List<AlgorithmRun> runs) {
		runCount = runs.size();
		
	}
	
}
