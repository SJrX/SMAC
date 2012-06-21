package ca.ubc.cs.beta.smac;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.cs.beta.ac.config.RunConfig;
import ca.ubc.cs.beta.config.SMACConfig;
import ca.ubc.cs.beta.configspace.ParamConfiguration;
import ca.ubc.cs.beta.configspace.ParamConfiguration.StringFormat;
import ca.ubc.cs.beta.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.probleminstance.ProblemInstanceSeedPair;
import ca.ubc.cs.beta.random.SeedableRandomSingleton;
import ca.ubc.cs.beta.seedgenerator.InstanceSeedGenerator;
import ca.ubc.cs.beta.seedgenerator.RandomInstanceSeedGenerator;
import ca.ubc.cs.beta.smac.ac.runners.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.smac.ac.runs.AlgorithmRun;
import ca.ubc.cs.beta.smac.history.DuplicateRunException;
import ca.ubc.cs.beta.smac.history.NewRunHistory;
import ca.ubc.cs.beta.smac.history.RunHistory;
import ca.ubc.cs.beta.smac.state.RandomPoolType;
import ca.ubc.cs.beta.smac.state.StateDeserializer;
import ca.ubc.cs.beta.smac.state.StateFactory;
import ca.ubc.cs.beta.smac.state.StateSerializer;
import ca.ubc.cs.beta.smac.util.AutoStartStopWatch;
import ca.ubc.cs.beta.smac.util.StopWatch;

public class AbstractAlgorithmFramework {

	
	/**
	 * PRNG Generator used by algorithms
	 * Should only be modified by restoreState, and not by run()
	 */
	protected Random rand;
	
	/**
	 * Run History class
	 * Should only be modified by restoreState, and not by run()
	 */
	protected RunHistory runHistory;

	protected final long applicationStartTime = System.currentTimeMillis();

	protected final ParamConfigurationSpace configSpace;
	
	
	
	
	protected final double cutoffTime;
	
	protected final List<ProblemInstance> instances;
	protected final List<ProblemInstance> testInstances;
	
	protected final TargetAlgorithmEvaluator algoEval;
	
	/**
	 * Stores our configuration
	 */
	@Deprecated
	protected final SMACConfig config;
	
	protected final Logger log = LoggerFactory.getLogger(this.getClass());
		
	private final StateFactory stateFactory;
	
	private final FileWriter fout;
	
	private int iteration;
	protected ParamConfiguration incumbent = null;
	
	private final int MAX_RUNS_FOR_INCUMBENT;
	
