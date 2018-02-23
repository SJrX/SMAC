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

import ca.ubc.cs.beta.aeatk.model.helper.ModelBuilderHelper;
import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.random.MersenneTwister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.cs.beta.aeatk.acquisitionfunctions.AcquisitionFunction;
import ca.ubc.cs.beta.aeatk.algorithmexecutionconfiguration.AlgorithmExecutionConfiguration;
import ca.ubc.cs.beta.aeatk.algorithmrunresult.AlgorithmRunResult;
import ca.ubc.cs.beta.aeatk.eventsystem.EventManager;
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
import ca.ubc.cs.beta.aeatk.probleminstance.seedgenerator.InstanceSeedGenerator;
import ca.ubc.cs.beta.aeatk.random.SeedableRandomPool;
import ca.ubc.cs.beta.aeatk.random.SeedableRandomPoolConstants;
import ca.ubc.cs.beta.aeatk.runhistory.RunHistory;
import ca.ubc.cs.beta.aeatk.runhistory.RunHistoryHelper;
import ca.ubc.cs.beta.aeatk.runhistory.ThreadSafeRunHistory;
import ca.ubc.cs.beta.aeatk.smac.SMACOptions;
import ca.ubc.cs.beta.aeatk.state.StateFactory;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.termination.CompositeTerminationCondition;
import ca.ubc.cs.beta.models.fastrf.RandomForest;
import static ca.ubc.cs.beta.aeatk.misc.math.ArrayMathOps.*;

// Sample Run Configuration: 
// --scenarioFile /home/frank/git/SMAC/deployables/example_scenarios/leadingones/leadingones-100-scenario.txt --log-level debug --log-all-call-strings true --intensification-percentage 0 --acq-func LCB  --rf-split-min 1  --mask-inactive-conditional-parameters-as-default-value false --fullTreeBootstrap true

// still only finds about -20      --scenarioFile /home/frank/git/SMAC/deployables/example_scenarios/leadingones/leadingones-100-scenario.txt --log-level debug --log-all-call-strings true --intensification-percentage 0 --acq-func LCB --rf-split-min 1 --mask-inactive-conditional-parameters-as-default-value false --ignoreConditionality true
// finds -100 in 100 evaluations:  --scenarioFile /home/frank/git/SMAC/deployables/example_scenarios/leadingones/leadingones-100-scenario.txt --log-level debug --log-all-call-strings true --intensification-percentage 0 --acq-func LCB --rf-split-min 1 --mask-inactive-conditional-parameters-as-default-value false --ignoreConditionality false
// finds -100 in 100 evaluations:  --scenarioFile /home/frank/git/SMAC/deployables/example_scenarios/leadingones/leadingones-100-scenario.txt --log-level debug --log-all-call-strings true --intensification-percentage 0 --acq-func LCB --rf-split-min 1 --mask-inactive-conditional-parameters-as-default-value true --ignoreConditionality false
// finds -100 in ~60 evaluations:  --scenarioFile /home/frank/git/SMAC/deployables/example_scenarios/leadingones/leadingones-100-scenario.txt --log-level debug --log-all-call-strings true --intensification-percentage 0 --acq-func LCB --rf-split-min 1 --mask-inactive-conditional-parameters-as-default-value true --ignoreConditionality true

