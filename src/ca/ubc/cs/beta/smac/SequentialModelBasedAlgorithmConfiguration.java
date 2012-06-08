package ca.ubc.cs.beta.smac;

import java.util.ArrayList;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.cs.beta.ac.config.ProblemInstance;
import ca.ubc.cs.beta.config.SMACConfig;
import ca.ubc.cs.beta.configspace.ParamConfiguration;
import ca.ubc.cs.beta.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.models.fastrf.RandomForest;
import ca.ubc.cs.beta.random.SeedableRandomSingleton;
import ca.ubc.cs.beta.smac.ac.runners.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.smac.helper.ParamWithEI;
import ca.ubc.cs.beta.smac.history.RunHistory;
import ca.ubc.cs.beta.smac.model.builder.AdaptiveCappingModelBuilder;
import ca.ubc.cs.beta.smac.model.builder.BasicModelBuilder;
import ca.ubc.cs.beta.smac.model.builder.ModelBuilder;
import ca.ubc.cs.beta.smac.model.data.PCAModelDataSanitizer;
import ca.ubc.cs.beta.smac.state.StateFactory;
import ca.ubc.cs.beta.smac.util.AutoStartStopWatch;
import ca.ubc.cs.beta.smac.util.StopWatch;
import ei.ExpectedImprovementFunction;
import static ca.ubc.cs.beta.smac.helper.ArrayMathOps.*;

