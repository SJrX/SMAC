package ca.ubc.cs.beta.smac.executors.randomrun;

import java.util.List;
import java.util.Random;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import ca.ubc.cs.beta.aclib.algorithmrun.AlgorithmRun;
import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration;
import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.aclib.probleminstance.InstanceListWithSeeds;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstanceSeedPair;
import ca.ubc.cs.beta.aclib.runconfig.RunConfig;
import ca.ubc.cs.beta.aclib.runhistory.NewRunHistory;
import ca.ubc.cs.beta.aclib.runhistory.RunHistory;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.smac.options.RandomRunnerOptions;
import ec.util.MersenneTwister;

/**
 * Executor class for making Random Runs, incomplete at the moment.
 * 
 * @author Steve Ramage 
 *
 */
public class RandomRunner {

	public static void main(String[] args)
	{
	/*
		try {
			
			int numRuns = 0;
		
			Random rand = new MersenneTwister();
			
			RandomRunnerOptions options = new RandomRunnerOptions();
			
			JCommander jcom = new JCommander( options, true , true);
			
			jcom.parse(args);
			
			List<ProblemInstance> pis = null;
			ParamConfigurationSpace configSpace = null;
			configSpace.setPRNG(rand);
			boolean deterministic = false;
			
			TargetAlgorithmEvaluator tae;
			double cutoff = 500;
			InstanceListWithSeeds seeds;
			
			RunHistory runHistory = new NewRunHistory(null, null, null, null);
			
			for(int i=0; i < numRuns; numRuns++)
			{
				
				ProblemInstance randomPi = pis.get(rand.nextInt(pis.size()));
				long seed = -1;
				
				if(!deterministic)
				{
					seed = Math.abs(rand.nextLong());
				}
				
				ProblemInstanceSeedPair pisp = new ProblemInstanceSeedPair(randomPi,seed);
				ParamConfiguration paramConfig = configSpace.getRandomConfiguration();
				RunConfig rc = new RunConfig(pisp, cutoff, paramConfig, false );
				
				List<AlgorithmRun> runs = tae.evaluateRun(rc);
				
				for(AlgorithmRun run : runs)
				{
					runHistory.append(run);
				}
			}
			
			
			System.exit(SMACReturnValues.SUCCESS);
		} catch(ParameterException e)
		{
			
			
			
			System.exit(SMACReturnValues.PARAMETER_EXCEPTION);
		}
		*/
		
	}
}