// without sideways moves: -35:   --scenarioFile /home/frank/git/SMAC/deployables/example_scenarios/leadingones/leadingones-100-scenario.txt --log-level debug --log-all-call-strings true --intensification-percentage 0 --acq-func LCB --rf-split-min 1 --mask-inactive-conditional-parameters-as-default-value true --ignoreConditionality true --allow-sideways-moves false

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
	
	private final RunHistory modelRunHistory;

	
	private final ExponentialDistribution exp;

	public SequentialModelBasedAlgorithmConfiguration(SMACOptions smacConfig, AlgorithmExecutionConfiguration execConfig, List<ProblemInstance> instances, TargetAlgorithmEvaluator algoEval, AcquisitionFunction ei, StateFactory sf, ParameterConfigurationSpace configSpace, InstanceSeedGenerator instanceSeedGen, ParameterConfiguration initialConfiguration, List<ParameterConfiguration> initialChallengers, EventManager eventManager, ThreadSafeRunHistory rh, SeedableRandomPool pool, CompositeTerminationCondition termCond, ParamConfigurationOriginTracker configTracker, InitializationProcedure initProc, RunHistory modelRH, CPUTime cpuTime) {
				super(smacConfig,execConfig, instances, algoEval,sf, configSpace, instanceSeedGen, initialConfiguration, initialChallengers, eventManager, rh, pool, termCond, configTracker,initProc,cpuTime);

		numPCA = smacConfig.numPCA;
		logModel = smacConfig.randomForestOptions.logModel;
		this.smacConfig = smacConfig;
		this.ei = ei;
		
		if(modelRH.getAlgorithmRunDataExcludingRedundant().size() > 0)
		{
			log.debug("Model warmstart payload detected with {} runs ", modelRH.getAlgorithmRunDataExcludingRedundant().size());
		}
		this.modelRunHistory = modelRH;
		MersenneTwister prng = new MersenneTwister(pool.getSeed(SeedableRandomPoolConstants.LCB_EXPONENTIAL_SAMPLING_SEED));
		this.exp = new ExponentialDistribution(prng,1);
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
	protected void learnModel(RunHistory runHistory, ParameterConfigurationSpace configSpace) 
	{
		
		
		runHistory = modelRunHistory;
		
		
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

		ModelBuilder mb = ModelBuilderHelper.getModelBuilder(runHistory, configSpace,instances,smacConfig.mbOptions, smacConfig.randomForestOptions,  pool.getRandom("RANDOM_FOREST_BUILDING_PRNG"),smacConfig.adaptiveCapping,numPCA,logModel,subsamplePercentage);

		 /*= */
		forest = mb.getRandomForest();
		preparedForest = mb.getPreparedRandomForest();
	
		log.debug("Random Forest Built");
	}

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
			ParameterConfiguration random = configSpace.getRandomParameterConfiguration(configSpaceRandomInterleave);
			if(i < 25)
			{
				configTracker.addConfiguration(random, "Random-Selection-" +this.getIteration(), "GeneratedThisRound=true");
			}
			randomChallengers.add(random);
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
		/*
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
		*/
		return challengers;
	}
	
	@SuppressWarnings("unused")
	public List<ParameterConfiguration> selectChallengersWithEI(int numChallengers)
	{
		Set<ProblemInstance> instanceSet = new HashSet<ProblemInstance>();
		instanceSet.addAll(runHistory.getProblemInstancesRan(incumbent));
		
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
		
		
		//=== Get predictions for all configurations we have run thus far.
		List<ParameterConfiguration> paramConfigs = runHistory.getAllParameterConfigurationsRan();
		Random rand = pool.getRandom("SMAC_RANDOM_EI_LOCAL_SEARCH");
		
		Set<ParameterConfiguration> randomParameterConfigurations = new HashSet<ParameterConfiguration>();
		
		for(int i=0; i < options.numberOfRandomConfigsUsedForLocalSearch; i++)
		{
			randomParameterConfigurations.add(configSpace.getRandomParameterConfiguration(rand));
		}
		
		randomParameterConfigurations.removeAll(paramConfigs);
		paramConfigs.addAll(randomParameterConfigurations);
		
		
		
		double[][] predictions = transpose(applyMarginalModel(paramConfigs));
		double[] predmean = predictions[0];
		double[] predvar = predictions[1];


		
		
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
		
		Set<ParameterConfiguration> selectedByRandomStartPoint = new HashSet<>();
		
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
			
			if(randomParameterConfigurations.contains(sortedParams.get(i)))
			{
				selectedByRandomStartPoint.add(lsResult.getValue());
			}
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
		
		if(randomConfigs.size() >  0)
		{
			log.trace("Generating {} Random Configurations for EI took {} (s)", numberOfRandomConfigsInEI, t.stop() / 1000.0);
			
			t = new AutoStartStopWatch();
			double[][] randomConfigToDebug = new double[randomConfigs.size()][];
			for(int i=0; i < randomConfigs.size(); i++)
			{
				randomConfigToDebug[i] = randomConfigs.get(i).toValueArray();
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
		}
		
		//== More debugging.
		configArrayToDebug = new double[bestResults.size()][];
		j=0; 
		for(ParamWithEI eic : bestResults)
		{
			configArrayToDebug[j++] = eic.getValue().toValueArray();
		}		
		
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
		
		if(randomParameterConfigurations.contains(bestResults.get(0).getValue()))
		{
			//if(bestResults.get(0).getAssociatedValue() < bestResults.get(1).getAssociatedValue())
			{
				log.warn("Best result was local search from a random configuration: " + bestResults.get(0).getValue().getFormattedParameterString());
			}
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
			if(options.allowSidewaysMoves && localSearchSteps % options.numberOfLsStepsIfSideways == 0) 
			{
				break; // if we're accepting sideways moves, we won't typically stop due to being in a local min, but after these many steps.
//				log.warn("Local Search has done {} iterations, possible infinite loop", localSearchSteps );
			}
			
			//=== Get EI of current configuration.
			ParameterConfiguration c = incumbentEIC.getValue();
			double currentMinEI = incumbentEIC.getAssociatedValue();

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
		
			boolean stoppingConditionSatisfied = false;
			if (options.allowSidewaysMoves){
				//=== If significant worsening then stop; otherwise move to one of the best neighbours.   
				stoppingConditionSatisfied = (min >= currentMinEI + epsilon);
			} else {
				//=== If no significant improvement then stop; otherwise move to one of the best neighbours.   
				stoppingConditionSatisfied = (min >= currentMinEI - epsilon);
			}
			
			if(stoppingConditionSatisfied)
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
				if(minIdx.size() == 0)
				{
					throw new IllegalStateException("AAAAAAH!");
				}
				int nextIdx = minIdx.get(configRandLS.nextInt(minIdx.size()));
				ParameterConfiguration best = neighbourhood.get(nextIdx);
				incumbentEIC = new ParamWithEI(eiVal[nextIdx], best);
				
				//==== Matlab code always uses the min even if we didn't select it.
				//incumbentEIC = new ParamWithEI(min, best);
				
				List<ParameterConfiguration> configList = new ArrayList<ParameterConfiguration>();
				configList.add(best);
				double[][] predictions = transpose(applyMarginalModel(configList));
				double[] mean = predictions[0];
				double[] var = predictions[1];
				
				eiVal = ei.computeAcquisitionFunctionValue(fmin_sample, mean, var,lcbStandardErrors);
			}
		}
		
		log.trace("Local Search took {} steps", localSearchSteps);
		
		return incumbentEIC;
	}
	

	/**
	 * Computes a marginal prediction across all instances for the configs. 
	 * @param configs
	 * @return
	 */
	public double[][] applyMarginalModel(List<ParameterConfiguration> configs)
	{
		//=== Translate into array format, and call method for that format.
		
		double[][] configArrays = new double[configs.size()][];
		int i=0; 
		double[] defaultValues = configSpace.getDefaultConfiguration().toValueArray();
		
		for(ParameterConfiguration config: configs)
		{
			//=== FH in February 2017: also replace inactive parameters with their default here at prediction time (missing before).
			if(smacConfig.mbOptions.maskInactiveConditionalParametersAsDefaultValue)
			{
				configArrays[i] = config.toComparisonValueArray();
				for (int j = 0; j < defaultValues.length; j++) {
					if (Double.isNaN(configArrays[i][j])){
						configArrays[i][j] = defaultValues[j];  
					}
				}
			} else
			{
				configArrays[i] = config.toValueArray();
			}
			i++;
		}
		
		
		
		//=== Use all trees.
		int[] treeIdxsToUse = new int[forest.numTrees];
		for(i=0; i <  forest.numTrees; i++)
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
	
	@Override
	protected Object getModel()
	{
		return this.preparedForest;
	}
	
}
