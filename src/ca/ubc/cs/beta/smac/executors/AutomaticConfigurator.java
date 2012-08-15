package ca.ubc.cs.beta.smac.executors;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Random;
import java.util.SortedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.aclib.configspace.ParamFileHelper;
import ca.ubc.cs.beta.aclib.exceptions.StateSerializationException;
import ca.ubc.cs.beta.aclib.exceptions.TrajectoryDivergenceException;
import ca.ubc.cs.beta.aclib.execconfig.AlgorithmExecutionConfig;

import ca.ubc.cs.beta.aclib.misc.logback.MarkerFilter;
import ca.ubc.cs.beta.aclib.misc.logging.LoggingMarker;
import ca.ubc.cs.beta.aclib.misc.random.SeedableRandomSingleton;
import ca.ubc.cs.beta.aclib.misc.version.VersionTracker;
import ca.ubc.cs.beta.aclib.model.builder.HashCodeVerifyingModelBuilder;
import ca.ubc.cs.beta.aclib.objectives.OverallObjective;
import ca.ubc.cs.beta.aclib.objectives.RunObjective;
import ca.ubc.cs.beta.aclib.options.SMACOptions;
import ca.ubc.cs.beta.aclib.options.ScenarioOptions;
import ca.ubc.cs.beta.aclib.probleminstance.InstanceListWithSeeds;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstanceHelper;
import ca.ubc.cs.beta.aclib.seedgenerator.InstanceSeedGenerator;
import ca.ubc.cs.beta.aclib.state.StateDeserializer;
import ca.ubc.cs.beta.aclib.state.StateFactory;
import ca.ubc.cs.beta.aclib.state.legacy.LegacyStateFactory;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluatorFactory;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.decorators.AbortOnCrashTargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.decorators.AbortOnFirstRunCrashTargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.decorators.RetryCrashedRunsTargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.decorators.RunHashCodeVerifyingAlgorithmEvalutor;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.loader.TargetAlgorithmEvaluatorLoader;
import ca.ubc.cs.beta.aclib.trajectoryfile.TrajectoryFileEntry;
import ca.ubc.cs.beta.smac.AbstractAlgorithmFramework;
import ca.ubc.cs.beta.smac.SequentialModelBasedAlgorithmConfiguration;
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
	
	
	/**
	 * Executes SMAC then exits the JVM {@see System.exit()}
	 *  
	 * @param args string arguments
	 */
	public static void main(String[] args)
	{
		int returnValue = oldMain(args);
		
		logger.info("Returning with value: {}",returnValue);
		
		System.exit(returnValue);
	}
	
	
	/**
	 * Executes SMAC according to the given arguments
	 * @param args 	string input arguments
	 * @return return value for operating system
	 */
	public static int oldMain(String[] args)
	{
		/*
		 * WARNING: DO NOT LOG ANYTHING UNTIL AFTER WE HAVE PARSED THE CLI OPTIONS
		 * AS THE CLI OPTIONS USE A TRICK TO ALLOW LOGGING TO BE CONFIGURABLE ON THE CLI
		 * IF YOU LOG PRIOR TO IT ACTIVATING, IT WILL BE IGNORED 
		 */
		try {
			SMACOptions options = parseCLIOptions(args);
			
			
			logger.info("Automatic Configurator Started");
			
			
			
			SeedableRandomSingleton.setSeed(options.numRun + options.seedOffset);
			Random rand = SeedableRandomSingleton.getRandom(); 

			
			/*
			 * Build the Serializer object used in the model 
			 */
			StateFactory restoreSF;
			switch(options.statedeSerializer)
			{
				case NULL:
					restoreSF = new NullStateFactory();
					break;
				case LEGACY:
					restoreSF = new LegacyStateFactory(options.scenarioConfig.outputDirectory + File.separator + options.runGroupName + File.separator + "state-run" + options.numRun + File.separator, options.restoreStateFrom);
					break;
				default:
					throw new IllegalArgumentException("State Serializer specified is not supported");
			}
			
			String paramFile = options.scenarioConfig.paramFileDelegate.paramFile;
			logger.info("Parsing Parameter Space File", paramFile);
			ParamConfigurationSpace configSpace = null;
			
			
			String[] possiblePaths = { paramFile, options.experimentDir + File.separator + paramFile, options.scenarioConfig.algoExecOptions.algoExecDir + File.separator + paramFile }; 
			for(String path : possiblePaths)
			{
				try {
					logger.debug("Trying param file in path {} ", path);
					
					configSpace = ParamFileHelper.getParamFileParser(path, options.numRun + options.seedOffset +1000000);
					break;
				} catch(IllegalStateException e)
				{ 
				
				}
			}
			
			
			if(configSpace == null)
			{
				throw new ParameterException("Could not find param file");
			}
			
			String algoExecDir = options.scenarioConfig.algoExecOptions.algoExecDir;
			File f2 = new File(algoExecDir);
			if (!f2.isAbsolute()){
				f2 = new File(options.experimentDir + File.separator + algoExecDir);
			}
			AlgorithmExecutionConfig execConfig = new AlgorithmExecutionConfig(options.scenarioConfig.algoExecOptions.algoExec, f2.getAbsolutePath(), configSpace, false, options.scenarioConfig.algoExecOptions.deterministic, options.scenarioConfig.cutoffTime );
		
			
			
			
			StateFactory sf;
			
			switch(options.stateSerializer)
			{
				case NULL:
					sf = new NullStateFactory();
					break;
				case LEGACY:
					sf = new LegacyStateFactory(options.scenarioConfig.outputDirectory + File.separator + options.runGroupName + File.separator + "state-run" + options.numRun + File.separator, options.restoreStateFrom);
					break;
				default:
					throw new IllegalArgumentException("State Serializer specified is not supported");
			}
			
			
			TargetAlgorithmEvaluator algoEval = TargetAlgorithmEvaluatorFactory.getTargetAlgorithmEvaluator(options.scenarioConfig, execConfig);
			

			if(options.modelHashCodeFile != null)
			{
				logger.info("Algorithm Execution will verify model Hash Codes");
				parseModelHashCodes(options.modelHashCodeFile);
			}
			
			
			
			AbstractAlgorithmFramework smac;
			switch(options.execMode)
			{
				case ROAR:
					smac = new AbstractAlgorithmFramework(options,instances, testInstances,algoEval,sf, configSpace, instanceSeedGen, rand);
					break;
				case SMAC:
					smac = new SequentialModelBasedAlgorithmConfiguration(options, instances, testInstances, algoEval, options.expFunc.getFunction(),sf, configSpace, instanceSeedGen, rand);
					break;
				default:
					throw new IllegalArgumentException("Execution Mode Specified is not supported");
			}
			
			if(options.restoreIteration != null)
			{
				restoreState(options, restoreSF, smac, configSpace,options.scenarioConfig.intraInstanceObj,options.scenarioConfig.interInstanceObj,options.scenarioConfig.runObj, instances, execConfig);
			}
			
				
			smac.run();
			if(options.doValidation)
			{
				
				
				//Don't use the same TargetAlgorithmEvaluator as above as it may have runhashcode and other validation crap that is probably not applicable here
				
				
				if(options.validationOptions.maxTimestamp == -1)
				{
					options.validationOptions.maxTimestamp = options.scenarioConfig.tunerTimeout;
				}
				
				TargetAlgorithmEvaluator validatingTae =TargetAlgorithmEvaluatorFactory.getTargetAlgorithmEvaluator(options.scenarioConfig, execConfig, false);
				String outputDir = options.scenarioConfig.outputDirectory + File.separator + options.runGroupName + File.separator;
				
				List<TrajectoryFileEntry> tfes = smac.getTrajectoryFileEntries();
				
				
				SortedMap<TrajectoryFileEntry, Double> performance = (new Validator()).validate(testInstances,options.validationOptions,options.scenarioConfig.cutoffTime, testInstanceSeedGen, validatingTae, outputDir, options.scenarioConfig.runObj, options.scenarioConfig.intraInstanceObj, options.scenarioConfig.interInstanceObj, tfes, options.numRun);
				smac.afterValidationStatistics();
				smac.logSMACResult(performance);
				
				
			}
			
			logger.info("SMAC Completed Successfully");
			
			
			return SMACReturnValues.SUCCESS;
		} catch(Throwable t)
		{
			System.out.flush();
			System.err.flush();
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
		
				
				if(t instanceof ParameterException)
				{
					return SMACReturnValues.PARAMETER_EXCEPTION;
				}
				
				if(t instanceof StateSerializationException)
				{
					return SMACReturnValues.SERIALIZATION_EXCEPTION;
				}
				
				if(t instanceof TrajectoryDivergenceException)
				{
					return SMACReturnValues.TRAJECTORY_DIVERGENCE;
				}
				
				return SMACReturnValues.OTHER_EXCEPTION;
		}
		
		
	}
	

	

	
	private static void restoreState(SMACOptions options, StateFactory sf, AbstractAlgorithmFramework smac,  ParamConfigurationSpace configSpace, OverallObjective intraInstanceObjective, OverallObjective interInstanceObjective, RunObjective runObj, List<ProblemInstance> instances, AlgorithmExecutionConfig execConfig) {
		
		if(options.restoreIteration < 0)
		{
			throw new ParameterException("Iteration must be a non-negative integer");
		}
		
		StateDeserializer sd = sf.getStateDeserializer("it", options.restoreIteration, configSpace, intraInstanceObjective, interInstanceObjective, runObj, instances, execConfig);
		
		smac.restoreState(sd);
		
		
		
	}




	/**
	 * Parsers Command Line Arguments and returns a options object
	 * @param args
	 * @return
	 */
	private static SMACOptions parseCLIOptions(String[] args) throws ParameterException, IOException
	{
		//DO NOT LOG UNTIL AFTER WE PARSE CONFIG OBJECT
		SMACOptions config = new SMACOptions();
		JCommander com = new JCommander(config, true, true);
		com.setProgramName("smac");
		try {
			
			
			//JCommanderHelper.parse(com, args);
			try {
				com.parse(args);
				
				File outputDir = new File(config.scenarioConfig.outputDirectory);
				if(!outputDir.exists())
				{
					outputDir.mkdir();
				}
				
				
				
			} finally
			{
				System.setProperty("OUTPUTDIR", config.scenarioConfig.outputDirectory);
				System.setProperty("RUNGROUPDIR", config.runGroupName);
				System.setProperty("NUMRUN", String.valueOf(config.numRun));
				System.setProperty("STDOUT-LEVEL", config.consoleLogLevel.name());
				System.setProperty("ROOT-LEVEL",config.logLevel.name());
				
				System.out.println("*****************************\nLogging to: " + config.scenarioConfig.outputDirectory + File.separator + config.runGroupName + File.separator + "log-run" + config.numRun+ ".txt\n*****************************");
				//${OUTPUTDIR}/${RUNGROUPDIR}/log-run${NUMRUN}.txt
				logger = LoggerFactory.getLogger(AutomaticConfigurator.class);
				exception = MarkerFactory.getMarker("EXCEPTION");
				stackTrace = MarkerFactory.getMarker("STACKTRACE");
				
				//VersionTracker.loadVersionFromClassPath("SMAC", "smac-version.txt");
				VersionTracker.logVersions();
				
				
			}
			
			logger.trace("Command Line Options Parsed");
			
			
			
			if(config.adaptiveCapping == null)
			{
				switch(config.scenarioConfig.runObj)
				{
				case RUNTIME:
					config.adaptiveCapping = true;
				case QUALITY:
					config.adaptiveCapping = false;
				}
			}
			
			
			
			
			
			validateObjectiveCombinations(config.scenarioConfig, config.adaptiveCapping);
			
			logCallString(args);
			
			if(config.scenarioConfig.algoExecOptions.logAllCallStrings)
			{
				MarkerFilter.accept(LoggingMarker.COMMAND_LINE_CALL);
			} else
			{
				MarkerFilter.deny(LoggingMarker.COMMAND_LINE_CALL);
			}
			
			if(config.scenarioConfig.algoExecOptions.logAllProcessOutput)
			{
				MarkerFilter.accept(LoggingMarker.FULL_PROCESS_OUTPUT);
			} else
			{
				MarkerFilter.deny(LoggingMarker.FULL_PROCESS_OUTPUT);
			}
			
			
			if(config.logLevel.lessVerbose(config.consoleLogLevel))
			{
				logger.warn("The console has been set to be more verbose than the log. This is generally an error, except if you have modified the conf.xml to have certain loggers be more specific");
				//throw new ParameterException("The console can NOT be more verbose than the logs (This will have no effect)");
				
			}
				
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
			{ //If this fails it's okay we just use it to output to the log
				
			}
			
			logger.info("Hostname:{}\n\n", hostname);
			logger.info("==========System Properties==============\n{}", sb.toString() );
			 
			
			logger.info("==========Configuration Options==========\n{}", config.toString());
			 
			 
			logger.info("Parsing instances from {}", config.scenarioConfig.instanceFile );
			InstanceListWithSeeds ilws;
			ilws = ProblemInstanceHelper.getInstances(config.scenarioConfig.instanceFile,config.experimentDir, config.scenarioConfig.instanceFeatureFile, config.scenarioConfig.checkInstanceFilesExist, config.numRun+config.seedOffset+1, (config.scenarioConfig.algoExecOptions.deterministic));
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
			ilws = ProblemInstanceHelper.getInstances(config.scenarioConfig.testInstanceFile, config.experimentDir, null, config.scenarioConfig.checkInstanceFilesExist, config.numRun+config.seedOffset+2,(config.scenarioConfig.algoExecOptions.deterministic ) );
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
			
			
			
			
			
			
			
				
			List<String> names = TargetAlgorithmEvaluatorFactory.getAvailableTargetAlgorithmEvaluators(config.scenarioConfig.algoExecOptions);
			
			for(String name : names)
			{
				logger.debug("Target Algorithm Evaluator Available {} ", name);
			}
			
			try {
				//We don't handle this more gracefully because this seems like a super rare incident.
				if(ManagementFactory.getThreadMXBean().isThreadCpuTimeEnabled())
				{
					logger.debug("JVM Supports CPU Timing Measurements");
				} else
				{
					logger.warn("This Java Virtual Machine has CPU Time Measurements disabled, tunerTimeout will not contain any SMAC Execution Time.");
				}
			} catch(UnsupportedOperationException e)
			{
				logger.warn("This Java Virtual Machine does not support CPU Time Measurements, tunerTimeout will not contain any SMAC Execution Time Information (http://docs.oracle.com/javase/1.5.0/docs/api/java/lang/management/ThreadMXBean.html#setThreadCpuTimeEnabled(boolean))");
			}
			
			if(config.numRun + config.seedOffset < 0)
			{
				logger.warn("NumRun {} plus Seed Offset {} should be positive, things may not seed correctly",config.numRun, config.seedOffset );
			}
			return config;
		} catch(IOException e)
		{
			com.setColumnSize(getConsoleSize());
			com.usage();
			throw e;
			
		} catch(ParameterException e)
		{
			com.setColumnSize(getConsoleSize());
			com.usage();
			throw e;
		}
	}
	

	
	/**
	 * Validates the various objective functions and ensures that they are legal together
	 * @param scenarioConfig
	 */
	private static void validateObjectiveCombinations(
			ScenarioOptions scenarioConfig, boolean adaptiveCapping) {

		switch(scenarioConfig.interInstanceObj)
		{
			case MEAN:
				//Okay
				break;
			default:
				throw new ParameterException("Model does not currently support an inter-instance objective other than " +  OverallObjective.MEAN);
				
		}
		
		
		
		
		switch(scenarioConfig.runObj)
		{
			case RUNTIME:
				break;
			
			case QUALITY:
				if(!scenarioConfig.intraInstanceObj.equals(OverallObjective.MEAN))
				{
					throw new ParameterException("To optimize quality you MUST use an intra-instance objective of " + OverallObjective.MEAN);
				}
				
				if(adaptiveCapping)
				{
					throw new ParameterException("You can only use Adaptive Capping when using " + RunObjective.RUNTIME + " as an objective");
				}
				
		}
	}


	/**
	 * Makes a best at our column size 
	 * @return
	 */
	private static int getConsoleSize() {
		//Tried using tputs but apparently java destroys it and always gets an 80, I'll have to do some more trickery
		
		//Anyway lets make it wider atleast
		return 160;
	}


	private static void logCallString(String[] args) {
		StringBuilder sb = new StringBuilder("java -cp ");
		sb.append(System.getProperty("java.class.path")).append(" ");
		sb.append(AutomaticConfigurator.class.getCanonicalName()).append(" ");
		for(String arg : args)
		{
			boolean escape = false;
			if(arg.contains(" "))
			{
				escape = true;
				arg = arg.replaceAll(" ", "\\ ");
			}
			
			
			if(escape) sb.append("\"");
			sb.append(arg);
			if(escape) 	sb.append("\"");
			sb.append(" ");
		}
		
		logger.info("Call String:");
		logger.info("{}", sb.toString());
	}





	
	
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
	
	
	

	
}