	public AbstractAlgorithmFramework(SMACConfig smacConfig, List<ProblemInstance> instances,List<ProblemInstance> testInstances, TargetAlgorithmEvaluator algoEval, StateFactory stateFactory, ParamConfigurationSpace configSpace, InstanceSeedGenerator instanceSeedGen)
	{
		
		this.instances = instances;
		this.testInstances = testInstances;
		this.cutoffTime = smacConfig.scenarioConfig.cutoffTime;
		this.config = smacConfig;
		
		this.algoEval = algoEval;
		this.stateFactory = stateFactory;
		
		long time = System.currentTimeMillis();
		
		Date d = new Date(time);
		DateFormat df = DateFormat.getDateTimeInstance();
		
		log.info("Automatic Configuration Start Time is {}", df.format(d));
		
		//smacConfig = parseCLIOptions(args);
		//TODO We should be handed the Random object
		if(smacConfig.seed != 0)
		{
			SeedableRandomSingleton.setSeed(smacConfig.seed);
		} 
		
		rand = SeedableRandomSingleton.getRandom(); 

		this.configSpace = configSpace;
		
		runHistory = new NewRunHistory(instanceSeedGen,smacConfig.scenarioConfig.overallObj, smacConfig.scenarioConfig.overallObj, smacConfig.scenarioConfig.runObj);
		//RunHistory h1 = new LegacyRunHistory(new InstanceSeedGenerator(instances,smacConfig.seed),smacConfig.overallObj, smacConfig.overallObj, smacConfig.runObj);
		//runHistory = new DebugRunHistory(h1,h2);
		iteration = 0;
		
		if(instanceSeedGen.getInitialSeedCount() < config.maxIncumbentRuns)
		{
			log.info("Due to lack of instance/seeds maximum number of runs limited to {}", instanceSeedGen.getInitialSeedCount());
			MAX_RUNS_FOR_INCUMBENT = instanceSeedGen.getInitialSeedCount();
		}  else
		{
			MAX_RUNS_FOR_INCUMBENT=smacConfig.maxIncumbentRuns;
			log.info("Maximimum Number of Runs for the Incumbent Initialized to {}:", MAX_RUNS_FOR_INCUMBENT);
		}
		
		
		try {
			String outputFileName = config.scenarioConfig.outputDirectory + File.separator + config.runID + File.separator +"traj-algo-" + config.runID.replaceAll("\\s+", "_") + ".txt";
			this.fout = new FileWriter(new File(outputFileName));
			log.info("Trajectory File Writing To: {}", outputFileName);
			fout.write(config.runID + "\n");
			
			
			
		} catch (IOException e) {
			
			throw new IllegalStateException("Could not create trajectory file: " , e);
		}
		
	}
	
	
	/*
	public AbstractAlgorithmFramework(SMACConfig smacConfig, List<ProblemInstance> instances,List<ProblemInstance> testInstances, AlgorithmEvalutor algoEval, StateFactory stateFactory, StateDeserializer sd)
	{
		
		this.instances = instances;
		this.testInstances = testInstances;
		this.cutoffTime = smacConfig.cutoffTime;
		this.config = smacConfig;
		
		this.algoEval = algoEval;
		this.stateFactory = stateFactory;
		
		long time = System.currentTimeMillis();
		
		Date d = new Date(time);
		DateFormat df = DateFormat.getDateInstance();
		
		configSpace = ParamFileHelper.getParamFileParser(smacConfig.paramFile);
		log.info("Automatic Configuration Start Time is {}", df.format(d));
		
		//smacConfig = parseCLIOptions(args);
		//TODO We should be handed the Random object
		/*
		if(smacConfig.seed != 0)
		{
			SeedableRandomSingleton.setSeed(smacConfig.seed);
		} 
		//rand = SeedableRandomSingleton.getRandom(); 
		
	
		
		
		
		

		
		//this.runHistory = new RunHistory(new InstanceSeedGenerator(instances,smacConfig.seed),smacConfig.overallObj, smacConfig.runObj);
	
	}
	*/
	
	public ParamConfiguration getIncumbent()
	{
		return incumbent;
	}
	
