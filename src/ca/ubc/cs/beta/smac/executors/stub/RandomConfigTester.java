package ca.ubc.cs.beta.smac.executors.stub;

import org.apache.commons.math.stat.StatUtils;

import ca.ubc.cs.beta.configspace.ParamConfiguration;
import ca.ubc.cs.beta.configspace.ParamConfigurationSpace;

public class RandomConfigTester {

	public static void main(String[] args)
	{
		ParamConfigurationSpace sp = new ParamConfigurationSpace("/ubc/cs/project/arrow/hutter/experiments/algorithms/spear/spear-params.txt");
	
		int N = 10000;
		double[] vals = new double[N];
		//We will ignore the first value as java hasn't warmed up yet.
		for(int j=0; j <= N; j++)
		{
		
		
			long time = System.currentTimeMillis();
			ParamConfiguration c = null;
			for(int i=0; i < 10000; i++)
			{
				c = sp.getRandomConfiguration();
			}
			if(j == 0) continue;
			
			vals[j-1] = System.currentTimeMillis() - time;
			//System.out.println(vals[j-1]);
			//System.out.println(c);
		}
		System.out.println("MEAN:" + StatUtils.mean(vals));
		System.out.println("STDDEV:" + Math.sqrt(StatUtils.variance(vals)));
	}
}
