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

import ca.ubc.cs.beta.aclib.algorithmrun.AlgorithmRun;
import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration;
import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration.StringFormat;
import ca.ubc.cs.beta.aclib.exceptions.DuplicateRunException;
import ca.ubc.cs.beta.aclib.misc.random.SeedableRandomSingleton;
import ca.ubc.cs.beta.aclib.misc.watch.AutoStartStopWatch;
import ca.ubc.cs.beta.aclib.misc.watch.StopWatch;
import ca.ubc.cs.beta.aclib.options.SMACOptions;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstanceSeedPair;
import ca.ubc.cs.beta.aclib.runconfig.RunConfig;
import ca.ubc.cs.beta.aclib.runhistory.NewRunHistory;
import ca.ubc.cs.beta.aclib.runhistory.RunHistory;
import ca.ubc.cs.beta.aclib.seedgenerator.InstanceSeedGenerator;
import ca.ubc.cs.beta.aclib.state.RandomPoolType;
import ca.ubc.cs.beta.aclib.state.StateDeserializer;
import ca.ubc.cs.beta.aclib.state.StateFactory;
import ca.ubc.cs.beta.aclib.state.StateSerializer;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.smac.ac.exceptions.OutOfTimeException;

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
	protected final SMACOptions options;
	
	protected final Logger log = LoggerFactory.getLogger(this.getClass());
		
	private final StateFactory stateFactory;
	
	private final FileWriter fout;
	
	private int iteration = 0;
	protected ParamConfiguration incumbent = null;
	
	private final int MAX_RUNS_FOR_INCUMBENT;
	
	public AbstractAlgorithmFramework(SMACOptions smacOptions, List<ProblemInstance> instances,List<ProblemInstance> testInstances, TargetAlgorithmEvaluator algoEval, StateFactory stateFactory, ParamConfigurationSpace configSpace, InstanceSeedGenerator instanceSeedGen, Random rand)
	{
		this.instances = instances;
		this.testInstances = testInstances;
		this.cutoffTime = smacOptions.scenarioConfig.cutoffTime;
		this.options = smacOptions;
		this.rand = rand;		
		this.algoEval = algoEval;
		this.stateFactory = stateFactory;
		this.configSpace = configSpace;
		this.runHistory = new NewRunHistory(instanceSeedGen,smacOptions.scenarioConfig.intraInstanceObj, smacOptions.scenarioConfig.interInstanceObj, smacOptions.scenarioConfig.runObj);
		
		long time = System.currentTimeMillis();
		Date d = new Date(time);
		DateFormat df = DateFormat.getDateTimeInstance();	
		log.info("Automatic Configuration Start Time is {}", df.format(d));				
		
		//=== Clamp # runs for incumbent to # of available seeds.
		if(instanceSeedGen.getInitialInstanceSeedCount() < options.maxIncumbentRuns)
		{
			log.info("Clamping number of runs to {} due to lack of instance/seeds pairs", instanceSeedGen.getInitialInstanceSeedCount());
			MAX_RUNS_FOR_INCUMBENT = instanceSeedGen.getInitialInstanceSeedCount();
		}  else
		{
			MAX_RUNS_FOR_INCUMBENT=smacOptions.maxIncumbentRuns;
			log.info("Maximimum Number of Runs for the Incumbent Initialized to {}:", MAX_RUNS_FOR_INCUMBENT);
		}
		
		//=== Initialize trajectory file.
		try {
			String outputFileName = options.scenarioConfig.outputDirectory + File.separator + options.runGroupName + File.separator +"traj-run-" + options.seed + ".txt";
			this.fout = new FileWriter(new File(outputFileName));
			log.info("Trajectory File Writing To: {}", outputFileName);
			fout.write(options.runGroupName + ", " + options.seed + "\n");			
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
	protected boolean have_to_stop(int iteration){
		return have_to_stop(iteration, 0);
	}
	
	/**
	 * Function that determines whether we should stop processing or not
	 * @param iteration - number of iterations we have done
	 * @param nextRunTime - the time the next run takes
	 * @return
	 */
	protected boolean have_to_stop(int iteration, double nextRunTime)
	{
		if(getTunerTime() + nextRunTime > options.scenarioConfig.tunerTimeout)
		{
			log.info("Run cost {} greater than tuner timeout {}",runHistory.getTotalRunCost() + nextRunTime, options.scenarioConfig.tunerTimeout);
			return true;
		}
		
		if(iteration > options.numIteratations)
		{
			log.info("Iteration {} greater than number permitted {}", iteration, options.numIteratations);
			return true;
		}
		
		if(runHistory.getAlgorithmRunData().size() >= options.totalNumRunLimit)
		{
			log.info("Number of runs {} is greater than the number permitted {}",runHistory.getAlgorithmRunData().size(), options.totalNumRunLimit);
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
			log.info("Incument currently is: {} {} ", incumbent, incumbent.getFormattedParamString());
		}
		ThreadMXBean b = ManagementFactory.getThreadMXBean();
		double wallTime = (System.currentTimeMillis() - applicationStartTime) / 1000.0;
		double cpuTime = runHistory.getTotalRunCost();
		
		double acTime = b.getCurrentThreadCpuTime() / 1000.0 / 1000 / 1000;
		Object[] arr2 = { iteration, wallTime , options.runtimeLimit - wallTime , cpuTime,options.scenarioConfig.tunerTimeout - cpuTime,   b.getCurrentThreadCpuTime() / 1000.0 / 1000 / 1000, b.getCurrentThreadUserTime() / 1000.0 / 1000 / 1000 , Runtime.getRuntime().maxMemory() / 1024.0 / 1024, Runtime.getRuntime().totalMemory() / 1024.0 / 1024, Runtime.getRuntime().freeMemory() / 1024.0 / 1024 };
		
		

		
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
					//Create initial row
					writeIncumbent(0, Double.MAX_VALUE, -1,1,0, incumbent.getFormattedParamString(StringFormat.STATEFILE_SYNTAX));
					evaluateRun(incumbentRunConfig);
					logIncumbent(iteration);
				} else
				{
					//We are restoring state
				}
				
				/**
				 * Main Loop
				 */
				
				try{
					while(!have_to_stop(iteration+1))
					{
						if(shouldSave()) saveState();
						
						runHistory.incrementIteration();
						iteration++;
						log.info("Starting Iteration {}", iteration);
						StopWatch t = new AutoStartStopWatch();
						learnModel(runHistory, configSpace);
						
						
						
						ArrayList<ParamConfiguration> challengers = new ArrayList<ParamConfiguration>();
						challengers.addAll(selectConfigurations());
						

						double learnModelTime = t.stop()/1000.0;
						double intensifyTime = Math.ceil( learnModelTime) * (options.intensificationPercentage / (1.0-options.intensificationPercentage));
						intensify(challengers, intensifyTime);
						
						logIncumbent(iteration);
					} 
				} catch(OutOfTimeException e){
					// We're out of time.
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
					log.error("SMAC has encounted an exception, and encountered another exception while trying to save the local state. NOTE: THIS PARTICULAR ERROR DID NOT CAUSE SMAC TO FAIL, the original culprit follows further below. (This second error is potentially another / seperate issue, or a disk failure of some kind.) When submitting bug/error reports, please include enough context for *BOTH* exceptions \n  ", e2);
					throw e;
				}
				throw e;
			}
		} finally
		{
			try {
				fout.close();
			} catch (IOException e) {
				log.error("Trying to close Trajectory File failed with exception {}", e);
			}
		}
	}
	
	protected boolean shouldSave() 
	{
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



	protected void learnModel(RunHistory runHistory, ParamConfigurationSpace configSpace) {
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
		log.info("Calling intensify with {} challenger(s)", challengers.size());
		for(int i=0; i < challengers.size(); i++)
		{
			double timeUsed = runHistory.getTotalRunCost() - initialTime;
			if( timeUsed > timeBound && i > 1)
			{
				log.info("Out of time for intensification timeBound {}; used: {}", timeBound, timeUsed );
				break;
			}
			challengeIncumbent(challengers.get(i));
		}
	}

	
	private void challengeIncumbent(ParamConfiguration challenger) {

		log.debug("Challenging incumbent with configuration {} ", challenger);
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
			 * Get all the <instance,seed> pairs the incumbent has run (get them in a set).
			 * Then remove all the <instance,seed> pairs the challenger has run on from that set.
			 */
			Set<ProblemInstanceSeedPair> sMissing = new HashSet<ProblemInstanceSeedPair>( runHistory.getAlgorithmInstanceSeedPairsRan(incumbent) );
			sMissing.removeAll( runHistory.getAlgorithmInstanceSeedPairsRan(challenger) );

			List<ProblemInstanceSeedPair> aMissing = new ArrayList<ProblemInstanceSeedPair>();
			aMissing.addAll(sMissing);
			
			//DO NOT SHUFFLE AS MATLAB DOESN'T
			int runsToMake = Math.min(N, aMissing.size());
			
			Collections.sort(aMissing);
			
			
			//=== Sort aMissing in the order that we want to evaluate <instance,seed> pairs.
			int[] permutations = SeedableRandomSingleton.getPermutation(aMissing.size(), 0);
			SeedableRandomSingleton.permuteList(aMissing, permutations);
			aMissing = aMissing.subList(0, runsToMake);
			
			
			//=== Only bother with this loop if tracing is enabled (facilitates stepping through the code).
			if(log.isTraceEnabled())
			{
				for(ProblemInstanceSeedPair pisp : aMissing)
				{
					log.trace("Missing Problem Instance Seed Pair {}", pisp);
				}
			}
			
			
			
			
			log.trace("Permuting elements according to {}", Arrays.toString(permutations));
			
			//TODO: refactor adaptive capping.
			double bound_inc = Double.POSITIVE_INFINITY;
			Set<ProblemInstance> missingInstances = null;
			Set<ProblemInstance> missingPlusCommon = null;
			if(options.adaptiveCapping)
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
			
			List<RunConfig> runsToEval = new ArrayList<RunConfig>(options.maxConcurrentAlgoExecs); 
			
			if(options.adaptiveCapping && incumbentImpossibleToBeat(challenger, aMissing.get(0), aMissing, missingPlusCommon, cutoffTime, bound_inc))
			{
				log.info("Challenger cannot beat incumbent => scheduling empty run");
				runsToEval.add(getBoundedRunConfig(aMissing.get(0), 0, challenger));
				if (runsToMake != 1){
					throw new IllegalStateException("Error in empty run scheduling: empty runs should only be scheduled in first iteration of intensify.");
				}
			} else
			{
				for (int i = 0; i < runsToMake ; i++) {
					//Runs to make and aMissing should be the same size
					//int index = permutations[i];
					
					RunConfig runConfig;
					ProblemInstanceSeedPair pisp = aMissing.get(0);
					if(options.adaptiveCapping)
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
					if(runsToEval.size() == options.maxConcurrentAlgoExecs )
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
				if(chalCost < incCost - Math.pow(10,-6))
				{
					changeIncumbentTo(challenger);
				} else
				{
					log.info("Challenger has all the runs of the incumbent, but did not (significantly beat it");
				}
				
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
		return (lowerBoundOnEmpiricalPerformance(challenger, pisp, aMissing, instanceSet, cutofftime, BEST_POSSIBLE_VALUE) > bound_inc);
	}
	
	private final double BEST_POSSIBLE_VALUE = 0;
	private final double UNCOMPETITIVE_CAPTIME = 0;
	private double computeCapBinSearch(ParamConfiguration challenger, ProblemInstanceSeedPair pisp, List<ProblemInstanceSeedPair> aMissing, Set<ProblemInstance> missingInstances, double cutofftime, double bound_inc,  double lowerBound, double upperBound)
	{
	
		
		
		if(upperBound - lowerBound < Math.pow(10,-6))
		{
			double capTime = upperBound + Math.pow(10, -3);
			return capTime * options.capSlack + options.capAddSlack;
		}
		
		
		
		double mean = (upperBound + lowerBound)/2;
		
			
		
		
		double predictedPerformance = lowerBoundOnEmpiricalPerformance(challenger, pisp, aMissing, missingInstances, cutofftime, mean); 
		if(predictedPerformance < bound_inc)
		{
			return computeCapBinSearch(challenger, pisp, aMissing, missingInstances, cutofftime, bound_inc, mean, upperBound);
		} else
		{
			return computeCapBinSearch(challenger, pisp, aMissing, missingInstances, cutofftime, bound_inc, lowerBound, mean);
		}
	}

	
	private double lowerBoundOnEmpiricalPerformance(ParamConfiguration challenger, ProblemInstanceSeedPair pisp, List<ProblemInstanceSeedPair> aMissing, Set<ProblemInstance> instanceSet, double cutofftime, double probe)
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
				if (have_to_stop(iteration, run.getRuntime())){
					throw new OutOfTimeException();
				}
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
		return evaluateRun(Collections.singletonList(runConfig));
	}
	
	/**
	 * Evaluates a list of runs and updates our runHistory
	 * @param runConfigs
	 * @return
	 */
	protected List<AlgorithmRun> evaluateRun(List<RunConfig> runConfigs)
	{
		int i=0;
		log.info("Evaluating {} run(s)", runConfigs.size());
		if(log.isDebugEnabled())
		{
			for(RunConfig rc : runConfigs)
			{
				log.debug("Run {}: {} ",i++, rc);
			}
		}
		
		List<AlgorithmRun> completedRuns = algoEval.evaluateRun(runConfigs);
		updateRunHistory(completedRuns);
		return completedRuns;
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