public class SequentialModelBasedAlgorithmConfiguration extends
		AbstractAlgorithmFramework {

	private final int numPCA;
	private final boolean logModel;
	private final Logger log = LoggerFactory.getLogger(this.getClass());
	private final SMACConfig smacConfig;
	
	/**
	 * Most recent forest built
	 */
	private RandomForest forest;
	
	/**
	 * Most recent prepared forest built (may be NULL but always corresponds to the last forest built)
	 */
	private RandomForest preparedForest;
	
	/**
	 * Last build of sanatized data
	 */
	private PCAModelDataSanitizer sdm;
	private final ExpectedImprovementFunction ei;
	
	
	public SequentialModelBasedAlgorithmConfiguration(SMACConfig smacConfig, List<ProblemInstance> instances, List<ProblemInstance> testInstances, TargetAlgorithmEvaluator algoEval, ExpectedImprovementFunction ei, StateFactory sf, ParamConfigurationSpace configSpace) {
		super(smacConfig, instances, testInstances, algoEval,sf, configSpace);
		numPCA = smacConfig.numPCA;
		logModel = smacConfig.randomForestConfig.logModel;
		this.smacConfig = smacConfig;
		this.ei = ei;
	}

	
	@Override
	protected void learnModel(RunHistory runHistory, ParamConfigurationSpace configSpace) 
	{
		/**
		 * These sets should be sorted by instance and paramConfig ID;
		 */
		
		Set<ProblemInstance> all_instances = new LinkedHashSet<ProblemInstance>(instances);
		
		Set<ParamConfiguration> paramConfigs = runHistory.getUniqueParamConfigurations();
		
		int i=0; 
		double[][] instanceFeatures = new double[all_instances.size()][];
		
		Set<ProblemInstance> runInstances=runHistory.getUniqueInstancesRan();
		
		ArrayList<Integer> runInstancesIdx = new ArrayList<Integer>(all_instances.size());
		
		for(ProblemInstance pi : all_instances)
		{
			
			if(runInstances.contains(pi))
			{
				runInstancesIdx.add(i);
			}
			instanceFeatures[i] = pi.getFeaturesDouble();
			i++;
			
		}
		int[] usedInstanceIdxs = new int[runInstancesIdx.size()]; 
		for(int j=0; j <  runInstancesIdx.size(); j++)
		{
			usedInstanceIdxs[j] = runInstancesIdx.get(j);
		}
		
				
		double[][] paramValues = new double[paramConfigs.size()][];
		i = 0;
		for(ParamConfiguration pc : paramConfigs)
		{
			paramValues[i++] = pc.toValueArray();
		}
		
		double[] runResponseValues = runHistory.getRunResponseValues();
	
		
		sdm = new PCAModelDataSanitizer(instanceFeatures, paramValues, numPCA, runResponseValues, usedInstanceIdxs, logModel, configSpace);
		
		
		/**
		 * Create unique 
		 */
		//You can change this back to a BasicModelBuilder
		ModelBuilder mb;
		if(config.adaptiveCapping)
		{
			mb = new AdaptiveCappingModelBuilder(sdm,smacConfig.randomForestConfig, runHistory, rand, smacConfig.imputationIterations, smacConfig.scenarioConfig.cutoffTime, smacConfig.scenarioConfig.overallObj.getPenaltyFactor());
		} else
		{
			mb = new BasicModelBuilder(sdm, smacConfig.randomForestConfig, runHistory); 
		}
		 /*= new HashCodeVerifyingModelBuilder(sdm,smacConfig.randomForestConfig, runHistory);*/
		forest = mb.getRandomForest();
		preparedForest = mb.getPreparedRandomForest();
	
		log.info("Random Forest Built");
	}
	
	protected List<ParamConfiguration> selectConfigurations()
	{
		
		
		double[][] marginals = applyMarginalModel();
		
		List<ParamConfiguration> eichallengers = selectChallengersWithEI(smacConfig.numberOfChallengers);

		
		List<ParamConfiguration> randomChallengers = new ArrayList<ParamConfiguration>(eichallengers.size());
		log.info("Generating {} random configurations", eichallengers.size());
		for(int i=0; i < eichallengers.size(); i++)
		{
			randomChallengers.add(configSpace.getRandomConfiguration());
		}
		
		
		List<ParamConfiguration> challengers = new ArrayList<ParamConfiguration>(eichallengers.size()*2);
		
		
		
		/**
		 * Interleave the lists
		 */
		for(int i=0; i < eichallengers.size(); i++)
		{
			challengers.add(eichallengers.get(i));
			challengers.add(randomChallengers.get(i));
		}
		
		
		

		double[][] configArrayToDebug = new double[challengers.size()][];
		int j=0; 
		for(ParamConfiguration c : challengers)
		{
			configArrayToDebug[j++] = c.toValueArray();
		}
		
		log.info("Final Selected Challengers Configurations Hash Code {}", matlabHashCode(configArrayToDebug));
		
		return challengers;
	
		//return super.selectConfigurations();
	}
	
	public List<ParamConfiguration> selectChallengersWithEI(int numChallengers)
	{
		Set<ProblemInstance> instanceSet = new HashSet<ProblemInstance>();
		instanceSet.addAll(runHistory.getInstancesRan(incumbent));
		
		double quality = runHistory.getEmpiricalCost(incumbent, instanceSet, smacConfig.scenarioConfig.cutoffTime);
		
		if (smacConfig.randomForestConfig.logModel)
		{
			//TODO HANDLE MIN RUNTIME THIS IS SO A BUG
			//THIS IS SO A BUG
			//THIS IS SO A BUG 
			quality = Math.log10(quality);
		}
		
		/**
		 * Get predictions for everything we have run thus far
		 */
		List<ParamConfiguration> paramConfigs = runHistory.getAllParameterConfigurationsRan();
		Map<ParamConfiguration, double[]> configPredMeanVarEIMap = new LinkedHashMap<ParamConfiguration, double[]>();
		
		double[][] predictions = transpose(applyMarginalModel(paramConfigs));
		double[] predmean = predictions[0];
		double[] predvar = predictions[1];
		
		
		
		
		log.info("Optimizing EI at valdata.iteration {}", getIteration());
		StopWatch watch = new AutoStartStopWatch();
		double[] expectedImprovementOfTheta = ei.computeNegativeExpectedImprovement(quality, predmean, predvar);
		watch.stop();
		log.info("Compute negEI for all conf. seen at valdata.iteration {}: took {} s",getIteration(), watch.time() / 1000.0 );
		for(int i=0; i < paramConfigs.size(); i++)
		{
			double[] val = { predmean[i], predvar[i], expectedImprovementOfTheta[i] };
			configPredMeanVarEIMap.put(paramConfigs.get(i), val);
		}
		
		List<ParamWithEI> sortedParams = ParamWithEI.merge(expectedImprovementOfTheta, paramConfigs);
		
	
		Collections.sort(sortedParams);
		int numStartConfigs = numChallengers;
		int numPrevConfigs = numStartConfigs;
		
		
		
		int numberOfSearches = Math.min(numChallengers, predmean.length);
		List<ParamWithEI> bestResults  = new ArrayList<ParamWithEI>(numberOfSearches);
		double min_neg = Double.MAX_VALUE;
		/**
		 * Local Search for best candidate
		 */
		for(int i=0; i < numberOfSearches; i++)
		{
			watch = new AutoStartStopWatch();
			
			//double[][] cArray = { sortedParams.get(i).getValue().toValueArray() };
			
			ParamWithEI bestSearch = localSearch(sortedParams.get(i), quality, Math.pow(10, -5));
			if(bestSearch.getAssociatedValue() < min_neg)
			{
				min_neg = bestSearch.getAssociatedValue();
			}
			
			predictions = transpose(applyMarginalModel(Collections.singletonList(bestSearch.getValue())));
			double[] val = { predictions[0][0], predictions[1][0], bestSearch.getAssociatedValue()};
			
			bestResults.add(bestSearch);
			configPredMeanVarEIMap.put(bestSearch.getValue(), val);
			watch.stop();
			Object[] args = {i+1,watch.time()/1000,min_neg};
			//cArray[0] = bestSearch.getValue().toValueArray();
			//log.debug("Local Search End Hash Code: {}", matlabHashCode(cArray));
			log.info("LS {} took {} seconds and yielded neg log EI {}",args);
			 
		
		}
		
		double[][] configArrayToDebug = new double[bestResults.size()][];
		int j=0; 
		for(ParamWithEI bestResult : bestResults)
		{
			configArrayToDebug[j++] = bestResult.getValue().toValueArray();
		}
		
		log.info("Local Search Selected Configurations Hash Code {}", matlabHashCode(configArrayToDebug));
		
		/**
		 * Generate random configurations
		 */
		
		int numberOfRandomConfigsInEI = smacConfig.numberOfRandomConfigsInEI;
		
		List<ParamConfiguration> randomConfigs = new ArrayList<ParamConfiguration>(numberOfRandomConfigsInEI);
		
		for(int i=0; i < numberOfRandomConfigsInEI; i++)
		{
			randomConfigs.add(configSpace.getRandomConfiguration());
			
		}
		predictions = transpose(applyMarginalModel(randomConfigs));
		predmean = predictions[0];
		predvar = predictions[1]; 
		double[] expectedImprovementOfRandoms = ei.computeNegativeExpectedImprovement(quality, predmean, predvar);
		
		
		for(int i=0; i <  randomConfigs.size(); i++)
		{
			double[] val = { predmean[i], predvar[i], expectedImprovementOfRandoms[i] };
			configPredMeanVarEIMap.put(randomConfigs.get(i), val);
		}
		
		
		bestResults.addAll(ParamWithEI.merge(expectedImprovementOfRandoms, randomConfigs));
		
		configArrayToDebug = new double[bestResults.size()][];
		j=0; 
		for(ParamWithEI eic : bestResults)
		{
			configArrayToDebug[j++] = eic.getValue().toValueArray();
		}
		
		log.debug("Local Search Selected Configurations & Random Configs Hash Code: {}", matlabHashCode(configArrayToDebug));
		
		bestResults =permute(bestResults);
		Collections.sort(bestResults);
		
		
		
		for(int i=0; i < numberOfSearches; i++)
		{
			
			
			double[] meanvar = configPredMeanVarEIMap.get(bestResults.get(i).getValue());
			Object[] args = {i+1, meanvar[0], Math.sqrt(meanvar[1]), meanvar[2]}; 
			log.info("Challenger {} predicted {} +/- {}, expected improvement {}",args);
		}
		
		
		
		List<ParamConfiguration> results = new ArrayList<ParamConfiguration>(bestResults.size());
		for(ParamWithEI eic : bestResults)
		{
			results.add(eic.getValue());
		}
		//
		
		configArrayToDebug = new double[results.size()][];
		j=0; 
		for(ParamConfiguration c : results)
		{
			configArrayToDebug[j++] = c.toValueArray();
		}
		
		log.debug("Select Challengers Configurations Hash Code {}", matlabHashCode(configArrayToDebug));

		return Collections.unmodifiableList(results);	
	}
	
	/**
	 * Performs a local search around the Parameter specified 
	 * @param eic - Parameter Coupled with it's Expected Improvement 
	 * @param fmin_sample - 
	 * @param epsilon - Minimum value of improvement required before terminating
	 * @return best Param&EI Found
	 */
	private ParamWithEI localSearch(ParamWithEI startEIC, double fmin_sample, double epsilon)
	{
		
		ParamWithEI incumbentEIC = startEIC;
		while(true)
		{
		
			
			ParamConfiguration c = incumbentEIC.getValue();
			double minEI = incumbentEIC.getAssociatedValue();
			
			
			double[][] cArray = {c.toValueArray()};
			int LSHashCode = matlabHashCode(cArray);
			log.trace("Local Search Start Hash Code: {}", LSHashCode);
			
			List<ParamConfiguration> neighbourhood = c.getNeighbourhood();
			

			
			
			double[][] prediction = transpose(applyMarginalModel(neighbourhood));
			
			double[] means = prediction[0];
			double[] vars = prediction[1];
			
			
			double[] eiVal = ei.computeNegativeExpectedImprovement(fmin_sample, means,vars); 
			
			
			
			List<ParamConfiguration> minConfigs = new ArrayList<ParamConfiguration>(c.size());
			
			
			double min = eiVal[0];
			for(int i=1; i < eiVal.length; i++)
			{
				if(eiVal[i] < min)
				{
					min = eiVal[i];
				}
			}
		
			
			
			if(min >= minEI - epsilon)
			{
				break;
			} else
			{
	
				for(int i=0; i < eiVal.length; i++)
				{
					if(eiVal[i] <= min + epsilon)
					{
						minEI = eiVal[i];
						minConfigs.add(neighbourhood.get(i));
					} 
				}
				int nextIdx = SeedableRandomSingleton.getRandom().nextInt(minConfigs.size());
				ParamConfiguration best = minConfigs.get(nextIdx);
				
				incumbentEIC = new ParamWithEI(eiVal[nextIdx], best);
				//return localSearch(new ParamWithEI(minEI, best), fmin_sample, epsilon);
			}
		}
		
		return incumbentEIC;
	}

	
	protected double[][] applyMarginalModel(double[][] configArrays)
	{
		int[] treesToSearch = new int[forest.numTrees];
		
		for(int i=0; i <  forest.numTrees; i++)
		{
			treesToSearch[i]=i;
		}
		
		
		
		if(smacConfig.randomForestConfig.preprocessMarginal)
		{
			return RandomForest.applyMarginal(preparedForest,treesToSearch,configArrays);
		} else
		{
			return RandomForest.applyMarginal(forest,treesToSearch,configArrays,sdm.getPCAFeatures());
		}
		
	}
	protected double[][] applyMarginalModel(List<ParamConfiguration> configs)
	{
		double[][] configArrays = new double[configs.size()][];
		int i=0; 
		for(ParamConfiguration config: configs)
		{
			configArrays[i] = config.toValueArray();
			i++;
		}
		

		return applyMarginalModel(configArrays);
		
	}
	
	protected double[][] applyMarginalModel()
	{
		return applyMarginalModel(runHistory.getAllConfigurationsRanInValueArrayForm());
	}
	
}