	public void restoreState(StateDeserializer sd)
	{
		log.info("Restoring State");
		rand = sd.getPRNG(RandomPoolType.SEEDABLE_RANDOM_SINGLETON);
		SeedableRandomSingleton.setRandom(rand);
		configSpace.setPRNG(sd.getPRNG(RandomPoolType.PARAM_CONFIG));
		iteration = sd.getIteration();
		
		runHistory = sd.getRunHistory();
		
		incumbent = sd.getIncumbent();
		log.info("Incumbent Set To {}",incumbent);
		
		algoEval.seek(runHistory.getAlgorithmRuns());
		
		log.info("Restored to Iteration {}", iteration);
	}
	
	
	/**
	 * Function that determines whether we should stop processing or not
	 * @param iteration - number of iterations we have done
	 * @return
	 */
	protected boolean have_to_stop(int iteration)
	{
		if(runHistory.getTotalRunCost() > config.scenarioConfig.tunerTimeout)
		{
			log.info("Run cost {} greater than tuner timeout {}",runHistory.getTotalRunCost(), config.scenarioConfig.tunerTimeout);
			return true;
		}
		
		if(iteration >= config.numIteratations)
		{
			log.info("Iteration {} greater than number permitted {}", iteration, config.numIteratations);
			return true;
		}
		
		if(runHistory.getAlgorithmRunData().size() > config.totalNumRunLimit)
		{
			log.info("Number of runs {} is greater than the number permitted {}",runHistory.getAlgorithmRunData().size(), config.totalNumRunLimit);
			return true;
		}
		
		return false;

		
	}
	
	
	public void logIncumbent()
	{
		logIncumbent(-1);
	}
	public void logIncumbent(int iteration)
	{
		if (iteration > 0)
		{
			Object[] arr = {iteration,  incumbent, incumbent.getFormattedParamString()};		
			log.info("At end of iteration {}, incumbent is {} {}",arr);
		} else
		{
			log.info("Incument currently is: ", incumbent, incumbent.getFormattedParamString());
		}
		ThreadMXBean b = ManagementFactory.getThreadMXBean();
		double wallTime = (System.currentTimeMillis() - applicationStartTime) / 1000.0;
		double cpuTime = runHistory.getTotalRunCost();
		
		double acTime = b.getCurrentThreadCpuTime() / 1000.0 / 1000 / 1000;
		Object[] arr2 = { iteration, wallTime , config.runtimeLimit - wallTime , cpuTime,config.scenarioConfig.tunerTimeout - cpuTime,   b.getCurrentThreadCpuTime() / 1000.0 / 1000 / 1000, b.getCurrentThreadUserTime() / 1000.0 / 1000 / 1000 , Runtime.getRuntime().maxMemory() / 1024.0 / 1024, Runtime.getRuntime().totalMemory() / 1024.0 / 1024, Runtime.getRuntime().freeMemory() / 1024.0 / 1024 };
		
		

		
		// -1 should be the variance but is allegedly the sqrt in compareChallengersagainstIncumbents.m and then is just set to -1.
		writeIncumbent(runHistory.getTotalRunCost() + acTime, runHistory.getEmpiricalCost(incumbent, runHistory.getUniqueInstancesRan(), this.cutoffTime),-1,runHistory.getThetaIdx(incumbent), acTime, incumbent.getFormattedParamString(StringFormat.STATEFILE_SYNTAX));
		
		log.info("*****Runtime Statistics*****\n Iteration: {}\n Wallclock Time: {} s\n Wallclock Time Remaining:{} s\n Total CPU Time: {} s\n CPU Time Remaining: {} s\n AC CPU Time: {} s\n AC User Time: {} s\n Max Memory: {} MB\n Total Java Memory: {} MB\n Free Java Memory: {} MB\n",arr2);
		
		
	}
	
