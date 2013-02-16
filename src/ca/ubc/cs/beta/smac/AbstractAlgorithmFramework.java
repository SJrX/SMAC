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
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.cs.beta.aclib.algorithmrun.AlgorithmRun;
import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration;
import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration.StringFormat;
import ca.ubc.cs.beta.aclib.exceptions.DeveloperMadeABooBooException;
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
import ca.ubc.cs.beta.aclib.trajectoryfile.TrajectoryFileEntry;

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
	protected final SMACOptions options;
	
	protected final Logger log = LoggerFactory.getLogger(this.getClass());
		
	private final StateFactory stateFactory;
	
	private final FileWriter trajectoryFileWriter;
	private final FileWriter trajectoryFileWriterCSV;
	
	private int iteration = 0;
	protected ParamConfiguration incumbent = null;
	
	private final int MAX_RUNS_FOR_INCUMBENT;
	
	
	private final List<TrajectoryFileEntry> tfes = new ArrayList<TrajectoryFileEntry>();
	
	
	private double sumOfWallClockTime = 0;
	private double sumOfReportedAlgorithmRunTime = 0;

	protected final InstanceSeedGenerator instanceSeedGen;
	
	private final ParamConfiguration initialIncumbent;
	
	public AbstractAlgorithmFramework(SMACOptions smacOptions, List<ProblemInstance> instances,List<ProblemInstance> testInstances, TargetAlgorithmEvaluator algoEval, StateFactory stateFactory, ParamConfigurationSpace configSpace, InstanceSeedGenerator instanceSeedGen, Random rand, ParamConfiguration initialIncumbent)
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
		this.instanceSeedGen = instanceSeedGen;
		this.initialIncumbent = initialIncumbent;
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
			log.info("Maximimum Number of Runs for the Incumbent Initialized to {}", MAX_RUNS_FOR_INCUMBENT);
		}
		
		//=== Initialize trajectory file.
		try {
			String outputFileName = options.scenarioConfig.outputDirectory + File.separator + options.runGroupName + File.separator +"traj-run-" + options.numRun + ".txt";
			this.trajectoryFileWriter = new FileWriter(new File(outputFileName));
			log.info("Trajectory File Writing To: {}", outputFileName);
			String outputFileNameCSV = options.scenarioConfig.outputDirectory + File.separator + options.runGroupName + File.separator +"traj-run-" + options.numRun + ".csv";
			this.trajectoryFileWriterCSV = new FileWriter(new File(outputFileNameCSV));
			log.info("Trajectory File Writing To: {}", outputFileNameCSV);
			
			
			
			trajectoryFileWriter.write(options.runGroupName + ", " + options.numRun + "\n");
			trajectoryFileWriterCSV.write(options.runGroupName + ", " + options.numRun + "\n");		
		} catch (IOException e) {
			
			throw new IllegalStateException("Could not create trajectory file: " , e);
		}
		
	}
	
	
	public ParamConfiguration getInitialIncumbent()
	{
		return initialIncumbent;
	}
	
	
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
	
	
	//Last runtime that we saw
	double unaccountedRunTime = 0;
	boolean outOfTime = false;
	/**
	 * Function that determines whether we should stop processing or not
	 * @param iteration - number of iterations we have done
	 * @param nextRunTime - the time the next run takes
	 * @return
	 */
	protected boolean have_to_stop(int iteration, double nextRunTime)
	{
		outOfTime = true;
		if(getTunerTime() + nextRunTime > options.scenarioConfig.tunerTimeout)
		{
			unaccountedRunTime = nextRunTime;
			log.info("Run cost {} greater than tuner timeout {}",runHistory.getTotalRunCost() + nextRunTime, options.scenarioConfig.tunerTimeout);
			return true;
		} else
		{
			unaccountedRunTime = 0;
		}
		
		if(iteration > options.numIteratations)
		{
			log.info("Iteration {} greater than number permitted {}", iteration, options.numIteratations);
			return true;
		}
		
		if(runHistory.getAlgorithmRunData().size() >= options.totalNumRunsLimit)
		{
			log.info("Number of runs {} is greater than the number permitted {}",runHistory.getAlgorithmRunData().size(), options.totalNumRunsLimit);
			return true;
		}
		outOfTime = false;
		return false;
	}
	
	
	public void logIncumbent()
	{
		logIncumbent(-1);
	}
	
	/**
	 * Returns the total CPU Time for this JVM
	 * 
	 * @return cpu time for this jvm if enabled&supported 0 otherwise
	 */
	public long getCPUTime()
	{
		try 
		{
			ThreadMXBean b = ManagementFactory.getThreadMXBean();
		
			long cpuTime = 0;
			for(long threadID : b.getAllThreadIds())
			{
				long threadTime =  b.getThreadCpuTime(threadID);
				if(threadTime == -1)
				{ //This JVM doesn't have CPU time enabled
			      //We check every iteration because some threads (the current thread may give us something other than -1)
					
					log.debug("JVM does not have CPU Time enabled");
					return 0; 
				}
				
				cpuTime += threadTime;
			}
			return cpuTime;
		} catch(UnsupportedOperationException e)
		{
			log.debug("JVM does not support CPU Time measurements");
			return 0;
		}
		
	}
	
	/**
	 * Returns the total CPU Time for this JVM
	 * 
	 * @return cpu user time for this jvm if enabled&supported 0 otherwise */
	public long getCPUUserTime()
	{
		try
		{
			ThreadMXBean b = ManagementFactory.getThreadMXBean();
			
			long cpuTime = 0;
			for(long threadID : b.getAllThreadIds())
			{
				long threadTime =  b.getThreadUserTime(threadID);
				if(threadTime == -1)
				{ //This JVM doesn't have CPU time enabled
				      //We check every iteration because some threads (the current thread may give us something other than -1)
					log.debug("JVM does not have CPU Time enabled");
					return 0; 
				}
				cpuTime += threadTime;
			}
	
			return cpuTime;
		
		} catch(UnsupportedOperationException e)
		{
			log.debug("JVM does not support CPU Time measurements");
			return 0;
		}
	}
	
	
	/**
	 * Logs the incumbent 
	 * @param iteration
	 */
	public void logIncumbent(int iteration)
	{
		
		if (iteration > 0)
		{
			Object[] arr = {iteration, runHistory.getThetaIdx(incumbent), incumbent};		
			log.info("At end of iteration {}, incumbent is {} ({}) ",arr);
		} else
		{
			log.info("Incumbent currently is: {} ({}) ", runHistory.getThetaIdx(incumbent), incumbent);
		}				
		writeIncumbent();
		
	}
	private String lastLogMessage = "No statistics logged";
	protected void logRuntimeStatistics()
	{
		
		double wallTime = (System.currentTimeMillis() - applicationStartTime) / 1000.0;
		double tunerTime = getTunerTime();
		
		Object[] arr = { iteration,
				runHistory.getThetaIdx(incumbent) + " (" + incumbent +")",
				runHistory.getTotalNumRunsOfConfig(incumbent),
				runHistory.getInstancesRan(incumbent).size(),
				runHistory.getUniqueParamConfigurations().size(),
				runHistory.getEmpiricalCost(incumbent, runHistory.getUniqueInstancesRan(), this.cutoffTime),
				runHistory.getAlgorithmRuns().size(), 
				wallTime ,
				options.runtimeLimit - wallTime ,
				tunerTime,
				options.scenarioConfig.tunerTimeout - tunerTime,
				runHistory.getTotalRunCost(),
				getCPUTime() / 1000.0 / 1000 / 1000,
				getCPUUserTime() / 1000.0 / 1000 / 1000 ,
				sumOfReportedAlgorithmRunTime,
				sumOfWallClockTime,
				Runtime.getRuntime().maxMemory() / 1024.0 / 1024,
				Runtime.getRuntime().totalMemory() / 1024.0 / 1024,
				Runtime.getRuntime().freeMemory() / 1024.0 / 1024 };
		
		lastLogMessage = "*****Runtime Statistics*****\n" +
				" Iteration: " + arr[0]+
				"\n Incumbent ID: "+ arr[1]+
				"\n Number of Runs for Incumbent: " + arr[2] +
				"\n Number of Instances for Incumbent: " + arr[3]+
				"\n Number of Configurations Run: " + arr[4]+ 
				"\n Performance of the Incumbent: " + arr[5]+
				"\n Total Number of runs performed: " + arr[6]+ 
				"\n Wallclock time: "+ arr[7] + " s" +
				"\n Wallclock time remaining: "+ arr[8] +" s" +
				"\n Configuration time budget used: "+ arr[9] +" s" +
				"\n Configuration time budget remaining: "+ arr[10]+" s" +
				"\n Sum of Target Algorithm Execution Times (treating minimum value as 0.1): "+arr[11] +" s" + 
				"\n CPU time of Configurator: "+arr[12]+" s" +
				"\n User time of Configurator: "+arr[13]+" s" +
				"\n Total Reported Algorithm Runtime: " + arr[14] + " s" + 
				"\n Sum of Measured Wallclock Runtime: " + arr[15] + " s" +
				"\n Max Memory: "+arr[16]+" MB" +
				"\n Total Java Memory: "+arr[17]+" MB" +
				"\n Free Java Memory: "+arr[18]+" MB";
		
		log.info(lastLogMessage);
		
	}
	
	
	public void afterValidationStatistics()
	{
		log.info(lastLogMessage);
		
	}
	
	
	private void writeIncumbent()
	{
		writeIncumbent(getTunerTime()+unaccountedRunTime,runHistory.getEmpiricalCost(incumbent, runHistory.getUniqueInstancesRan(), this.cutoffTime));
	}
	
	private double lastEmpericalPerformance = Double.NaN;
	private ParamConfiguration lastIncumbent = null;
	
	/**
	 * Writes the incumbent to the trajectory file
	 * @param totalTime
	 * @param empericalPerformance
	 * @param stdDevInc
	 * @param thetaIdxInc
	 * @param acTime
	 * @param paramString
	 */
	private void writeIncumbent(double tunerTime, double empericalPerformance)
	{
		
		if(incumbent.equals(lastIncumbent) && lastEmpericalPerformance == empericalPerformance && !outOfTime)
		{
			return;
		} else
		{
			lastEmpericalPerformance = empericalPerformance;
			lastIncumbent = incumbent;
		}
		
		int thetaIdxInc = runHistory.getThetaIdx(incumbent);
		double acTime = getCPUTime() / 1000.0 / 1000 / 1000;
		
		//-1 should be the variance but is allegedly the sqrt in compareChallengersagainstIncumbents.m and then is just set to -1.
		double stdDevInc = -1;
		
		String paramString = incumbent.getFormattedParamString(StringFormat.STATEFILE_SYNTAX);
		
		TrajectoryFileEntry tfe = new TrajectoryFileEntry(incumbent, tunerTime, empericalPerformance, acTime);
		
		this.tfes.add(tfe);
		
		String outLine = tunerTime + ", " + empericalPerformance + ", " + stdDevInc + ", " + thetaIdxInc + ", " + acTime + ", " + paramString +"\n";
		try 
		{
			trajectoryFileWriter.write(outLine);
			trajectoryFileWriter.flush();
			trajectoryFileWriterCSV.write(outLine);
			trajectoryFileWriterCSV.flush();
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
					incumbent = initialIncumbent;
					log.info("Initial Incumbent set as Incumbent: {}", incumbent);
					
					iteration = 0;
					
					boolean firstRun = true;
					int N= options.initialIncumbentRuns;
					
					N = Math.min(N, instances.size());
					N = Math.min(N, options.maxIncumbentRuns);
					log.debug("Scheduling initial configuration for {} runs",N);
					for(int i=0; i <N; i++)
					{
						
						/**
						 * Evaluate initial Configuration
						 */
						ProblemInstanceSeedPair pisp = runHistory.getRandomInstanceSeedWithFewestRunsFor(incumbent, instances, rand);
						log.trace("New Problem Instance Seed Pair generated {}", pisp);
						RunConfig incumbentRunConfig = getRunConfig(pisp, cutoffTime,incumbent);
						//Create initial row
						writeIncumbent(0, Double.MAX_VALUE);
						try { 
						evaluateRun(incumbentRunConfig);
						
					
						} catch(OutOfTimeException e)
						{
							log.warn("Ran out of time while evaluating the initial configuration on the first run, this is most likely a configuration error");
							//Ignore this exception
							//Force the incumbent to be logged in RunHistory and then we will timeout next
							try {
								runHistory.append(e.getAlgorithmRun());
							
							} catch (DuplicateRunException e1) {

								throw new DeveloperMadeABooBooException(e1);
							}
						}
						
					}
					
					logConfiguration("New Incumbent", incumbent);
					logIncumbent(iteration);
					logRuntimeStatistics();
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
						log.info("Model Learn Time: {} (s)", t.time() / 1000.0);
						
						ArrayList<ParamConfiguration> challengers = new ArrayList<ParamConfiguration>();
						challengers.addAll(selectConfigurations());
						

						double learnModelTime = t.stop()/1000.0;
						
						double intensifyTime = Math.ceil( learnModelTime) * (options.intensificationPercentage / (1.0-options.intensificationPercentage));
						
						intensify(challengers, intensifyTime);
						
						logIncumbent(iteration);
						logRuntimeStatistics();
					} 
				} catch(OutOfTimeException e){
					// We're out of time.
					logIncumbent(iteration);
					logRuntimeStatistics();
				}
				
				
				saveState("it", true);
				
				log.info("SMAC Completed");
				
				if(options.cleanOldStatesOnSuccess)
				{
					log.info("Cleaning old states");
					stateFactory.purgePreviousStates();
				}
				
			} catch(RuntimeException e)
			{
				try{
					saveState("CRASH",true);
				} catch(RuntimeException e2)
				{
					log.error("SMAC has encountered an exception, and encountered another exception while trying to save the local state. NOTE: THIS PARTICULAR ERROR DID NOT CAUSE SMAC TO FAIL, the original culprit follows further below. (This second error is potentially another / seperate issue, or a disk failure of some kind.) When submitting bug/error reports, please include enough context for *BOTH* exceptions \n  ", e2);
					throw e;
				}
				throw e;
			}
		} finally
		{
			try {
				trajectoryFileWriter.close();
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

	
	public void logIncumbentPerformance(SortedMap<TrajectoryFileEntry, Double> tfePerformance)
	{
		TrajectoryFileEntry tfe = null;
		double testSetPerformance = Double.POSITIVE_INFINITY;
		
		//=== We want the last TFE
		
		ParamConfiguration lastIncumbent = null;
		double lastEmpericalPerformance = Double.POSITIVE_INFINITY;
		
		double lastTestSetPerformance = Double.POSITIVE_INFINITY;
		for(Entry<TrajectoryFileEntry, Double> ents : tfePerformance.entrySet())
		{
			
			
			tfe = ents.getKey();
			double empericalPerformance = tfe.getEmpericalPerformance();
			
			testSetPerformance = ents.getValue();
			double tunerTime = tfe.getTunerTime();
			ParamConfiguration formerIncumbent = tfe.getConfiguration();
			
			
			if(formerIncumbent.equals(lastIncumbent) && empericalPerformance == lastEmpericalPerformance && lastTestSetPerformance == testSetPerformance)
			{
				continue;
			} else
			{
				lastIncumbent = formerIncumbent;
				lastEmpericalPerformance = empericalPerformance;
				lastTestSetPerformance = testSetPerformance;
			}
			
			
			
			if(Double.isInfinite(testSetPerformance))
			{
				Object[] args2 = {runHistory.getThetaIdx(formerIncumbent), formerIncumbent, tunerTime, empericalPerformance }; 
				log.info("Total Objective of Incumbent {} ({}) at time {} on training set: {}", args2 );
			} else
			{
				Object[] args2 = {runHistory.getThetaIdx(formerIncumbent), formerIncumbent, tunerTime, empericalPerformance, testSetPerformance };
				log.info("Total Objective of Incumbent {} ({}) at time {} on training set: {}; on test set: {}", args2 );
			}
			
			
		}
		
	}
	
	/**
	 * 
	 * @param tfePerformance
	 */
	public void logSMACResult(SortedMap<TrajectoryFileEntry, Double> tfePerformance)
	{
		
		

		TrajectoryFileEntry tfe = null;
		double testSetPerformance = Double.POSITIVE_INFINITY;
		
		//=== We want the last TFE
		tfe = tfePerformance.lastKey();
		testSetPerformance = tfePerformance.get(tfe);
		
		if(tfe != null)
		{
			if(!tfe.getConfiguration().equals(incumbent))
			{
				throw new IllegalStateException("Last TFE should be Incumbent");
			}
		}
		
		
		ProblemInstanceSeedPair pisp =  runHistory.getAlgorithmInstanceSeedPairsRan(incumbent).iterator().next();
	
		RunConfig runConfig = new RunConfig(pisp, cutoffTime, incumbent);
	
		String cmd = algoEval.getManualCallString(runConfig);
		Object[] args = {runHistory.getThetaIdx(incumbent), incumbent, cmd };
	

		log.info("**********************************************");
		
		if(Double.isInfinite(testSetPerformance))
		{
			Object[] args2 = { runHistory.getThetaIdx(incumbent), incumbent, runHistory.getEmpiricalCost(incumbent, runHistory.getUniqueInstancesRan(), cutoffTime) }; 
			log.info("Total Objective of Final Incumbent {} ({}) on training set: {}", args2 );
		} else
		{
			Object[] args2 = { runHistory.getThetaIdx(incumbent), incumbent,runHistory.getEmpiricalCost(incumbent, runHistory.getUniqueInstancesRan(), cutoffTime), testSetPerformance };
			log.info("Total Objective of Final Incumbent {} ({}) on training set: {}; on test set: {}", args2 );
		}
		
		log.info("Sample Call for Final Incumbent {} ({}) \n{} ",args);
		log.info("Complete Configuration (no inactive conditionals):{}", incumbent.getFormattedParamString(StringFormat.STATEFILE_SYNTAX_NO_INACTIVE));
		log.info("Complete Configuration (including inactive conditionals):{}", incumbent.getFormattedParamString(StringFormat.STATEFILE_SYNTAX));
		
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
	 * @param timeBound  - Amount of time we are allowed to run against (seconds)
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
				log.info("Out of time for intensification timeBound: {} (s); used: {}  (s)", timeBound, timeUsed );
				break;
			} else
			{
				
				log.info("Intensification timeBound: {} (s); used: {}  (s)", timeBound, timeUsed);
			}
			challengeIncumbent(challengers.get(i));
		}
	}

	
	private void challengeIncumbent(ParamConfiguration challenger) {
		//=== Perform run for incumbent unless it has the maximum #runs.
		if (runHistory.getTotalNumRunsOfConfig(incumbent) < MAX_RUNS_FOR_INCUMBENT){
			log.debug("Performing additional run with the incumbent ");
			ProblemInstanceSeedPair pisp = runHistory.getRandomInstanceSeedWithFewestRunsFor(incumbent, instances, rand);
			RunConfig incumbentRunConfig = getRunConfig(pisp, cutoffTime,incumbent);
			evaluateRun(incumbentRunConfig);
		} else
		{
			log.debug("Already have performed max runs ({}) for incumbent" , MAX_RUNS_FOR_INCUMBENT);
		}
		
		if(challenger.equals(incumbent))
		{
			Object[] args = { runHistory.getThetaIdx(challenger), challenger,  runHistory.getThetaIdx(incumbent), incumbent };
			log.info("Challenger {} ({}) is equal to the incumbent {} ({}); not evaluating it further ", args);
			return;
		}
		
		int N=options.initialChallengeRuns;
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
			if (runsToMake == 0){
		        log.info("Aborting challenge of incumbent. Incumbent has " + runHistory.getTotalNumRunsOfConfig(incumbent) + " runs, challenger has " + runHistory.getTotalNumRunsOfConfig(challenger) + " runs, and the maximum runs for any config is set to " + MAX_RUNS_FOR_INCUMBENT + ".");
		        return;
			}
			
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
			Object[] args2 = { N,  runHistory.getThetaIdx(challenger), challenger, bound_inc } ;
			log.info("Performing up to {} run(s) for challenger {} ({}) up to a total bound of {} ", args2);
			
			List<RunConfig> runsToEval = new ArrayList<RunConfig>(options.scenarioConfig.algoExecOptions.maxConcurrentAlgoExecs); 
			
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
					if(runsToEval.size() == options.scenarioConfig.algoExecOptions.maxConcurrentAlgoExecs )
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
						
			//=== Get performance of incumbent and challenger on their common instances.
			Set<ProblemInstance> piCommon = runHistory.getInstancesRan(incumbent);
			piCommon.retainAll( runHistory.getInstancesRan( challenger ));
			
			double incCost = runHistory.getEmpiricalCost(incumbent, piCommon,cutoffTime);
			double chalCost = runHistory.getEmpiricalCost(challenger, piCommon, cutoffTime);
			
			Object args[] = {piCommon.size(), runHistory.getUniqueInstancesRan().size(), runHistory.getThetaIdx(challenger), challenger.getFriendlyIDHex(), chalCost,runHistory.getThetaIdx(incumbent),  incumbent, incCost  };
			log.info("Based on {} common runs on (up to) {} instances, challenger {} ({})  has a lower bound {} and incumbent {} ({}) has obj {}",args);
			
			//=== Decide whether to discard challenger, to make challenger incumbent, or to continue evaluating it more.		
			if (incCost + Math.pow(10, -6)  < chalCost){
				log.info("Challenger {} ({}) is worse; aborting its evaluation",  runHistory.getThetaIdx(challenger), challenger );
				break;
			} else if (sMissing.isEmpty())
			{	
				if(chalCost < incCost - Math.pow(10,-6))
				{
					changeIncumbentTo(challenger);
				} else
				{
					log.info("Challenger {} ({}) has all the runs of the incumbent, but did not outperform it", runHistory.getThetaIdx(challenger), challenger );
				}
				
				break;
			} else
			{
				N *= 2;
				Object[] args3 = { runHistory.getThetaIdx(challenger), challenger, N};
				log.trace("Increasing additional number of runs for challenger {} ({}) to : {} ", args3);
			}
		}
	}
	
	

	private void logConfiguration(String type, ParamConfiguration challenger) {
		
		
		ProblemInstanceSeedPair pisp =  runHistory.getAlgorithmInstanceSeedPairsRan(incumbent).iterator().next();
		
		RunConfig config = new RunConfig(pisp, cutoffTime, challenger);
		
		String cmd = algoEval.getManualCallString(config);
		Object[] args = { type, runHistory.getThetaIdx(challenger), challenger, cmd };
		log.info("Sample Call for {} {} ({}) \n{} ",args);
		
	}



	private static Object currentIncumbentCost;

	private void changeIncumbentTo(ParamConfiguration challenger) {
		// TODO Auto-generated method stub
		incumbent = challenger;
		updateIncumbentCost();
		log.info("Incumbent Changed to: {} ({})", runHistory.getThetaIdx(challenger), challenger );
		logConfiguration("New Incumbent", challenger);

		
		

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
					throw new OutOfTimeException(run);
				} else
				{
					this.sumOfWallClockTime += run.getWallclockExecutionTime();
					this.sumOfReportedAlgorithmRunTime += run.getRuntime();
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
		log.info("Iteration {}: Scheduling {} run(s):", iteration,  runConfigs.size());
		for(RunConfig rc : runConfigs)
		{
			Object[] args = { iteration, runHistory.getThetaIdx(rc.getParamConfiguration()), rc.getParamConfiguration(), rc.getProblemInstanceSeedPair().getInstance().getInstanceID(),  rc.getProblemInstanceSeedPair().getSeed(), rc.getCutoffTime()};
			log.info("Iteration {}: Scheduling run for config {} ({}) on instance {} with seed {} and captime {}", args);
		}
		
		List<AlgorithmRun> completedRuns = algoEval.evaluateRun(runConfigs);
		
		for(AlgorithmRun run : completedRuns)
		{
			RunConfig rc = run.getRunConfig();
			Object[] args = { iteration, runHistory.getThetaIdx(rc.getParamConfiguration()), rc.getParamConfiguration(), rc.getProblemInstanceSeedPair().getInstance().getInstanceID(),  rc.getProblemInstanceSeedPair().getSeed(), rc.getCutoffTime(), run.getRunResult(), options.scenarioConfig.runObj.getObjective(run), run.getWallclockExecutionTime()};
			
			
			log.info("Iteration {}: Completed run for config {} ({}) on instance {} with seed {} and captime {} => Result: {}, response: {}, wallclock time: {} seconds", args);
		}
		
		
		
		updateRunHistory(completedRuns);
		return completedRuns;
	}


	public double getTunerTime() {
		
		double cpuTime = 0;
		
		if(options.countSMACTimeAsTunerTime)
		{
			cpuTime = getCPUTime() / 1000.0 / 1000 / 1000;
		}
		
		return cpuTime + runHistory.getTotalRunCost();
	}


	public double getEmpericalPerformance(ParamConfiguration config) {
		Set<ProblemInstance> pis = new HashSet<ProblemInstance>();
		pis.addAll(instances);
		return runHistory.getEmpiricalCost(config, pis, cutoffTime);
	}
	
	public List<TrajectoryFileEntry> getTrajectoryFileEntries()
	{
		return Collections.unmodifiableList(tfes);
	}
}
