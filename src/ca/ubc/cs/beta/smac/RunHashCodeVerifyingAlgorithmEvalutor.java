package ca.ubc.cs.beta.smac;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import ca.ubc.cs.beta.aclib.algorithmrun.AlgorithmRun;
import ca.ubc.cs.beta.aclib.exceptions.TrajectoryDivergenceException;
import ca.ubc.cs.beta.aclib.runconfig.RunConfig;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.AbstractTargetAlgorithmEvaluatorDecorator;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluator;

public class RunHashCodeVerifyingAlgorithmEvalutor extends AbstractTargetAlgorithmEvaluatorDecorator {

	private final Queue<Integer> runHashQueue;
	private int hashCodesOfRuns = 0;
	private int runNumber = 0;
	private final Logger log = LoggerFactory.getLogger(getClass());
	private final Marker runHash = MarkerFactory.getMarker("RUN_HASH");
	
	
	public RunHashCodeVerifyingAlgorithmEvalutor(TargetAlgorithmEvaluator tae, Queue<Integer> runHashes) {
		super(tae);
		
		
		this.runHashQueue = runHashes;
		
		log.debug("Created with {} hash codes to verify", runHashQueue.size());
		if(runHashQueue.size() == 0)
		{
			outOfHashCodesDisplayed = true;
		}
	}
	
	public RunHashCodeVerifyingAlgorithmEvalutor(TargetAlgorithmEvaluator tae) {
		this(tae, new LinkedList<Integer>());
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
	boolean outOfHashCodesDisplayed = false;
	private void validateRunHashCodes(List<AlgorithmRun> runs)
	{
		for(AlgorithmRun run: runs)
		{
			runNumber++;
			int hashCode = run.hashCode();
			hashCode =  (hashCode == Integer.MIN_VALUE) ? 0 : hashCode;  
			
			hashCodesOfRuns = (31*hashCodesOfRuns + Math.abs( hashCode)% 32452867) % 32452867 	; //Some prime around 2^25 (to prevent overflows in computation)
			log.debug(runHash, "Run Hash Codes:{} After {} runs",hashCodesOfRuns, runNumber);
			
			Integer expectedHashCode = runHashQueue.poll();
			if(expectedHashCode == null)
			{
				if(!outOfHashCodesDisplayed)
				{
					log.debug("No More Hash Codes To Verify");
					outOfHashCodesDisplayed = true;
				}
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