	/**
	 * Writes the incumbent to the trajectory file
	 * @param totalTime
	 * @param meanInc
	 * @param stdDevInc
	 * @param thetaIdxInc
	 * @param acTime
	 * @param paramString
	 */
	private void writeIncumbent(double totalTime, double meanInc, double stdDevInc, int thetaIdxInc, double acTime, String paramString)
	{
		
		String outLine = totalTime + ", " + meanInc + ", " + stdDevInc + ", " + thetaIdxInc + ", " + acTime + ", " + paramString +"\n";
		try 
		{
			fout.write(outLine);
			fout.flush();
		} catch(IOException e)
		{
			throw new IllegalStateException("Could not update trajectory file", e);
		}

	}
	public int getIteration()
	{
		return iteration;
	}
	/**
	 * Actually performs the Automatic Configuration
	 */
	public void run()
	{
		try {
			try {
				
				if(iteration == 0)
				{ 
					incumbent = configSpace.getDefaultConfiguration();
					log.info("Default Configuration set as Incumbent: {}", incumbent);
					iteration = 0;
					
					
					/**
					 * Evaluate Default Configuration
					 */
					ProblemInstanceSeedPair pisp = runHistory.getRandomInstanceSeedWithFewestRunsFor(incumbent, instances, rand);
					log.trace("New Problem Instance Seed Pair generated {}", pisp);
					RunConfig incumbentRunConfig = getRunConfig(pisp, cutoffTime,incumbent);
					evaluateRun(incumbentRunConfig);
					//Create initial row
					writeIncumbent(0, Double.MAX_VALUE, -1,runHistory.getThetaIdx(incumbent),0, incumbent.getFormattedParamString(StringFormat.STATEFILE_SYNTAX));
					logIncumbent(iteration);
				} else
				{
					//We are restoring state
				}
				
				/**
				 * Main Loop
				 */
				
				
				
				while(!have_to_stop(iteration))
				{
					if(shouldSave()) saveState();
					
					
					runHistory.incrementIteration();
					iteration++;
					log.info("Starting Iteration {}", iteration);
					StopWatch t = new AutoStartStopWatch();
					learnModel(runHistory, configSpace);
					
					
					t.stop();
					
					ArrayList<ParamConfiguration> challengers = new ArrayList<ParamConfiguration>();
					challengers.addAll(selectConfigurations());
					intensify(challengers,0);
					
					
					
					logIncumbent(iteration);
					
				}
				
				saveState("it", true);
				log.info("SMAC Completed");
				
			} catch(RuntimeException e)
			{
				try{
					
					
					saveState("CRASH",true);
				} catch(RuntimeException e2)
				{
					log.error("SMAC has encounted an exception, and encountered another exception while trying to save the local state. NOTE: THIS DID NOT CAUSE SMAC TO FAIL. The following exception/error message is the cause. This is potentially another / seperate issue, or a disk failure of some kind. When submitting bug/error reports, please include enough context for *BOTH* exceptions \n  ", e2);
					throw e;
				}
				throw e;
			}
		} finally
		{
			try {
				fout.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				log.error("Trying to close Trajectory File failed with exception {}", e);
			}
			
		}
		
		
		//log.info("Real Time: {} s, CPU Time: {}, User Time:{} ",data);

	}
	
	protected boolean shouldSave() 
	{
		//Perfect power of 2
		return true;
	}

	
	private void saveState()
	{
		saveState("it",(((iteration - 1) & iteration) == 0));
	}

	private void saveState(String id, boolean saveFullState) {
		StateSerializer state = stateFactory.getStateSerializer(id, iteration);
		state.setPRNG(RandomPoolType.SEEDABLE_RANDOM_SINGLETON, SeedableRandomSingleton.getRandom());
		state.setPRNG(RandomPoolType.PARAM_CONFIG, configSpace.getPRNG());
		if(saveFullState)
		{	
			//Only save run history on perfect powers of 2.
			state.setRunHistory(runHistory);
		} 
		state.setInstanceSeedGenerator(runHistory.getInstanceSeedGenerator());
		state.setIncumbent(incumbent);
		state.save();
		
		
	}



	protected void learnModel(RunHistory runHistory,
			ParamConfigurationSpace configSpace) {
	}



