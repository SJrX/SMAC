package ca.ubc.cs.beta.executors;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import ca.ubc.cs.beta.config.RandomConfigParameters;
import ca.ubc.cs.beta.configspace.ParamConfiguration;
import ca.ubc.cs.beta.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.random.SeedableRandomSingleton;

public class DefaultConfigGen {
	
	public static void main(String[] args)
	{
		RandomConfigParameters rcp = new RandomConfigParameters();
		JCommander com = new JCommander(rcp);
		
		try {
		
		com.parse(args);
		
		
		ParamConfigurationSpace configSpace = new ParamConfigurationSpace(rcp.parameterFile);
		
		if(rcp.seed > 0)
		{
			SeedableRandomSingleton.setSeed(rcp.seed);
		} 
		
		StringBuilder sb = new StringBuilder();
		
		sb.append("number, dummy_non_parameter_1, dummy_non_parameter_2, dummy_non_parameter_3, dummy_non_parameter_4, ");
		for(String configName : configSpace.getParameterNamesInAuthorativeOrder())
		{
			sb.append(configName).append(", ");
		}
		sb.append("\n");
		
		
		
		ParamConfiguration config = configSpace.getDefaultConfiguration();
		sb.append("0, -1, 0, 0, 0, ");
		
		
		
		
		for(String configName : configSpace.getParameterNamesInAuthorativeOrder())
		{
			sb.append(config.get(configName)).append(", ");
		}
		sb.append("\n");
	
		
		
		String s = sb.toString();
		s = s.replaceAll(", \n", "\n");
		
		
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(rcp.outputFile));
			
			out.write(s);
			System.out.println("[INFO]: File output written to: " + rcp.outputFile.getAbsolutePath());
			
			out.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//System.out.println(s);
		
		} catch(ParameterException e)
		{
			System.err.println(e.getMessage());
			StringBuilder sb = new StringBuilder();
			com.usage(sb);
			System.err.println(sb.toString());
		}
	}

}
