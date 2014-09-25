package ca.ubc.cs.beta.smac.configurator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.random.MersenneTwister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.cs.beta.aeatk.acquisitionfunctions.AcquisitionFunction;
import ca.ubc.cs.beta.aeatk.algorithmexecutionconfiguration.AlgorithmExecutionConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmrunconfiguration.AlgorithmRunConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.concurrent.threadfactory.SequentiallyNamedThreadFactory;
import ca.ubc.cs.beta.aeatk.eventsystem.EventManager;
import ca.ubc.cs.beta.aeatk.exceptions.DuplicateRunException;
import ca.ubc.cs.beta.aeatk.initialization.InitializationProcedure;
import ca.ubc.cs.beta.aeatk.misc.associatedvalue.ParamWithEI;
import ca.ubc.cs.beta.aeatk.misc.cputime.CPUTime;
import ca.ubc.cs.beta.aeatk.misc.watch.AutoStartStopWatch;
import ca.ubc.cs.beta.aeatk.misc.watch.StopWatch;
import ca.ubc.cs.beta.aeatk.model.builder.AdaptiveCappingModelBuilder;
import ca.ubc.cs.beta.aeatk.model.builder.BasicModelBuilder;
import ca.ubc.cs.beta.aeatk.model.builder.ModelBuilder;
import ca.ubc.cs.beta.aeatk.model.data.MaskCensoredDataAsUncensored;
import ca.ubc.cs.beta.aeatk.model.data.MaskInactiveConditionalParametersWithDefaults;
import ca.ubc.cs.beta.aeatk.model.data.PCAModelDataSanitizer;
import ca.ubc.cs.beta.aeatk.model.data.SanitizedModelData;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParameterConfiguration;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.ParameterConfigurationSpace;
import ca.ubc.cs.beta.aeatk.parameterconfigurationspace.tracking.ParamConfigurationOriginTracker;
import ca.ubc.cs.beta.aeatk.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aeatk.probleminstance.ProblemInstanceSeedPair;
import ca.ubc.cs.beta.aeatk.probleminstance.seedgenerator.InstanceSeedGenerator;
import ca.ubc.cs.beta.aeatk.probleminstance.seedgenerator.SetInstanceSeedGenerator;
import ca.ubc.cs.beta.aeatk.random.SeedableRandomPool;
import ca.ubc.cs.beta.aeatk.random.SeedableRandomPoolConstants;
import ca.ubc.cs.beta.aeatk.runhistory.RunHistory;
import ca.ubc.cs.beta.aeatk.runhistory.RunHistoryHelper;
import ca.ubc.cs.beta.aeatk.runhistory.ThreadSafeRunHistory;
import ca.ubc.cs.beta.aeatk.smac.SMACOptions;
import ca.ubc.cs.beta.aeatk.state.StateFactory;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluatorRunObserver;
import ca.ubc.cs.beta.aeatk.termination.CompositeTerminationCondition;
import ca.ubc.cs.beta.models.fastrf.RandomForest;
import static ca.ubc.cs.beta.aeatk.misc.math.ArrayMathOps.*;

