package ca.ubc.cs.beta.smac.executors.randomrun;

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
		int numRuns = 0;
	
		Random rand = new MersenneTwister();
		List<ProblemInstance> pis = null;
		ParamConfigurationSpace configSpace = null;
		configSpace.setPRNG(rand);
		boolean deterministic = false;
		
		TargetAlgorithmEvaluator tae;
		double cutoff = 500;
		InstanceListWithSeeds seeds;
		
		RunHistory runHistory = new NewRunHistory();
		
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
		
		
		*/
	}
}