	protected List<ParamConfiguration> selectConfigurations()
	{
		ParamConfiguration c = configSpace.getRandomConfiguration();
		log.debug("Selecting a random configuration {}", c);
		return Collections.singletonList(c);
	}
	/**
	 * Intensification
	 * @param challengers - List of challengers we should check against
	 * @param timeBound  - Amount of time we are allowed to run against
	 */
	private void intensify(List<ParamConfiguration> challengers, double timeBound) 
	{
		
	
		double initialTime = runHistory.getTotalRunCost();
		log.info("Intensifying on Challengers");
		for(int i=0; i < challengers.size(); i++)
		{
			double timeUsed = runHistory.getTotalRunCost() - initialTime;
			if( timeUsed > timeBound && i > 1)
			{
				log.info("Out of time for intensification timeBound {}, used: {}", timeBound, timeUsed );
				break;
			}
			challengeIncumbent(challengers.get(i));
		}
	}

	
	private void challengeIncumbent(ParamConfiguration challenger) {

		log.debug("Challenging Incumbent With {} ", challenger);
		//=== Perform run for incumbent unless it has the maximum #runs.
		if (runHistory.getTotalNumRunsOfConfig(incumbent) < MAX_RUNS_FOR_INCUMBENT){
			log.debug("Performing additional run with the incumbent");
			ProblemInstanceSeedPair pisp = runHistory.getRandomInstanceSeedWithFewestRunsFor(incumbent, instances, rand);
			RunConfig incumbentRunConfig = getRunConfig(pisp, cutoffTime,incumbent);
			evaluateRun(incumbentRunConfig);
		} else
		{
			log.debug("Too many incumbent runs, not performing additional run");
		}
		
		
		int N=1;
		while(true){

			/*
			 * Get all the Instance Seed Pairs, the incumbent has run on in a Set
			 * Remove all the instance Seed pairs the challenger has run on from the set.
			 */
			Set<ProblemInstanceSeedPair> sMissing = new HashSet<ProblemInstanceSeedPair>( runHistory.getAlgorithmInstanceSeedPairsRan(incumbent) );
			sMissing.removeAll( runHistory.getAlgorithmInstanceSeedPairsRan(challenger) );

			List<ProblemInstanceSeedPair> aMissing = new ArrayList<ProblemInstanceSeedPair>();
			aMissing.addAll(sMissing);
			
			//DO NOT SHUFFLE AS MATLAB DOESN'T
			int runsToMake = Math.min(N, aMissing.size());
			
			Collections.sort(aMissing);
			
			
			
			int[] permutations = SeedableRandomSingleton.getPermutation(aMissing.size(), 0);
			SeedableRandomSingleton.permuteList(aMissing, permutations);
			aMissing = aMissing.subList(0, runsToMake);
			
			

			if(log.isTraceEnabled())
			{
				for(ProblemInstanceSeedPair pisp : aMissing)
				{
					log.trace("Missing Problem Instance Seed Pair {}", pisp);
				}
			}
			
			
			
			
			log.trace("Permuting elements according to {}", Arrays.toString(permutations));
			
			
			double bound_inc = Double.POSITIVE_INFINITY;
			Set<ProblemInstance> missingInstances = null;
			Set<ProblemInstance> missingPlusCommon = null;
			if(config.adaptiveCapping)
			{
				missingInstances = new HashSet<ProblemInstance>();
				
				for(int i=0; i < runsToMake; i++)
				{
					//int index = permutations[i];
					missingInstances.add(aMissing.get(i).getInstance());
				}
				missingPlusCommon = new HashSet<ProblemInstance>();
				missingPlusCommon.addAll(missingInstances);
				Set<ProblemInstance> piCommon = runHistory.getInstancesRan(incumbent);
				piCommon.retainAll( runHistory.getInstancesRan( challenger ));
				missingPlusCommon.addAll(piCommon);
				
				
				bound_inc = runHistory.getEmpiricalCost(incumbent, missingPlusCommon, cutoffTime) + Math.pow(10, -3);
			}
			
			log.info("Performing up to {} runs for challenger up to a total bound of {} ", N, bound_inc);
			
			List<RunConfig> runsToEval = new ArrayList<RunConfig>(config.maxConcurrentAlgoExecs); 
			
			if(config.adaptiveCapping && incumbentImpossibleToBeat(challenger, aMissing.get(0), aMissing, missingPlusCommon, cutoffTime, bound_inc))
			{
				log.info("Challenger cannot beat incumbent scheduling empty run");
				runsToEval.add(getBoundedRunConfig(aMissing.get(0), 0, challenger));
			} else
			{
				for (int i = 0; i < runsToMake ; i++) {
					//Runs to make and aMissing should be the same size
					//int index = permutations[i];
					
					RunConfig runConfig;
					ProblemInstanceSeedPair pisp = aMissing.get(0);
					if(config.adaptiveCapping)
					{
						
						
						double capTime = computeCap(challenger, pisp, aMissing, missingPlusCommon, cutoffTime, bound_inc);
						if(capTime < cutoffTime)
						{
							if(capTime <= BEST_POSSIBLE_VALUE)
							{
								//If we are here abort runs
								break;
							}
							runConfig = getBoundedRunConfig(pisp, capTime, challenger);
						} else
						{
							runConfig = getRunConfig(pisp, cutoffTime, challenger);
						}
					} else
					{
						runConfig = getRunConfig(pisp, cutoffTime, challenger);
					}
					
					runsToEval.add(runConfig);
					
					sMissing.remove(pisp);
					aMissing.remove(0);
					if(runsToEval.size() == config.maxConcurrentAlgoExecs )
					{
						evaluateRun(runsToEval);
						runsToEval.clear();
					} 
				}
				
				
			}
			if(runsToEval.size() > 0)
			{
				evaluateRun(runsToEval);
				runsToEval.clear();
			}
			//
			
			
			//=== Get performance of incumbent and challenger on their common instances.
			Set<ProblemInstance> piCommon = runHistory.getInstancesRan(incumbent);
			piCommon.retainAll( runHistory.getInstancesRan( challenger ));
			
			
			
			double incCost = runHistory.getEmpiricalCost(incumbent, piCommon,cutoffTime);
			double chalCost = runHistory.getEmpiricalCost(challenger, piCommon, cutoffTime);
			

			Object args[] = {piCommon.size(), runHistory.getUniqueInstancesRan().size(), challenger.getFriendlyID(), chalCost, incumbent.getFriendlyID(), incCost  };
			log.info("Based on {} common runs on (up to) {} instances, challenger {} has a lower bound {} and incumbent {} has obj {}",args);
			
			//=== Decide whether to discard challenger, to make challenger incumbent, or to continue evaluating it more.		
			if (incCost + Math.pow(10, -6)  < chalCost){
				log.info("Challenger is worse aborting runs with challenger {}", challenger);
				break;
			} else if (sMissing.isEmpty())
			{	
				changeIncumbentTo(challenger);
				break;
			} else
			{
				
				N *= 2;
				log.trace("Increasing number of runs to: {}", N);
			}
		}
		
		
	}
	
	

