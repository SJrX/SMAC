package ca.ubc.cs.beta.smac;

import java.util.ArrayList;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration;
import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.aclib.expectedimprovement.ExpectedImprovementFunction;
import ca.ubc.cs.beta.aclib.misc.associatedvalue.ParamWithEI;
import ca.ubc.cs.beta.aclib.misc.math.ArrayMathOps;
import ca.ubc.cs.beta.aclib.misc.random.SeedableRandomSingleton;
import ca.ubc.cs.beta.aclib.misc.watch.AutoStartStopWatch;
import ca.ubc.cs.beta.aclib.misc.watch.StopWatch;
import ca.ubc.cs.beta.aclib.model.builder.AdaptiveCappingModelBuilder;
import ca.ubc.cs.beta.aclib.model.builder.BasicModelBuilder;
import ca.ubc.cs.beta.aclib.model.builder.HashCodeVerifyingModelBuilder;
import ca.ubc.cs.beta.aclib.model.builder.ModelBuilder;
import ca.ubc.cs.beta.aclib.model.data.PCAModelDataSanitizer;
import ca.ubc.cs.beta.aclib.options.SMACOptions;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aclib.runhistory.RunHistory;
import ca.ubc.cs.beta.aclib.seedgenerator.InstanceSeedGenerator;
import ca.ubc.cs.beta.aclib.state.StateFactory;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.models.fastrf.RandomForest;
import ca.ubc.cs.beta.models.fastrf.RoundingMode;
import ca.ubc.cs.beta.smac.matlab.helper.StaticMethodWrapper;
import static ca.ubc.cs.beta.aclib.misc.math.ArrayMathOps.*;

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
	private PCAModelDataSanitizer sanitizedData;
	private final ExpectedImprovementFunction ei;
	
	
	public SequentialModelBasedAlgorithmConfiguration(SMACOptions smacConfig, List<ProblemInstance> instances, List<ProblemInstance> testInstances, TargetAlgorithmEvaluator algoEval, ExpectedImprovementFunction ei, StateFactory sf, ParamConfigurationSpace configSpace, InstanceSeedGenerator instanceSeedGen, Random rand) {
		super(smacConfig, instances, testInstances, algoEval,sf, configSpace, instanceSeedGen, rand);
		numPCA = smacConfig.numPCA;
		logModel = smacConfig.randomForestOptions.logModel;
		this.smacConfig = smacConfig;
		this.ei = ei;
	}

	/**
	 * Learns a model from the data in runHistory.
	 */
	@Override
	protected void learnModel(RunHistory runHistory, ParamConfigurationSpace configSpace) 
	{
		//=== The following two sets are required to be sorted by instance and paramConfig ID.
		Set<ProblemInstance> all_instances = new LinkedHashSet<ProblemInstance>(instances);
		Set<ParamConfiguration> paramConfigs = runHistory.getUniqueParamConfigurations();
		
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
		for(ParamConfiguration pc : paramConfigs)
		{
			thetaMatrix[i++] = pc.toValueArray();
		}

		//=== Get an array of the order in which instances were used (TODO: same for Theta, from ModelBuilder) 
		int[] usedInstanceIdxs = new int[runInstancesIdx.size()]; 
		for(int j=0; j <  runInstancesIdx.size(); j++)
		{
			usedInstanceIdxs[j] = runInstancesIdx.get(j);
		}
		
		double[] runResponseValues = runHistory.getRunResponseValues();
		
		
		
		for(int j=0; j < runResponseValues.length; j++)
		{ //=== Not sure if I Should be penalizing runs prior to the model
			// but matlab sure does
			if(runResponseValues[j] >= options.scenarioConfig.cutoffTime)
			{	
				runResponseValues[j] = runResponseValues[j] * options.scenarioConfig.intraInstanceObj.getPenaltyFactor();
			}
		}
	
		//=== Sanitize the data.
		sanitizedData = new PCAModelDataSanitizer(instanceFeatureMatrix, thetaMatrix, numPCA, runResponseValues, usedInstanceIdxs, logModel, configSpace);
		
		//=== Actually build the model.
		ModelBuilder mb;
		//TODO: always go through AdaptiveCappingModelBuilder
		if(options.adaptiveCapping)
		{
			mb = new AdaptiveCappingModelBuilder(sanitizedData, smacConfig.randomForestOptions, runHistory, rand, smacConfig.imputationIterations, smacConfig.scenarioConfig.cutoffTime, smacConfig.scenarioConfig.intraInstanceObj.getPenaltyFactor());
		} else
		{
			mb = new HashCodeVerifyingModelBuilder(sanitizedData,smacConfig.randomForestOptions, runHistory);
			//mb = new BasicModelBuilder(sanitizedData, smacConfig.randomForestOptions, runHistory); 
		}
		 /*= */
		forest = mb.getRandomForest();
		preparedForest = mb.getPreparedRandomForest();
	
		log.info("Random Forest Built");
	}
	
	protected List<ParamConfiguration> selectConfigurations()
	{
		List<ParamConfiguration> eichallengers = selectChallengersWithEI(smacConfig.numberOfChallengers);
		
		List<ParamConfiguration> randomChallengers = new ArrayList<ParamConfiguration>(eichallengers.size());
		log.info("Generating {} random configurations", eichallengers.size());
		for(int i=0; i < eichallengers.size(); i++)
		{
			randomChallengers.add(configSpace.getRandomConfiguration());
		}
		
		//=== Interleave the EI and random challengers.
		List<ParamConfiguration> challengers = new ArrayList<ParamConfiguration>(eichallengers.size()*2);
		for(int i=0; i < eichallengers.size(); i++)
		{
			challengers.add(eichallengers.get(i));
			challengers.add(randomChallengers.get(i));
		}
		
		//=== Convert to array form for debug hash code.
		double[][] configArrayToDebug = new double[challengers.size()][];
		int j=0; 
		for(ParamConfiguration c : challengers)
		{
			configArrayToDebug[j++] = c.toValueArray();
		}
		log.info("Final Selected Challengers Configurations Hash Code {}", matlabHashCode(configArrayToDebug));
		
		return challengers;
	}
	
	public List<ParamConfiguration> selectChallengersWithEI(int numChallengers)
	{
		Set<ProblemInstance> instanceSet = new HashSet<ProblemInstance>();
		instanceSet.addAll(runHistory.getInstancesRan(incumbent));
		
		//=== Get predictions for all configurations we have run thus far.
		List<ParamConfiguration> paramConfigs = runHistory.getAllParameterConfigurationsRan();
		double[][] predictions = transpose(applyMarginalModel(paramConfigs));
		double[] predmean = predictions[0];
		double[] predvar = predictions[1];

		double quality = runHistory.getEmpiricalCost(incumbent, instanceSet, smacConfig.scenarioConfig.cutoffTime);
		//=== Get the empirical cost into log space if the model gives log predictions. 
		if (smacConfig.randomForestOptions.logModel)
		{
			//TODO HANDLE MIN RUNTIME THIS IS SO A BUG
			//THIS IS SO A BUG
			//THIS IS SO A BUG 
			quality = Math.log10(quality);
		}
		
		//=== Compute EI of these configurations (as given by predmean,predvar)
		log.info("Optimizing EI at valdata.iteration {}", getIteration());
		StopWatch watch = new AutoStartStopWatch();
		double[] negativeExpectedImprovementOfTheta = ei.computeNegativeExpectedImprovement(quality, predmean, predvar);
		
		
		
		
		watch.stop();
		log.info("Compute negEI for all conf. seen at valdata.iteration {}: took {} s",getIteration(), ((double) watch.time()) / 1000.0 );

		//=== Put these EIs into a map for each configuration.
		Map<ParamConfiguration, double[]> configPredMeanVarEIMap = new LinkedHashMap<ParamConfiguration, double[]>();
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
		for(int i=0; i < numberOfSearches; i++)
		{
			watch = new AutoStartStopWatch();
			
			ParamWithEI lsResult = localSearch(sortedParams.get(i), quality, Math.pow(10, -5));
			

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
			log.info("LS {} took {} seconds and yielded neg log EI {}",args);
		}
		
		//=== Get into array format for debugging.
		double[][] configArrayToDebug = new double[bestResults.size()][];
		int j=0; 
		for(ParamWithEI bestResult : bestResults)
		{
			configArrayToDebug[j++] = bestResult.getValue().toValueArray();
		}
		log.info("Local Search Selected Configurations Hash Code {}", matlabHashCode(configArrayToDebug));
		int nextRandom = SeedableRandomSingleton.getRandom().nextInt();
		log.info("Next Int {}", nextRandom);
		//=== Generate random configurations 
		int numberOfRandomConfigsInEI = smacConfig.numberOfRandomConfigsInEI;
		log.warn("Hard coded number of configurations for random to 10");
		numberOfRandomConfigsInEI = 10;
		List<ParamConfiguration> randomConfigs = new ArrayList<ParamConfiguration>(numberOfRandomConfigsInEI);
		for(int i=0; i < numberOfRandomConfigsInEI; i++)
		{
			randomConfigs.add(configSpace.getRandomConfiguration());
		}
		
		
		double[][] randomConfigToDebug = new double[randomConfigs.size()][];
		for(int i=0; i < randomConfigs.size(); i++)
		{
			randomConfigToDebug[i] = randomConfigs.get(i).toValueArray();
		}
		log.info("Local Search Selected Random Configs Hash Code: {}", matlabHashCode(randomConfigToDebug));
		//=== Compute EI for the random configs.		
		predictions = transpose(applyMarginalModel(randomConfigs));
		predmean = predictions[0];
		predvar = predictions[1]; 
		double[] expectedImprovementOfRandoms = ei.computeNegativeExpectedImprovement(quality, predmean, predvar);
		
		for(int i=0; i <  randomConfigs.size(); i++)
		{
			double[] val = { predmean[i], predvar[i], expectedImprovementOfRandoms[i] };
			configPredMeanVarEIMap.put(randomConfigs.get(i), val);
		}
		
		//=== Add random configs to LS configs.
		bestResults.addAll(ParamWithEI.merge(expectedImprovementOfRandoms, randomConfigs));

		//== More debugging.
		configArrayToDebug = new double[bestResults.size()][];
		j=0; 
		for(ParamWithEI eic : bestResults)
		{
			configArrayToDebug[j++] = eic.getValue().toValueArray();
		}
		//log.debug("Local Search Selected Configurations & Random Configs Hash Code: {}", matlabHashCode(configArrayToDebug));
		
		
		
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
		
		
		//=== Sort configs by EI and output top ones.
		
		bestResults = permute(bestResults);
		Collections.sort(bestResults);
		
		
		
	
		
		for(int i=0; i < numberOfSearches; i++)
		{
			double[] meanvar = configPredMeanVarEIMap.get(bestResults.get(i).getValue());
			Object[] args = {i+1, meanvar[0], Math.sqrt(meanvar[1]), -meanvar[2]}; 
			log.info("Challenger {} predicted {} +/- {}, expected improvement {}",args);
		}
		
		//=== Make result list of configurations. 
		List<ParamConfiguration> results = new ArrayList<ParamConfiguration>(bestResults.size());
		for(ParamWithEI eic : bestResults)
		{
			results.add(eic.getValue());
		}
		
		//== More debugging.
		configArrayToDebug = new double[results.size()][];
		j=0; 
		for(ParamConfiguration c : results)
		{
			configArrayToDebug[j++] = c.toValueArray();
		}
		log.debug("Re-sorted Local Search Selected Configurations & Randm Configs Hash Code {}", matlabHashCode(configArrayToDebug));
		
		return Collections.unmodifiableList(results);	
	}
	
	StaticMethodWrapper smw = new StaticMethodWrapper();
	
	/**
	 * Performs a local search starting from the specified start configuration  
	 * @param eic - Parameter configuration coupled with it's expected improvement 
	 * @param fmin_sample - The best performance (f_min) to beat
	 * @param epsilon - Minimum value of improvement required before terminating
	 * @return best Param&EI Found
	 */
	private ParamWithEI localSearch(ParamWithEI startEIC, double fmin_sample, double epsilon)
	{
		ParamWithEI incumbentEIC = startEIC;
		
		int localSearchSteps = 0;
		
		while(true)
		{
			localSearchSteps++;
			if(localSearchSteps % 1000 == 0)
			{
				log.warn("Local Search has done {} iterations, possible infinite loop", localSearchSteps );
			}
			
			//=== Get EI of current configuration.
			ParamConfiguration c = incumbentEIC.getValue();
			double currentMinEI = incumbentEIC.getAssociatedValue();
			
			//System.out.println("minEI: " + currentMinEI + " incumbent: " + c.hashCode());
			double[][] cArray = {c.toValueArray()};
			int LSHashCode = matlabHashCode(cArray);
			log.debug("Local Search HashCode: {}", LSHashCode);
			
			//=== Get neighbourhood of current options and compute EI for all of it.
			List<ParamConfiguration> neighbourhood = c.getNeighbourhood();
			double[][] prediction = transpose(applyMarginalModel(neighbourhood));
			double[] means = prediction[0];
			double[] vars = prediction[1];
			double[] eiVal = ei.computeNegativeExpectedImprovement(fmin_sample, means, vars); 
			
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
				int nextIdx = minIdx.get(SeedableRandomSingleton.getRandom().nextInt(minIdx.size()));
				ParamConfiguration best = neighbourhood.get(nextIdx);
				incumbentEIC = new ParamWithEI(eiVal[nextIdx], best);
				
				//==== Matlab code always uses the min even if we didn't select it.
				//incumbentEIC = new ParamWithEI(min, best);
				
				double[][] next = { best.toValueArray() };
				double[][] predictions = transpose(applyMarginalModel(next));
				double[] mean = predictions[0];
				double[] var = predictions[1];
				
				eiVal = ei.computeNegativeExpectedImprovement(fmin_sample, mean, var);
				Object[] args = {eiVal[0], mean[0], var[0]};
				log.trace("Expected improvement for next step is {} mean={}, var={}",args);
				
				
				
			}
		}
		log.debug("Local Search End Hash Code: {}",smw.matlabHashSingular(incumbentEIC.getValue().toValueArray()));
		log.debug("Local Search took {} steps", localSearchSteps);
		
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
	protected double[][] applyMarginalModel(List<ParamConfiguration> configs)
	{
		//=== Translate into array format, and call method for that format.
		double[][] configArrays = new double[configs.size()][];
		int i=0; 
		for(ParamConfiguration config: configs)
		{
			configArrays[i] = config.toValueArray();
			i++;
		}
		return applyMarginalModel(configArrays);		
	}
	
}