public class SequentialModelBasedAlgorithmConfiguration extends
		AbstractAlgorithmFramework {

	private final int numPCA;
	private final boolean logModel;
	private final Logger log = LoggerFactory.getLogger(this.getClass());
	private final SMACOptions smacConfig;
	
	/**
	 * Most recent forest built
	 */
	private RandomForest forest;
	
	/**
	 * Most recent prepared forest built (may be NULL but always corresponds to the last forest built)
	 */
	private RandomForest preparedForest;
	
	/**
	 * Last build of sanitized data
	 */
	private SanitizedModelData sanitizedData;
	
	private final AcquisitionFunction ei;
	
	
	private static final boolean SELECT_CONFIGURATION_SYNC_DEBUGGING = false;
	
	private final ThreadSafeRunHistory modelRunHistory;

	private final ThreadSafeRunHistory mainRunHistory;
	
	private final ExponentialDistribution exp;
	
	private final AlgorithmExecutionConfiguration execConfig;
	private final ExecutorService execService;
	public SequentialModelBasedAlgorithmConfiguration(SMACOptions smacConfig, AlgorithmExecutionConfiguration execConfig, List<ProblemInstance> instances, TargetAlgorithmEvaluator algoEval, AcquisitionFunction ei, StateFactory sf, ParameterConfigurationSpace configSpace, InstanceSeedGenerator instanceSeedGen, ParameterConfiguration initialConfiguration, EventManager eventManager, final ThreadSafeRunHistory rh, SeedableRandomPool pool, CompositeTerminationCondition termCond, ParamConfigurationOriginTracker configTracker, InitializationProcedure initProc, ThreadSafeRunHistory modelRH, CPUTime cpuTime) {
		super(smacConfig,execConfig, instances, algoEval,sf, configSpace, instanceSeedGen, initialConfiguration, eventManager, rh, pool, termCond, configTracker,initProc,cpuTime);
		
		
		
		numPCA = smacConfig.numPCA;
		logModel = smacConfig.randomForestOptions.logModel;
		this.smacConfig = smacConfig;
		this.ei = ei;
		
		this.mainRunHistory = rh;
		if(modelRH.getAlgorithmRunDataExcludingRedundant().size() > 0)
		{
			log.debug("Model warmstart payload detected with {} runs ", modelRH.getAlgorithmRunDataExcludingRedundant().size());
		}
		this.modelRunHistory = modelRH;
		MersenneTwister prng = new MersenneTwister(pool.getSeed(SeedableRandomPoolConstants.LCB_EXPONENTIAL_SAMPLING_SEED));
		this.exp = new ExponentialDistribution(prng,1);
		
		this.execConfig = execConfig;
		
		
		if(smacConfig.doRandomRunsWithTAE && smacConfig.scenarioConfig.algoExecOptions.taeOpts.maxConcurrentAlgoExecs > 1)
		{
			execService = Executors.newFixedThreadPool(2, new SequentiallyNamedThreadFactory("SMAC Random Submission Threads", true));
			
			execService.execute(new RandomSubmissionLogging());
			execService.execute(new RandomSubmissionRunnable());
			
			
			
		} else
		{
			execService = null;
		}
	}

	
	private class RandomSubmissionLogging implements Runnable
	{

		@Override
		public void run() {
			while(!getTerminationCondition().haveToStop())
			{
				try {
					List<AlgorithmRunResult> runs = lowPriorityTAEQueue.take().getAlgorithmRuns();
					try {
						modelRunHistory.append(runs);
					} catch (DuplicateRunException e) {
						//Doesn't matter
						
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
			
			
		}
		
	}
	
	private class RandomSubmissionRunnable implements Runnable
	{
			@Override
			public void run() {
				
				
				Random randConfigs = pool.getRandom("RANDOM_SUBMISSION_CONFIGURATIONS");
				Random randInstances = pool.getRandom("RANDOM_SUBMISSION_INSTANCE_SELECTION");
				Random randSeeds = pool.getRandom("RANDOM_SUBMISSION_SEED_SELECTION");
				
				int collisionsInARow = 0;
				
				int runsSubmitted = 0;
masterLoop:
				while(!getTerminationCondition().haveToStop())
				{
					mainRunHistory.readLock();

					AlgorithmRunConfiguration runConfig;
					try
					{
						
						
						switch(options.scenarioConfig.getIntraInstanceObjective())
						{
							case MEAN10:
							case MEAN:
							case MEAN1000:
								break;
							default:
								//We don't do a proper search below for the appropriate bound 
								//for the runtime, so for instance if it was something non-sensical like PAR0.5 (timeout runs count for 1/2 the cutoff time),
								//Then we would screw up our calculation of what was better
								log.error("Not sure what intra instance objective is being used, cannot seed random runs");
								return;
						}
						
						switch(options.scenarioConfig.interInstanceObj)
						{
							case MEAN10:
							case MEAN:
							case MEAN1000:
								break;
							default:
								//We don't do a proper search below for the appropriate bound 
								//for the runtime, so for instance if it was something non-sensical like PAR0.5 (timeout runs count for 1/2 the cutoff time),
								//Then we would screw up our calculation of what was better
								log.error("Not sure what inter instance objective is being used, cannot seed random runs");
								return;
						}
						
						ParameterConfiguration incumbent = getIncumbent();
						
						
						
						ProblemInstance submissionInstance = instances.get(randInstances.nextInt(instances.size()));
						
						
						
						
						ParameterConfiguration newConfiguration = configSpace.getRandomParameterConfiguration(randConfigs);
						
						if(modelRunHistory.getProblemInstancesRan(newConfiguration).contains(submissionInstance))
						{
							//Try again with something else after sleeping for 1 second.
							collisionsInARow++;
							
							if(collisionsInARow > 100)
							{
								log.warn("Random seeding of model cannot continue as we seem to have run every configuration & problem instance seed pair that exists (100 collisions in a row)" );
								break masterLoop;
							}
							
							continue;
						} else
						{
							collisionsInARow=0;
						}
						
						double runCutOffTime = cutoffTime;
					
						switch(options.scenarioConfig.getRunObjective())
						{
							case RUNTIME:
									Set<ProblemInstance> instancesRan = mainRunHistory.getProblemInstancesRan(incumbent);
									
									double incCost; //Estimate of performance, if we ran the incumbent already on this instance, we will use it's cost
									//Otherwise we will request for the emprical estimate accross the entire distribution.
									
									if(instancesRan.contains(submissionInstance))
									{
										 incCost = mainRunHistory.getEmpiricalCost(incumbent, Collections.singleton(submissionInstance), cutoffTime);
										
										
									} else
									{							
										incCost = mainRunHistory.getEmpiricalCost(incumbent, instancesRan, cutoffTime);
									}
										
									
									runCutOffTime = Math.min(incCost*options.capSlack + options.capAddSlack, SequentialModelBasedAlgorithmConfiguration.this.cutoffTime);

								break;
							case QUALITY:
									runCutOffTime =  SequentialModelBasedAlgorithmConfiguration.this.cutoffTime;
								break;
							default:
								log.error("Not sure what run objective we are optimizing for, cannot get random runs");
						}
						
						long seed = -1;
						if(!options.scenarioConfig.algoExecOptions.deterministic)
						{
							if(SequentialModelBasedAlgorithmConfiguration.this.instanceSeedGen instanceof SetInstanceSeedGenerator)
							{
								log.error("Seeding of instances will not follow the preloaded instance seeds");
							} else
							{
								seed = randSeeds.nextInt(1000000);
							}
						}
						runConfig = new AlgorithmRunConfiguration(new ProblemInstanceSeedPair(submissionInstance, seed), runCutOffTime, newConfiguration, execConfig);
						
					} finally
					{
						mainRunHistory.releaseReadLock();
					}

					
					if(Thread.interrupted())
					{
						Thread.currentThread().interrupt();
						break masterLoop;
					}
					StopWatch watch = new AutoStartStopWatch();
					TargetAlgorithmEvaluatorRunObserver  obs = new TargetAlgorithmEvaluatorRunObserver()
					{

						@Override
						public void currentStatus(
								List<? extends AlgorithmRunResult> runs) {
							if(getTerminationCondition().haveToStop())
							{
								for(AlgorithmRunResult run : runs)
								{
									run.kill();
								}
							}
							
						}
						
					};
					lowPriorityTAEQueue.evaluateRunAsync(Collections.singletonList(runConfig), obs);
					log.debug("Submission of Random Run took {} seconds ",watch.stop() / 1000.0);
					runsSubmitted++;
					
					
				}
				
				log.info("Submitted {} random evaluations in background", runsSubmitted);
				
				if(execService != null)
				{
					execService.shutdownNow();
				}
				
			}
			
			
		
	}
	
	
	
	protected double freeMemoryAfterGC()
	{
		log.trace("Running System Garbage Collector");
		System.gc();
		
		double freeMemory = ((double) Runtime.getRuntime().freeMemory() / (double) Runtime.getRuntime().totalMemory());
		log.trace("Free memory {} %", freeMemory);
		return freeMemory;
	}
	
	
	private double subsamplePercentage = 1;
	/**
	 * Learns a model from the data in runHistory.
	 */
	@Override
	protected void learnModel(RunHistory runHistoryX, ParameterConfigurationSpace configSpace) 
	{
		
		ThreadSafeRunHistory runHistory = modelRunHistory;
		
		runHistory.readLock();
		try
		{
		
			if(runHistory.getAlgorithmRunsExcludingRedundant().size() == 0)
			{
				throw new IllegalStateException("Expected one run to be done before building model");
			}
			
			if(options.randomForestOptions.subsampleValuesWhenLowMemory)
			{
				
				double freeMemory = freeMemoryAfterGC();
				if(freeMemory < options.randomForestOptions.freeMemoryPercentageToSubsample)
				{
					subsamplePercentage *= options.randomForestOptions.subsamplePercentage;
					Object[] args = { getIteration(), freeMemory, subsamplePercentage};
					log.debug("Iteration {} : Free memory too low ({}) subsample percentage now {} ", args);
				}
	
			} else
			{
				subsamplePercentage = 1;
			}
			
			
			
			
			
			
			
			
			
			
			//=== The following two sets are required to be sorted by instance and paramConfig ID.
			Set<ProblemInstance> all_instances = new LinkedHashSet<ProblemInstance>(instances);
			Set<ParameterConfiguration> paramConfigs = runHistory.getUniqueParamConfigurations();
			
			Set<ProblemInstance> runInstances=runHistory.getUniqueInstancesRan();
			ArrayList<Integer> runInstancesIdx = new ArrayList<Integer>(all_instances.size());
			
			//=== Get the instance feature matrix (X).
			int i=0; 
			double[][] instanceFeatureMatrix = new double[all_instances.size()][];
			for(ProblemInstance pi : all_instances)
			{
				if(runInstances.contains(pi))
				{
					runInstancesIdx.add(i);
				}
				instanceFeatureMatrix[i] = pi.getFeaturesDouble();
				i++;
			}
	
			//=== Get the parameter configuration matrix (Theta).
			double[][] thetaMatrix = new double[paramConfigs.size()][];
			i = 0;
			for(ParameterConfiguration pc : paramConfigs)
			{
				if(smacConfig.mbOptions.maskInactiveConditionalParametersAsDefaultValue)
				{
					thetaMatrix[i++] = pc.toComparisonValueArray();
				} else
				{
					thetaMatrix[i++] = pc.toValueArray();
				}
			}
	
			//=== Get an array of the order in which instances were used (TODO: same for Theta, from ModelBuilder) 
			int[] usedInstanceIdxs = new int[runInstancesIdx.size()]; 
			for(int j=0; j <  runInstancesIdx.size(); j++)
			{
				usedInstanceIdxs[j] = runInstancesIdx.get(j);
			}
			
			
			List<AlgorithmRunResult> runs = runHistory.getAlgorithmRunsExcludingRedundant();
			double[] runResponseValues = RunHistoryHelper.getRunResponseValues(runs, runHistory.getRunObjective());
			boolean[] censored = RunHistoryHelper.getCensoredEarlyFlagForRuns(runs);
			
			if(smacConfig.mbOptions.maskCensoredDataAsKappaMax)
			{
				for(int j=0; j < runResponseValues.length; j++)
				{
					if(censored[j])
					{
						runResponseValues[j] = options.scenarioConfig.algoExecOptions.cutoffTime;
					}
				}
			}
			
			switch(smacConfig.scenarioConfig.getRunObjective())
			{
			case QUALITY:
				break;
			case RUNTIME:
				for(int j=0; j < runResponseValues.length; j++)
				{ //=== Not sure if I Should be penalizing runs prior to the model
					// but matlab sure does
					if(runResponseValues[j] >= options.scenarioConfig.algoExecOptions.cutoffTime)
					{	
						runResponseValues[j] = options.scenarioConfig.algoExecOptions.cutoffTime * options.scenarioConfig.getIntraInstanceObjective().getPenaltyFactor();
					}
				}
				break;
			default: 
				throw new IllegalStateException("Don't know how to optimize this run objective:" + smacConfig.scenarioConfig.getRunObjective());
			}
			
			//=== Sanitize the data.
			sanitizedData = new PCAModelDataSanitizer(instanceFeatureMatrix, thetaMatrix, numPCA, runResponseValues, usedInstanceIdxs, logModel, runHistory.getParameterConfigurationInstancesRanByIndexExcludingRedundant(), censored, configSpace);
			
			
			if(smacConfig.mbOptions.maskCensoredDataAsUncensored)
			{
				sanitizedData = new MaskCensoredDataAsUncensored(sanitizedData);
			}
			
			
			if(smacConfig.mbOptions.maskInactiveConditionalParametersAsDefaultValue)
			{
				sanitizedData = new MaskInactiveConditionalParametersWithDefaults(sanitizedData, configSpace);
			}
		
		} finally
		{
			runHistory.releaseReadLock();
		}
		
		//=== Actually build the model.
		ModelBuilder mb;
		//TODO: always go through AdaptiveCappingModelBuilder
		forest = null;
		preparedForest = null;
		if(options.adaptiveCapping)
		{
			mb = new AdaptiveCappingModelBuilder(sanitizedData, smacConfig.randomForestOptions, pool.getRandom("RANDOM_FOREST_BUILDING_PRNG"), smacConfig.mbOptions.imputationIterations, smacConfig.scenarioConfig.algoExecOptions.cutoffTime, smacConfig.scenarioConfig.getIntraInstanceObjective().getPenaltyFactor(), subsamplePercentage);
		} else
		{
			//mb = new HashCodeVerifyingModelBuilder(sanitizedData,smacConfig.randomForestOptions, runHistory);
			mb = new BasicModelBuilder(sanitizedData, smacConfig.randomForestOptions,subsamplePercentage, pool.getRandom("RANDOM_FOREST_BUILDING_PRNG")); 
		}
		
		 /*= */
		forest = mb.getRandomForest();
		preparedForest = mb.getPreparedRandomForest();
	
		log.debug("Random Forest Built");
	}
	
	//private int selectionCount = 0;
	protected List<ParameterConfiguration> selectConfigurations()
	{
		Random configSpaceRandomInterleave = pool.getRandom("SMAC_RANDOM_INTERLEAVED_CONFIG_PRNG");
		AutoStartStopWatch t = new AutoStartStopWatch();
		List<ParameterConfiguration> eichallengers = selectChallengersWithEI(smacConfig.numberOfChallengers);
		
		log.debug("Selecting {} challengers based on EI took {} seconds", eichallengers.size(), t.stop() / 1000.0);
		
		List<ParameterConfiguration> randomChallengers = new ArrayList<ParameterConfiguration>(eichallengers.size());
		
		
		 t = new AutoStartStopWatch();
		for(int i=0; i < eichallengers.size(); i++)
		{
			randomChallengers.add(configSpace.getRandomParameterConfiguration(configSpaceRandomInterleave));
		}
		log.trace("Generating {} Random Configurations took {} seconds", eichallengers.size(),  t.stop()/1000.0 );
		
		//=== Interleave the EI and random challengers.
		List<ParameterConfiguration> challengers = new ArrayList<ParameterConfiguration>(eichallengers.size()*2);
		for(int i=0; i < eichallengers.size(); i++)
		{
			challengers.add(eichallengers.get(i));
			challengers.add(randomChallengers.get(i));
		}
		
		//=== Convert to array form for debug hash code.
		double[][] configArrayToDebug = new double[challengers.size()][];
		int j=0; 
		for(ParameterConfiguration c : challengers)
		{
			configArrayToDebug[j++] = c.toValueArray();
		}
		
		if(SELECT_CONFIGURATION_SYNC_DEBUGGING)
		{
			log.trace("Final Selected Challengers Configurations Hash Code {}", matlabHashCode(configArrayToDebug));
		}
		
		return challengers;
	}
	
	@SuppressWarnings("unused")
	public List<ParameterConfiguration> selectChallengersWithEI(int numChallengers)
	{
		Set<ProblemInstance> instanceSet = new HashSet<ProblemInstance>();
		instanceSet.addAll(runHistory.getProblemInstancesRan(incumbent));
		
		//=== Get predictions for all configurations we have run thus far.
		List<ParameterConfiguration> paramConfigs = runHistory.getAllParameterConfigurationsRan();
		double[][] predictions = transpose(applyMarginalModel(paramConfigs));
		double[] predmean = predictions[0];
		double[] predvar = predictions[1];

		double[][] tmp_predictions = transpose(applyMarginalModel(Collections.singletonList(incumbent)));
		log.trace("Prediction for incumbent: {} +/- {} (in log space if logModel=true)", tmp_predictions[0][0], tmp_predictions[1][0]);
		
		double fmin = runHistory.getEmpiricalCost(incumbent, instanceSet, smacConfig.scenarioConfig.algoExecOptions.cutoffTime);
		double lcbStandardErrors = exp.sample();
		//=== Get the empirical cost into log space if the model gives log predictions. 
		if (smacConfig.randomForestOptions.logModel)
		{
			
			fmin = Math.max(SanitizedModelData.MINIMUM_RESPONSE_VALUE, fmin);
			fmin = Math.log10(fmin);
			
			double adjusted_fmin = runHistory.getEmpiricalCost(incumbent, instanceSet, smacConfig.scenarioConfig.algoExecOptions.cutoffTime, SanitizedModelData.MINIMUM_RESPONSE_VALUE);
			adjusted_fmin = Math.max(SanitizedModelData.MINIMUM_RESPONSE_VALUE, adjusted_fmin);
			adjusted_fmin = Math.log10(adjusted_fmin);
			Object[] args = { getIteration(), fmin, adjusted_fmin};
			
			log.trace("Optimizing EI at valdata.iteration {}. fmin: {}, fmin (accounting for minimum response value): {}", args);
			/*
			if(tmp_predictions[1][0] > Math.pow(10, -13))
			{
				
				List<AlgorithmRun> o =  runHistory.getAlgorithmRuns();
				for(AlgorithmRun run : o)
				{
					if(run.getRunConfig().getParamConfiguration().equals(incumbent))
					{
						log.debug("Instance {} Runtime {}:", run.getRunConfig().getProblemInstanceSeedPair().getInstance().getInstanceID(), run.getRuntime());
					}
				}
				
				throw new IllegalStateException("");
				//System.out.println("Hello");
				
			}
			*/
		} else
		{
			log.debug("Optimizing EI at valdata.iteration {}. fmin: {}", getIteration(), fmin);
		}
		
		//=== Compute EI of these configurations (as given by predmean,predvar)
		
		StopWatch watch = new AutoStartStopWatch();
		double[] negativeExpectedImprovementOfTheta = ei.computeAcquisitionFunctionValue(fmin, predmean, predvar,lcbStandardErrors);
		

		watch.stop();
		log.debug("Compute negEI for all conf. seen at valdata.iteration {}: took {} s",getIteration(), ((double) watch.time()) / 1000.0 );

		//=== Put these EIs into a map for each configuration.
		Map<ParameterConfiguration, double[]> configPredMeanVarEIMap = new LinkedHashMap<ParameterConfiguration, double[]>();
		for(int i=0; i < paramConfigs.size(); i++)
		{
			double[] val = { predmean[i], predvar[i], negativeExpectedImprovementOfTheta[i] };
			configPredMeanVarEIMap.put(paramConfigs.get(i), val);
		}
		
		//=== Sort the configurations by EI
		List<ParamWithEI> sortedParams = ParamWithEI.merge(negativeExpectedImprovementOfTheta, paramConfigs);
		Collections.sort(sortedParams);

		//=== Local search for good EI configs.
		int numberOfSearches = Math.min(numChallengers, predmean.length);
		List<ParamWithEI> bestResults  = new ArrayList<ParamWithEI>(numberOfSearches);
		double min_neg = Double.MAX_VALUE;
		AutoStartStopWatch stpWatch = new AutoStartStopWatch();
		
		for(int i=0; i < numberOfSearches; i++)
		{
			watch = new AutoStartStopWatch();
			
			ParamWithEI lsResult = localSearch(sortedParams.get(i), fmin, Math.pow(10, -5),lcbStandardErrors);
			

			if(lsResult.getAssociatedValue() < min_neg)
			{
				min_neg = lsResult.getAssociatedValue();
			}
			
			
			predictions = transpose(applyMarginalModel(Collections.singletonList(lsResult.getValue())));
			double[] val = { predictions[0][0], predictions[1][0], lsResult.getAssociatedValue()};
			
			//=== The Matlab code does not actually give us the real neg EI, we recompute it here because it just gives us
			//the best one it found, and we are within epsilon away from that
			//If you are reading this, someone somewhere is very sad that the code still does this ;)
			//double realnegEI = ei.computeNegativeExpectedImprovement(quality, predictions[0], predictions[1])[0];
			
			//lsResult = new ParamWithEI(realnegEI, lsResult.getValue());
			
			bestResults.add(lsResult);
			configPredMeanVarEIMap.put(lsResult.getValue(), val);
			watch.stop();
			Object[] args = {i+1,((double) watch.time()/1000.0),min_neg};
			log.trace("LS {} took {} seconds and yielded neg log EI {}",args);
		}
		
		log.trace("{} Local Searches took {} seconds in total ", numberOfSearches, stpWatch.stop()  / 1000.0 );
		//=== Get into array format for debugging.
		double[][] configArrayToDebug = new double[bestResults.size()][];
		int j=0; 
		for(ParamWithEI bestResult : bestResults)
		{
			configArrayToDebug[j++] = bestResult.getValue().toValueArray();
		}
		
		
		
		
	
		
		//=== Generate random configurations
		int numberOfRandomConfigsInEI = smacConfig.numberOfRandomConfigsInEI;
		
		
		Random configSpaceEIRandom = pool.getRandom("SMAC_RANDOM_EI_CONFIG_PRNG");
		AutoStartStopWatch t = new AutoStartStopWatch();
		List<ParameterConfiguration> randomConfigs = new ArrayList<ParameterConfiguration>(numberOfRandomConfigsInEI);
		for(int i=0; i < numberOfRandomConfigsInEI; i++)
		{
			randomConfigs.add(configSpace.getRandomParameterConfiguration(configSpaceEIRandom));
		} 
		
		log.trace("Generating {} Random Configurations for EI took {} (s)", numberOfRandomConfigsInEI, t.stop() / 1000.0);
		
		t = new AutoStartStopWatch();
		double[][] randomConfigToDebug = new double[randomConfigs.size()][];
		for(int i=0; i < randomConfigs.size(); i++)
		{
			randomConfigToDebug[i] = randomConfigs.get(i).toValueArray();
		}
		if(SELECT_CONFIGURATION_SYNC_DEBUGGING &&  log.isDebugEnabled())
		{
			log.trace("Local Search Selected Random Configs Hash Code: {}", matlabHashCode(randomConfigToDebug));
		}
		//=== Compute EI for the random configs.		
		predictions = transpose(applyMarginalModel(randomConfigs));
		predmean = predictions[0];
		predvar = predictions[1];
		
		log.trace("Prediction for Random Configurations took {} (s)", t.stop() / 1000.0);
		t = new AutoStartStopWatch();
		double[] expectedImprovementOfRandoms = ei.computeAcquisitionFunctionValue(fmin, predmean, predvar,lcbStandardErrors);
		log.trace("EI Calculation for Random Configurations took {} (s)", t.stop() / 1000.0);
		t = new AutoStartStopWatch();
		for(int i=0; i <  randomConfigs.size(); i++)
		{
			double[] val = { predmean[i], predvar[i], expectedImprovementOfRandoms[i] };
			configPredMeanVarEIMap.put(randomConfigs.get(i), val);
		}
		
		log.trace("Map Insertion for Random Configurations took {} (s)", t.stop() / 1000.0);
		
		
		//=== Add random configs to LS configs.
		bestResults.addAll(ParamWithEI.merge(expectedImprovementOfRandoms, randomConfigs));

		//== More debugging.
		configArrayToDebug = new double[bestResults.size()][];
		j=0; 
		for(ParamWithEI eic : bestResults)
		{
			configArrayToDebug[j++] = eic.getValue().toValueArray();
		}		
		
		/*
		if(RoundingMode.ROUND_NUMBERS_FOR_MATLAB_SYNC)
		{
			List<ParamWithEI> realBestResults = new ArrayList<ParamWithEI>(bestResults.size());
			//=== Round the ei value for MATLAB synchronization purposes
			for(ParamWithEI pwei : bestResults)
			{
				double ei = Math.round(pwei.getAssociatedValue() * 1000000000) / 1000000000.0;
				realBestResults.add(new ParamWithEI( ei,pwei.getValue()));
			}
			bestResults = realBestResults;
		}
		*/
		
		//=== Sort configs by EI and output top ones.
		
		bestResults = permute(bestResults,configSpaceEIRandom);
		Collections.sort(bestResults);
		
		
		
	
		
		for(int i=0; i < numberOfSearches; i++)
		{
			double[] meanvar = configPredMeanVarEIMap.get(bestResults.get(i).getValue());
			Object[] args = {i+1, meanvar[0], Math.sqrt(meanvar[1]), -meanvar[2]}; 
			log.trace("Challenger {} predicted {} +/- {}, expected improvement {}",args);
		}
		
		//=== Make result list of configurations. 
		List<ParameterConfiguration> results = new ArrayList<ParameterConfiguration>(bestResults.size());
		for(ParamWithEI eic : bestResults)
		{
			results.add(eic.getValue());
		}
		
		
		for(int i = 0; i < Math.min(25, bestResults.size()); i++)
		{
			ParamWithEI eic = bestResults.get(i);
			configTracker.addConfiguration(eic.getValue(), "Model-Builder-" +this.getIteration(),"EIMethod="+options.expFunc, "EI=" + eic.getAssociatedValue() , "firstArg(k)=" + fmin, "ModelVersion=" + this.getIteration() , "ModelPoints=" + this.runHistory.getAlgorithmRunsExcludingRedundant().size());
		}
		
		
		
		
		
		//== More debugging.
		/*
		configArrayToDebug = new double[results.size()][];
		j=0; 
		for(ParamConfiguration c : results)
		{
		configArrayToDebug[j++] = c.toValueArray();
		}
		if(SELECT_CONFIGURATION_SYNC_DEBUGGING)
		{
			log.debug("Re-sorted Local Search Selected Configurations & Random Configs Hash Code {}", matlabHashCode(configArrayToDebug));
		}*/
		
		
		return Collections.unmodifiableList(results);	
	}
	
	
	/**
	 * Performs a local search starting from the specified start configuration  
	 * @param eic - Parameter configuration coupled with it's expected improvement 
	 * @param fmin_sample - The best performance (f_min) to beat
	 * @param epsilon - Minimum value of improvement required before terminating
	 * @return best Param&EI Found
	 */
	private ParamWithEI localSearch(ParamWithEI startEIC, double fmin_sample, double epsilon, double lcbStandardErrors)
	{
		ParamWithEI incumbentEIC = startEIC;
		
		int localSearchSteps = 0;
		
		Random configRandLS = pool.getRandom("SMAC_EI_LOCAL_SEARCH_NEIGHBOURS");
		while(true)
		{
			localSearchSteps++;
			if(localSearchSteps % 1000 == 0)
			{
				log.warn("Local Search has done {} iterations, possible infinite loop", localSearchSteps );
			}
			
			//=== Get EI of current configuration.
			ParameterConfiguration c = incumbentEIC.getValue();
			double currentMinEI = incumbentEIC.getAssociatedValue();
			
			//System.out.println("minEI: " + currentMinEI + " incumbent: " + c.hashCode());
			double[][] cArray = {c.toValueArray()};
			int LSHashCode = matlabHashCode(cArray);
			
			if(SELECT_CONFIGURATION_SYNC_DEBUGGING) log.trace("Local Search HashCode: {}", LSHashCode);
			
			//=== Get neighbourhood of current options and compute EI for all of it.
			List<ParameterConfiguration> neighbourhood = c.getNeighbourhood(configRandLS, options.scenarioConfig.algoExecOptions.paramFileDelegate.continuousNeighbours);
			double[][] prediction = transpose(applyMarginalModel(neighbourhood));
			double[] means = prediction[0];
			double[] vars = prediction[1];
			double[] eiVal = ei.computeAcquisitionFunctionValue(fmin_sample, means, vars,lcbStandardErrors); 
			
			//=== Determine EI of best neighbour.
			double min = eiVal[0];
			for(int i=1; i < eiVal.length; i++)
			{
				if(eiVal[i] < min)
				{
					min = eiVal[i];
				}
			}
		
			//=== If significant improvement then move to one of the best neighbours; otherwise break.   
			if(min >= currentMinEI - epsilon)
			{
				break;
			} else
			{
				//== Make list of best neighbours (best within epsilon).
				List<Integer> minIdx = new ArrayList<Integer>(c.size());
				for(int i=0; i < eiVal.length; i++)
				{
					if(eiVal[i] <= min + epsilon)
					{
						//currentMinEI = eiVal[i];
						minIdx.add(i);
					} 
				}

				//== Move to random element of the best neighbours.
				int nextIdx = minIdx.get(configRandLS.nextInt(minIdx.size()));
				ParameterConfiguration best = neighbourhood.get(nextIdx);
				incumbentEIC = new ParamWithEI(eiVal[nextIdx], best);
				
				//==== Matlab code always uses the min even if we didn't select it.
				//incumbentEIC = new ParamWithEI(min, best);
				
				double[][] next = { best.toValueArray() };
				double[][] predictions = transpose(applyMarginalModel(next));
				double[] mean = predictions[0];
				double[] var = predictions[1];
				
				eiVal = ei.computeAcquisitionFunctionValue(fmin_sample, mean, var,lcbStandardErrors);
			}
		}
		
		log.trace("Local Search took {} steps", localSearchSteps);
		
		return incumbentEIC;
	}

	/**
	 * Computes a marginal prediction across all instances for the configArrays.
	 * @param configArrays
	 * @return
	 */
	protected double[][] applyMarginalModel(double[][] configArrays)
	{
		//=== Use all trees.
		int[] treeIdxsToUse = new int[forest.numTrees];
		for(int i=0; i <  forest.numTrees; i++)
		{
			treeIdxsToUse[i]=i;
		}
		
		//=== Get the marginal (from preprocessed forest if available).
		if(smacConfig.randomForestOptions.preprocessMarginal)
		{
			return RandomForest.applyMarginal(preparedForest,treeIdxsToUse,configArrays);
		} else
		{
			return RandomForest.applyMarginal(forest,treeIdxsToUse,configArrays,sanitizedData.getPCAFeatures());
		}
		
	}
	
	/**
	 * Computes a marginal prediction across all instances for the configs. 
	 * @param configs
	 * @return
	 */
	protected double[][] applyMarginalModel(List<ParameterConfiguration> configs)
	{
		//=== Translate into array format, and call method for that format.
		double[][] configArrays = new double[configs.size()][];
		int i=0; 
		for(ParameterConfiguration config: configs)
		{
			configArrays[i] = config.toValueArray();
			i++;
		}
		return applyMarginalModel(configArrays);		
	}
	
	@Override
	protected Object getModel()
	{
		return this.preparedForest;
	}
	
}
