package ca.ubc.cs.beta.smac;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import ca.ubc.cs.beta.ac.config.RunConfig;
import ca.ubc.cs.beta.config.AlgorithmExecutionConfig;
import ca.ubc.cs.beta.smac.ac.runners.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.smac.ac.runs.AlgorithmRun;
import ca.ubc.cs.beta.smac.exceptions.TrajectoryDivergenceException;

public class RunHashCodeVerifyingAlgorithmEvalutor extends TargetAlgorithmEvaluator {

	private final Queue<Integer> runHashQueue;
	private int hashCodesOfRuns = 0;
	private int runNumber = 0;
	private final Logger log = LoggerFactory.getLogger(getClass());
	private final Marker runHash = MarkerFactory.getMarker("RUN_HASH");
	
	
	public RunHashCodeVerifyingAlgorithmEvalutor(
			AlgorithmExecutionConfig execConfig, Queue<Integer> runHashes, boolean concurrentRuns) {
		super(execConfig, concurrentRuns);
		
		
		this.runHashQueue = runHashes;
		log.debug("Created with {} hash codes to verify", runHashQueue.size());
	}
	
	public RunHashCodeVerifyingAlgorithmEvalutor(
			AlgorithmExecutionConfig execConfig, boolean concurrentRuns) {
		this(execConfig, new LinkedList<Integer>(), concurrentRuns);
	}

	@Override
	public List<AlgorithmRun> evaluateRun(List<RunConfig> runConfigs)
	{
		//NOTE: runs are guaranteed to be in the same order as runConfigs
		//So we can just compute the runHash from the list iterator
		List<AlgorithmRun> runs = super.evaluateRun(runConfigs);
		validateRunHashCodes(runs);
		
		return runs;
	
	}
	
	private void validateRunHashCodes(List<AlgorithmRun> runs)
	{
		for(AlgorithmRun run: runs)
		{
			runNumber++;
			int hashCode = run.hashCode();
			hashCode =  (hashCode == Integer.MIN_VALUE) ? 0 : hashCode;  
			
			hashCodesOfRuns = (31*hashCodesOfRuns + Math.abs( hashCode)% 32452867) % 32452867 	; //Some prime around 2^25 (to prevent overflows in computation)
			log.info(runHash, "Run Hash Codes:{} After {} runs",hashCodesOfRuns, runNumber);
			
			Integer expectedHashCode = runHashQueue.poll();
			if(expectedHashCode == null)
			{
				log.debug("No More Hash Codes To Verify");
			} else if(hashCodesOfRuns != expectedHashCode)
			{
				throw new TrajectoryDivergenceException(expectedHashCode, hashCodesOfRuns, runNumber);
			} else
			{
				log.debug("Hash Code {} matched {}", expectedHashCode, hashCodesOfRuns);
			}
		}
	}
	@Override
	public void seek(List<AlgorithmRun> runs)
	{
		super.seek(runs);
		validateRunHashCodes(runs);
		
	}

}
