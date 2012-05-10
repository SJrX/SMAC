package ca.ubc.cs.beta.junit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import ca.ubc.cs.beta.configspace.ParamConfiguration;
import ca.ubc.cs.beta.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.configspace.ParamFileHelper;


public class RandomConfiguration {

	
	
	public boolean allValues(Map<String, Integer> possibleValues, Map<String, Set<String>> seenValues)
	{
		
		for(String key : possibleValues.keySet())
		{
			
			if(seenValues.get(key).size() == possibleValues.get(key))
			{
				continue;
			} 
			return false;
		}
		return true;
	}
	
	/**
	 * This tests to make sure that getRandomConfiguration() returns every possible value that we expect
	 */
	@Test
	public void testAllValuesAppear() {
		ParamConfigurationSpace f = ParamFileHelper.getParamFileParser(("/ubc/cs/home/s/seramage/arrowspace/sm/sample_inputs/spear-params.txt"));
		
	
		Map<String,Integer> possibleValues =  new HashMap<String, Integer>(); 
		Map<String, Set<String>> seenValues = new HashMap<String, Set<String>>();
		
		
		for(String param : f.getParameterNames())
		{
			possibleValues.put(param, f.getValuesMap().get(param).size());
			seenValues.put(param, new HashSet<String>());
		}
		
		int i=0;
		
		while(!allValues(possibleValues, seenValues))
		{
			ParamConfiguration config = f.getRandomConfiguration();
			
			for(String key : config.keySet())
			{
				seenValues.get(key).add(config.get(key));
			}
			System.out.println("Iteration: " + i++ );
		}
		
		
		
		
		
	}

}