	private static Object currentIncumbentCost;

	private void changeIncumbentTo(ParamConfiguration challenger) {
		// TODO Auto-generated method stub
		incumbent = challenger;
		updateIncumbentCost();
		log.info("Incumbent Changed to: {}", challenger);
	}

	private double computeCap(ParamConfiguration challenger, ProblemInstanceSeedPair pisp, List<ProblemInstanceSeedPair> aMissing, Set<ProblemInstance> instanceSet, double cutofftime, double bound_inc)
	{
		if(incumbentImpossibleToBeat(challenger, pisp, aMissing, instanceSet, cutofftime, bound_inc))
		{
			return UNCOMPETITIVE_CAPTIME;
		}
		return computeCapBinSearch(challenger, pisp, aMissing, instanceSet, cutofftime, bound_inc, BEST_POSSIBLE_VALUE, cutofftime);
	}
	
	private boolean incumbentImpossibleToBeat(ParamConfiguration challenger, ProblemInstanceSeedPair pisp, List<ProblemInstanceSeedPair> aMissing, Set<ProblemInstance> instanceSet, double cutofftime, double bound_inc)
	{
		return (predictedPerformance(challenger, pisp, aMissing, instanceSet, cutofftime, BEST_POSSIBLE_VALUE) > bound_inc);
	}
	
