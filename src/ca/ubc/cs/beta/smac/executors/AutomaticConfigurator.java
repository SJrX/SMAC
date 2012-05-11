package ca.ubc.cs.beta.smac.executors;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.ubc.cs.beta.ac.config.AlgorithmExecutionConfig;
import ca.ubc.cs.beta.ac.config.ProblemInstance;
import ca.ubc.cs.beta.config.SMACConfig;
import ca.ubc.cs.beta.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.configspace.ParamFileHelper;
import ca.ubc.cs.beta.probleminstance.ProblemInstanceHelper;
import ca.ubc.cs.beta.smac.AbstractAlgorithmFramework;
import ca.ubc.cs.beta.smac.OverallObjective;
import ca.ubc.cs.beta.smac.RunObjective;
import ca.ubc.cs.beta.smac.RunHashCodeVerifyingAlgorithmEvalutor;
import ca.ubc.cs.beta.smac.SequentialModelBasedAlgorithmConfiguration;
import ca.ubc.cs.beta.smac.ac.runners.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.smac.model.builder.HashCodeVerifyingModelBuilder;
import ca.ubc.cs.beta.smac.state.StateDeserializer;
import ca.ubc.cs.beta.smac.state.StateFactory;
import ca.ubc.cs.beta.smac.state.legacy.LegacyStateFactory;
import ca.ubc.cs.beta.smac.state.nullFactory.NullStateFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class AutomaticConfigurator 
{

	private static List<ProblemInstance> instances;
	private static List<ProblemInstance> testInstances;
	private static Logger logger;
	private static Marker exception;
	private static Marker stackTrace;
	public static void main(String[] args)
	{
		/*
		 * WARNING: DO NOT LOG ANYTHING UNTIL AFTER WE HAVE PARSED THE CLI OPTIONS
		 * AS THE CLI OPTIONS USE A TRICK TO ALLOW LOGGING TO BE CONFIGURABLE ON THE CLI
		 * IF YOU LOG PRIOR TO IT ACTIVATING, IT WILL BE IGNORED 
		 */
		try {
			SMACConfig config = parseCLIOptions(args);
			
			
			logger.info("Automatic Configuration Started");
			
			logger.info(config.toString());
			
			
			
			/*
			 * Build the Serializer object used in the model 
			 */
			StateFactory restoreSF;
			switch(config.statedeSerializer)
			{
				case NULL:
					restoreSF = new NullStateFactory();
					break;
				case LEGACY:
					restoreSF = new LegacyStateFactory(config.outputDirectory + File.separator + config.runID + File.separator + "state" + File.separator, config.restoreStateFrom);
					break;
				default:
					throw new IllegalArgumentException("State Serializer specified is not supported");
			}
			
			logger.info("Parsing Parameter Space File", config.paramFile.getAbsolutePath());
			ParamConfigurationSpace configSpace = ParamFileHelper.getParamFileParser(config.paramFile);
			
			
			
			AlgorithmExecutionConfig execConfig = new AlgorithmExecutionConfig(config.algoExec, config.rootDir, configSpace, false);
		
			
			
			TargetAlgorithmEvaluator algoEval;
			if(config.runHashCodeFile != null)
			{
				logger.info("Algorithm Execution will verify run Hash Codes");
				Queue<Integer> runHashCodes = parseRunHashCodes(config.runHashCodeFile);
				algoEval = new RunHashCodeVerifyingAlgorithmEvalutor(execConfig, runHashCodes);
				 
			} else
			{
				logger.info("Algorithm Execution will NOT verify run Hash Codes");
				//TODO Seperate the generation of Run Hash Codes from 
				algoEval = new RunHashCodeVerifyingAlgorithmEvalutor(execConfig);
			}

			if(config.modelHashCodeFile != null)
			{
				logger.info("Algorithm Execution will verify model Hash Codes");
				parseModelHashCodes(config.runHashCodeFile);
				
			
			}
			
			StateFactory sf;
			
			switch(config.stateSerializer)
			{
				case NULL:
					sf = new NullStateFactory();
					break;
				case LEGACY:
					sf = new LegacyStateFactory(config.outputDirectory + File.separator + config.runID + File.separator + "state" + File.separator, config.restoreStateFrom);
					break;
				default:
					throw new IllegalArgumentException("State Serializer specified is not supported");
			}
			
			
			
			AbstractAlgorithmFramework smac;
			switch(config.execMode)
			{
				case ROAR:
					smac = new AbstractAlgorithmFramework(config,instances, testInstances,algoEval,sf);
					break;
				case SMAC:
					smac = new SequentialModelBasedAlgorithmConfiguration(config, instances, testInstances, algoEval, config.expFunc.getFunction(),sf);
					break;
				default:
					throw new IllegalArgumentException("Execution Mode Specified is not supported");
			}
			
			if(config.restoreIteration != null)
			{
				restoreState(config, restoreSF, smac, configSpace,config.overallObj,config.runObj, instances, execConfig);
			}
			
				
			smac.run();
			return;
		} catch(Throwable t)
		{
			
				if(logger != null)
				{
					
					logger.error(exception, "Exception:{}", t.getClass().getCanonicalName());
					logger.error(exception, "Message: {}",t.getMessage());
					StringWriter sWriter = new StringWriter();
					PrintWriter writer = new PrintWriter(sWriter);
					t.printStackTrace(writer);
					logger.error(stackTrace, "StackTrace:{}",sWriter.toString());
					logger.info("Exiting Application with failure");
					t = t.getCause();
				} else
				{
					if(t instanceof ParameterException )
					{
						System.err.println(t.getMessage());
					} else
					{
						t.printStackTrace();
					}
					
				}
					
			
		}
		
		
	}
	



	private static void restoreState(SMACConfig config, StateFactory sf, AbstractAlgorithmFramework smac,  ParamConfigurationSpace configSpace, OverallObjective overallObj, RunObjective runObj, List<ProblemInstance> instances, AlgorithmExecutionConfig execConfig) {
		
		if(config.restoreIteration < 0)
		{
			throw new ParameterException("Iteration must be a non-negative integer");
		}
		
		StateDeserializer sd = sf.getStateDeserializer("it", config.restoreIteration, configSpace, overallObj, runObj, instances, execConfig);
		
		smac.restoreState(sd);
		
		
		
	}




	/**
	 * Parsers Command Line Arguments and returns a config object
	 * @param args
	 * @return
	 */
	private static SMACConfig parseCLIOptions(String[] args) throws ParameterException, IOException
	{
		//DO NOT LOG UNTIL AFTER WE PARSE CONFIG OBJECT
		SMACConfig config = new SMACConfig();
		JCommander com = new JCommander(config);
		com.setProgramName("smac");
		try {
			com.parse(args);
			
			File outputDir = new File(config.outputDirectory);
			if(!outputDir.exists())
			{
				outputDir.mkdir();
			}
			
			System.setProperty("OUTPUTDIR", config.outputDirectory);
			System.setProperty("RUNID", config.runID);
			
			logger = LoggerFactory.getLogger(AutomaticConfigurator.class);
			exception = MarkerFactory.getMarker("EXCEPTION");
			stackTrace = MarkerFactory.getMarker("STACKTRACE");
			
			
			
			logger.trace("Command Line Options Parsed");
			logger.info("Parsing instances from {}", config.instanceFile );
			instances = ProblemInstanceHelper.getInstances(config.instanceFile,config.experimentDir, config.instanceFeatureFile, true);
			
			logger.info("Parsing test instances from {}", config.instanceFile );
			testInstances = ProblemInstanceHelper.getInstances(config.testInstanceFile, config.experimentDir, true);
			
			return config;
		} catch(IOException e)
		{
			com.usage();
			throw e;
			
		} catch(ParameterException e)
		{
			com.usage();
			throw e;
		}
	}
	

	
	
	
	private static Pattern runHashCodePattern = Pattern.compile("^Run Hash Codes:\\d+( After \\d+ runs)?\\z");
	
	private static Pattern modelHashCodePattern = Pattern.compile("^(Preprocessed|Random) Forest Built with Hash Code:\\s*\\d+?\\z");
	
	
	private static void parseModelHashCodes(File modelHashCodeFile) {
		logger.info("Model Hash Code File Passed {}", modelHashCodeFile.getAbsolutePath());
		Queue<Integer> modelHashCodeQueue = new LinkedList<Integer>();
		Queue<Integer> preprocessedHashCodeQueue = new LinkedList<Integer>();
		
		BufferedReader bin = null;
		try {
			try{
				bin = new BufferedReader(new FileReader(modelHashCodeFile));
			
				String line;
				int hashCodeCount=0;
				int lineCount = 1;
				while((line = bin.readLine()) != null)
				{
					
					Matcher m = modelHashCodePattern.matcher(line);
					if(m.find())
					{
						Object[] array = { ++hashCodeCount, lineCount, line};
						logger.debug("Found Model Hash Code #{} on line #{} with contents:{}", array);
						boolean preprocessed = line.substring(0,1).equals("P");
						
						int colonIndex = line.indexOf(":");
						
						String lineSubStr = line.substring(colonIndex+1).trim();
						
						if(!preprocessed)
						{
							modelHashCodeQueue.add(Integer.valueOf(lineSubStr));
						} else
						{
							preprocessedHashCodeQueue.add(Integer.valueOf(lineSubStr));
						}
						
					} else
					{
						logger.trace("No Hash Code found on line: {}", line );
					}
					lineCount++;
				}
				if(hashCodeCount == 0)
				{
					logger.warn("Hash Code File Specified, but we found no hash codes");
				}
			} finally
			{
				if(bin != null) bin.close();
			}
		} catch(IOException e)
		{
			throw new RuntimeException(e);
		}
		
		//Who ever is looking at this code, I can feel your disgust.
		HashCodeVerifyingModelBuilder.modelHashes = modelHashCodeQueue;
		HashCodeVerifyingModelBuilder.preprocessedHashes = preprocessedHashCodeQueue;
		
	}
	
	
	private static Queue<Integer> parseRunHashCodes(File runHashCodeFile) 
	{
		logger.info("Run Hash Code File Passed {}", runHashCodeFile.getAbsolutePath());
		Queue<Integer> runHashCodeQueue = new LinkedList<Integer>();
		BufferedReader bin = null;
		try {
			try{
				bin = new BufferedReader(new FileReader(runHashCodeFile));
			
				String line;
				int hashCodeCount=0;
				int lineCount = 1;
				while((line = bin.readLine()) != null)
				{
					
					Matcher m = runHashCodePattern.matcher(line);
					if(m.find())
					{
						Object[] array = { ++hashCodeCount, lineCount, line};
						logger.debug("Found Run Hash Code #{} on line #{} with contents:{}", array);
						int colonIndex = line.indexOf(":");
						int spaceIndex = line.indexOf(" ", colonIndex);
						String lineSubStr = line.substring(colonIndex+1,spaceIndex);
						runHashCodeQueue.add(Integer.valueOf(lineSubStr));
						
					} else
					{
						logger.trace("No Hash Code found on line: {}", line );
					}
					lineCount++;
				}
				if(hashCodeCount == 0)
				{
					logger.warn("Hash Code File Specified, but we found no hash codes");
				}
			
			} finally
			{
				if(bin != null) bin.close();
			}
			
		} catch(IOException e)
		{
			throw new RuntimeException(e);
		}
		
		
		return runHashCodeQueue;
		
	}

	
}
