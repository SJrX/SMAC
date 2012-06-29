package ca.ubc.cs.beta.smac.executors;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.ubc.cs.beta.config.AlgorithmExecutionConfig;
import ca.ubc.cs.beta.config.JCommanderHelper;
import ca.ubc.cs.beta.config.SMACConfig;
import ca.ubc.cs.beta.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.configspace.ParamFileHelper;
import ca.ubc.cs.beta.probleminstance.InstanceListWithSeeds;
import ca.ubc.cs.beta.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.probleminstance.ProblemInstanceHelper;
import ca.ubc.cs.beta.random.SeedableRandomSingleton;
import ca.ubc.cs.beta.seedgenerator.InstanceSeedGenerator;
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
import ca.ubc.cs.beta.smac.validation.Validator;

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
	private static InstanceSeedGenerator instanceSeedGen;
	private static InstanceSeedGenerator testInstanceSeedGen;
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
			
			
			
			SeedableRandomSingleton.setSeed(config.seed);
			Random rand = SeedableRandomSingleton.getRandom(); 

			
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
					restoreSF = new LegacyStateFactory(config.scenarioConfig.outputDirectory + File.separator + config.runGroupName + File.separator + "state-run" + config.seed + File.separator, config.restoreStateFrom);
					break;
				default:
					throw new IllegalArgumentException("State Serializer specified is not supported");
			}
			
			String paramFile = config.scenarioConfig.paramFileDelegate.paramFile;
			logger.info("Parsing Parameter Space File", paramFile);
			ParamConfigurationSpace configSpace = null;
			
			
			String[] possiblePaths = { paramFile, config.experimentDir + File.separator + paramFile, config.scenarioConfig.algoExecConfig.algoExecDir + File.separator + paramFile }; 
			for(String path : possiblePaths)
			{
				try {
					logger.debug("Trying param file in path {} ", path);
					configSpace = ParamFileHelper.getParamFileParser(path, config.seed+1000000);
					break;
				} catch(IllegalStateException e)
				{ 
				
				}
			}
			
			
			if(configSpace == null)
			{
				throw new ParameterException("Could not find param file");
			}
			
			String algoExecDir = config.scenarioConfig.algoExecConfig.algoExecDir;
			File f2 = new File(algoExecDir);
			if (!f2.isAbsolute()){
				f2 = new File(config.experimentDir + File.separator + algoExecDir);
			}
			AlgorithmExecutionConfig execConfig = new AlgorithmExecutionConfig(config.scenarioConfig.algoExecConfig.algoExec, f2.getAbsolutePath(), configSpace, false);
		
			TargetAlgorithmEvaluator algoEval;
			boolean concurrentRuns = (config.maxConcurrentAlgoExecs > 1);
			if(config.runHashCodeFile != null)
			{
				logger.info("Algorithm Execution will verify run Hash Codes");
				Queue<Integer> runHashCodes = parseRunHashCodes(config.runHashCodeFile);
				algoEval = new RunHashCodeVerifyingAlgorithmEvalutor(execConfig, runHashCodes, concurrentRuns);
				 
			} else
			{
				logger.info("Algorithm Execution will NOT verify run Hash Codes");
				//TODO Seperate the generation of Run Hash Codes from verifying them
				algoEval = new RunHashCodeVerifyingAlgorithmEvalutor(execConfig, concurrentRuns);
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
					sf = new LegacyStateFactory(config.scenarioConfig.outputDirectory + File.separator + config.runGroupName + File.separator + "state-run" + config.seed + File.separator, config.restoreStateFrom);
					break;
				default:
					throw new IllegalArgumentException("State Serializer specified is not supported");
			}
			
			
			
			AbstractAlgorithmFramework smac;
			switch(config.execMode)
			{
				case ROAR:
					smac = new AbstractAlgorithmFramework(config,instances, testInstances,algoEval,sf, configSpace, instanceSeedGen, rand);
					break;
				case SMAC:
					smac = new SequentialModelBasedAlgorithmConfiguration(config, instances, testInstances, algoEval, config.expFunc.getFunction(),sf, configSpace, instanceSeedGen, rand);
					break;
				default:
					throw new IllegalArgumentException("Execution Mode Specified is not supported");
			}
			
			if(config.restoreIteration != null)
			{
				restoreState(config, restoreSF, smac, configSpace,config.scenarioConfig.overallObj,config.scenarioConfig.runObj, instances, execConfig);
			}
			
				
			smac.run();
			if(!config.skipValidation)
			{
			
				TargetAlgorithmEvaluator validatingTae = new TargetAlgorithmEvaluator(execConfig, concurrentRuns);
				String outputDir = config.scenarioConfig.outputDirectory + File.separator + config.runGroupName + File.separator;
				
				double tunerTime = smac.getTunerTime();
				double cpuTime = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() / 1000.0 / 1000 / 1000;;
				double empericalPerformance = smac.getEmpericalPerformance(smac.getIncumbent());
				
				(new Validator()).validate(testInstances, smac.getIncumbent(),config.validationOptions,config.scenarioConfig.cutoffTime, testInstanceSeedGen, validatingTae, outputDir, config.scenarioConfig.runObj, config.scenarioConfig.overallObj, tunerTime, empericalPerformance, cpuTime, config.seed);
			}
			
			logger.info("SMAC Completed Successfully");
			
			
			return;
		} catch(Throwable t)
		{
			
				if(logger != null)
				{
					
					logger.error(exception, "Message: {}",t.getMessage());

					if(!(t instanceof ParameterException))
					{
						logger.error(exception, "Exception:{}", t.getClass().getCanonicalName());
						StringWriter sWriter = new StringWriter();
						PrintWriter writer = new PrintWriter(sWriter);
						t.printStackTrace(writer);
						logger.error(stackTrace, "StackTrace:{}",sWriter.toString());
					}
					
						
					
					
					
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
			
			
			JCommanderHelper.parse(com, args);
			//com.parse(args);
			
			File outputDir = new File(config.scenarioConfig.outputDirectory);
			if(!outputDir.exists())
			{
				outputDir.mkdir();
			}
			
			System.setProperty("OUTPUTDIR", config.scenarioConfig.outputDirectory);
			System.setProperty("RUNGROUPDIR", config.runGroupName);
			System.setProperty("NUMRUN", String.valueOf(config.seed));
			System.setProperty("STDOUT-LEVEL", config.consoleLogLevel.name());
			
			logger = LoggerFactory.getLogger(AutomaticConfigurator.class);
			exception = MarkerFactory.getMarker("EXCEPTION");
			stackTrace = MarkerFactory.getMarker("STACKTRACE");
			
			logger.trace("Command Line Options Parsed");
			
			Map<String, String> env = System.getenv();
			
			StringBuilder sb = new StringBuilder();
			 for (String envName : env.keySet()) {
				 sb.append(envName).append("=").append(env.get(envName)).append("\n");
				 
		           
		        }
			
			
			 logger.info("==========Enviroment Variables===========\n{}", sb.toString());
			 Map<Object,Object > props = System.getProperties();
			 sb = new StringBuilder();
			 for (Entry<Object, Object> ent : props.entrySet())
			 {
				 
				 sb.append(ent.getKey().toString()).append("=").append(ent.getValue().toString()).append("\n");
				 
		           
		     }
			
			 String hostname = "[UNABLE TO DETERMINE HOSTNAME]";
			try {
				hostname = InetAddress.getLocalHost().getHostName();
			} catch(UnknownHostException e)
			{ //Don't care about this exception
				
			}
			
			logger.info("Hostname:{}\n\n", hostname);
			logger.info("==========System Properties==============\n{}", sb.toString() );
			 
			
			logger.info("==========Configuration Options==========\n{}", config.toString());
			 
			 
			logger.info("Parsing instances from {}", config.scenarioConfig.instanceFile );
			InstanceListWithSeeds ilws;
			ilws = ProblemInstanceHelper.getInstances(config.scenarioConfig.instanceFile,config.experimentDir, config.scenarioConfig.instanceFeatureFile, !config.scenarioConfig.skipInstanceFileCheck, config.seed+1, (config.scenarioConfig.deterministic > 0));
			instanceSeedGen = ilws.getSeedGen();
			
			logger.info("Instance Seed Generator reports {} seeds ", instanceSeedGen.getInitialInstanceSeedCount());
			if(instanceSeedGen.allInstancesHaveSameNumberOfSeeds())
			{
				logger.info("Instance Seed Generator reports that all instances have the same number of available seeds");
			} else
			{
				logger.error("Instance Seed Generator reports that some instances have a different number of seeds than others");
				throw new ParameterException("All Training Instances must have the same number of seeds in this version of SMAC");
			}
			
			instances = ilws.getInstances();
			
			
			logger.info("Parsing test instances from {}", config.scenarioConfig.testInstanceFile );
			ilws = ProblemInstanceHelper.getInstances(config.scenarioConfig.testInstanceFile, config.experimentDir, null, !config.scenarioConfig.skipInstanceFileCheck, config.seed+2,(config.scenarioConfig.deterministic > 0) );
			testInstances = ilws.getInstances();
			testInstanceSeedGen = ilws.getSeedGen();
			
			logger.info("Test Instance Seed Generator reports {} seeds ", testInstanceSeedGen.getInitialInstanceSeedCount());
			if(testInstanceSeedGen.allInstancesHaveSameNumberOfSeeds())
			{
				logger.info("Test Seed Generator reports that all instances have the same number of available seeds");
			} else
			{
				logger.info("Test Seed Generator reports that the number of seeds per instance varies.");
			}
			
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
