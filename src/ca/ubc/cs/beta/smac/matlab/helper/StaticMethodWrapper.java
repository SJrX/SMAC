package ca.ubc.cs.beta.smac.matlab.helper;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration;
import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.aclib.configspace.ParamFileHelper;
import ca.ubc.cs.beta.aclib.expectedimprovement.ExpectedExponentialImprovement;
import ca.ubc.cs.beta.aclib.misc.math.ArrayMathOps;
import ca.ubc.cs.beta.aclib.misc.random.SeedableRandomSingleton;
import ca.ubc.cs.beta.aclib.probleminstance.InstanceListWithSeeds;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstanceHelper;
import ca.ubc.cs.beta.aclib.seedgenerator.InstanceSeedGenerator;
import ca.ubc.cs.beta.aclib.seedgenerator.RandomInstanceSeedGenerator;
import ca.ubc.cs.beta.models.fastrf.RandomForest;
import ca.ubc.cs.beta.models.fastrf.RegtreeBuildParams;
import ec.util.MersenneTwister;

public class StaticMethodWrapper implements Serializable{

	private static InstanceSeedGenerator instanceSeedGenerator;
	private static int instanceCount = 0;
	
	
	
	public ParamConfiguration getRandomConfiguration(ParamConfigurationSpace p)
	{
	
		return p.getRandomConfiguration();
	}
	
	public ParamConfigurationSpace getParamFileParser(String s, long seedForRandomSampling)
	{
		return ParamFileHelper.getParamFileParser(s, seedForRandomSampling);
	}
	
	public ParamConfigurationSpace getParamFileParser(File f, long seedForRandomSampling)
	{
		return ParamFileHelper.getParamFileParser(f, seedForRandomSampling);
	}
	
	public ParamConfiguration fromString(ParamConfigurationSpace configSpace, String paramString)
	{
		return configSpace.getConfigurationFromString(paramString, ParamConfiguration.StringFormat.STATEFILE_SYNTAX);
	}
	public void reinitSeed()
	{
		ParamFileHelper.clear();
		SeedableRandomSingleton.reinit();
	}
	
	public void setSeedOrInit(long seed)
	{
		ParamFileHelper.clear();
		SeedableRandomSingleton.setSeed(seed);
		
		SeedableRandomSingleton.reinit();
		if(seed != SeedableRandomSingleton.getSeed())
		{
			throw new IllegalStateException("Seed could not be changed, perhaps you need to run clear java");
		}
		
		instanceSeedGenerator = new RandomInstanceSeedGenerator(instanceCount, seed);
		
	}
	
	public Random getTreeRandom()
	{
		return new MersenneTwister(24);
	}
	public Random getRandomSingleton()
	{
		return SeedableRandomSingleton.getRandom();
	}
	
	/**
	 * Matlab wants a permutation of numbers (1,n) we have a permutation from (0,n) so we translate to (1,n)
	 * @param n
	 * @return
	 */
	public int[] getPermutation(int n)
	{
		return SeedableRandomSingleton.getPermutation(n, 1);
	}
	
	public int getNextSeed(int instanceID)
	{
		return instanceSeedGenerator.getNextSeed(instanceID);
	}
	
	public void setInstanceCount(int instanceCount)
	{
		StaticMethodWrapper.instanceCount= instanceCount;
	}
	
	public RandomForest getRandomForest(int numTrees, double[][] allTheta, double[][] allX, int[][] theta_inst_idxs, double[] y, RegtreeBuildParams params)
	{
		return RandomForest.learnModel(numTrees, allTheta, allX, theta_inst_idxs, y, params);
	}
	
	public int matlabHashCode(double[][] matrix)
	{
		return ArrayMathOps.matlabHashCode(matrix);
	}
	
	public int matlabHashSingular(double[] matrix)
	{
		//System.out.println(Arrays.toString(matrix));
		double[][] obj = { matrix };
		return matlabHashCode(obj);
	}
	
	
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		for( Method m : StaticMethodWrapper.class.getMethods())
		{
			sb.append(m.getName()).append("=>(");
			for(Class<?> classes : m.getParameterTypes())
			{
				sb.append(classes.getName());
				sb.append(",");
			}
			sb.append("\n");
		}
		return sb.toString();
	}
	private static ExpectedExponentialImprovement eei = new ExpectedExponentialImprovement();
	
	public double expImp(double f_min, double mean, double var)
	{
		double[] predmean = { mean };
		double[] predvar = { var };
		double value = eei.computeNegativeExpectedImprovement(f_min, predmean, predvar)[0];
		
		 
		 return value;
	}

	public double[] expImp(double f_min, double[] mean, double[] var)
	{
		return eei.computeNegativeExpectedImprovement(f_min, mean, var);
	}
	public double[][] getNeighbours(ParamConfigurationSpace configSpace, double[] x)
	{
		ParamConfiguration config = configSpace.getConfigurationFromValueArray(x);
		List<ParamConfiguration> neighbours = config.getNeighbourhood();
		
		double[][] results = new double[neighbours.size()][];
		for(int i=0; i < results.length; i++)
		{
			results[i] = neighbours.get(i).toValueArray();
		}
				
		return results;
	}
	
	public double[][] getFeatures(String instanceFile, String experimentDir, String featureFileName)
	{
		
		try {
			InstanceListWithSeeds ilws = ProblemInstanceHelper.getInstances(instanceFile, experimentDir, featureFileName, false);
			
			double[][] features = new double[ilws.getInstances().size()][];
			for(int i=0; i < ilws.getInstances().size(); i++)
			{
				features[i] = ilws.getInstances().get(i).getFeaturesDouble();
			}
			
			return features;
		} catch (IOException e) {

			throw new RuntimeException(e);
		}
		
		
	}
}