	private final double BEST_POSSIBLE_VALUE = 0;
	private final double UNCOMPETITIVE_CAPTIME = 0;
	private double computeCapBinSearch(ParamConfiguration challenger, ProblemInstanceSeedPair pisp, List<ProblemInstanceSeedPair> aMissing, Set<ProblemInstance> missingInstances, double cutofftime, double bound_inc,  double lowerBound, double upperBound)
	{
	
		
		
		if(upperBound - lowerBound < Math.pow(10,-6))
		{
			double capTime = upperBound + Math.pow(10, -3);
			return capTime * config.capSlack + config.capAddSlack;
		}
		
		
		
		double mean = (upperBound + lowerBound)/2;
		
			
		
		
		double predictedPerformance = predictedPerformance(challenger, pisp, aMissing, missingInstances, cutofftime, mean); 
		if(predictedPerformance < bound_inc)
		{
			return computeCapBinSearch(challenger, pisp, aMissing, missingInstances, cutofftime, bound_inc, mean, upperBound);
		} else
		{
			return computeCapBinSearch(challenger, pisp, aMissing, missingInstances, cutofftime, bound_inc, lowerBound, mean);
		}
	}

	
	private double predictedPerformance(ParamConfiguration challenger, ProblemInstanceSeedPair pisp, List<ProblemInstanceSeedPair> aMissing, Set<ProblemInstance> instanceSet, double cutofftime, double probe)
	{
		
		Map<ProblemInstance, Map<Long, Double>> hallucinatedValues = new HashMap<ProblemInstance, Map<Long, Double>>();
		
		for(ProblemInstanceSeedPair missingPisp : aMissing)
		{
			ProblemInstance pi = missingPisp.getInstance();
		
			if(hallucinatedValues.get(pi) == null)
			{
				hallucinatedValues.put(pi, new HashMap<Long, Double>());
			}
			
			double hallucinatedValue = BEST_POSSIBLE_VALUE;
			
			if(pisp.equals(missingPisp))
			{
				hallucinatedValue = probe;
			}
			
			
			
			hallucinatedValues.get(pi).put(missingPisp.getSeed(), hallucinatedValue);
			
		}
		return runHistory.getEmpiricalCost(challenger, instanceSet, cutofftime, hallucinatedValues);
	}
	private  void updateIncumbentCost() {
		
		currentIncumbentCost = runHistory.getEmpiricalCost(incumbent, new HashSet<ProblemInstance>(instances), cutoffTime);
		log.debug("Incumbent Cost now: {}", currentIncumbentCost);
	}


	
	/**
	 * Gets a runConfiguration for the given parameters
	 * @param pisp
	 * @param cutofftime
	 * @param configuration
	 * @return
	 */
	protected RunConfig getRunConfig(ProblemInstanceSeedPair pisp, double cutofftime, ParamConfiguration configuration)
	{
		
		RunConfig rc =  new RunConfig(pisp, cutofftime, configuration );
		log.trace("RunConfig generated {}", rc);
		return rc;
	}
	
	
	private RunConfig getBoundedRunConfig(
			ProblemInstanceSeedPair pisp, double capTime,
			ParamConfiguration challenger) {
		
		RunConfig rc =  new RunConfig(pisp, capTime, challenger, true );
		log.trace("RunConfig generated {}", rc);
		return rc;
	}
	
	
	
	/**
	 * 
	 * @return the input parameter (unmodified, simply for syntactic convience)
	 */
	protected List<AlgorithmRun> updateRunHistory(List<AlgorithmRun> runs)
	{
		for(AlgorithmRun run : runs)
		{
			try {
				runHistory.append(run);
			} catch (DuplicateRunException e) {
				//We are trying to log a duplicate run
				throw new IllegalStateException(e);
			}
		}
		return runs;
	}
	
	/**
	 * Evaluates a single run, and updates our runHistory
	 * @param runConfig
	 * @return
	 */
	protected List<AlgorithmRun> evaluateRun(RunConfig runConfig)
	{
		log.info("Evaluating run: {}", runConfig);
		return updateRunHistory(algoEval.evaluateRun(runConfig));
	}
	
	/**
	 * Evaluates a list of runs and updates our runHistory
	 * @param runConfigs
	 * @return
	 */
	protected List<AlgorithmRun> evaluateRun(List<RunConfig> runConfigs)
	{
		int i=0;
		log.info("Evaluating {} runs", runConfigs.size());
		for(RunConfig rc : runConfigs)
		{
			log.info("Run {}: {} ",i++, rc);
		}
		return updateRunHistory(algoEval.evaluateRun(runConfigs));
	}


	public double getTunerTime() {

		return runHistory.getTotalRunCost();
	}


	public double getEmpericalPerformance(ParamConfiguration config) {
		// TODO Auto-generated method stub
		Set<ProblemInstance> pis = new HashSet<ProblemInstance>();
		pis.addAll(instances);
		return runHistory.getEmpiricalCost(config, pis, cutoffTime);
	}
	
}
