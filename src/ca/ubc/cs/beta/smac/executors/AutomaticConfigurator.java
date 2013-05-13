package ca.ubc.cs.beta.smac.executors;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration;
import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration.StringFormat;
import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.aclib.configspace.ParamFileHelper;
import ca.ubc.cs.beta.aclib.events.EventManager;
import ca.ubc.cs.beta.aclib.exceptions.FeatureNotFoundException;
import ca.ubc.cs.beta.aclib.exceptions.StateSerializationException;
import ca.ubc.cs.beta.aclib.exceptions.TrajectoryDivergenceException;
import ca.ubc.cs.beta.aclib.execconfig.AlgorithmExecutionConfig;

import ca.ubc.cs.beta.aclib.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.aclib.misc.random.SeedableRandomSingleton;
import ca.ubc.cs.beta.aclib.misc.returnvalues.ACLibReturnValues;
import ca.ubc.cs.beta.aclib.misc.version.VersionTracker;
import ca.ubc.cs.beta.aclib.model.builder.HashCodeVerifyingModelBuilder;
import ca.ubc.cs.beta.aclib.objectives.OverallObjective;
import ca.ubc.cs.beta.aclib.objectives.RunObjective;
import ca.ubc.cs.beta.aclib.options.AbstractOptions;
import ca.ubc.cs.beta.aclib.options.ConfigToLaTeX;
import ca.ubc.cs.beta.aclib.options.SMACOptions;
import ca.ubc.cs.beta.aclib.options.ScenarioOptions;
import ca.ubc.cs.beta.aclib.probleminstance.InstanceListWithSeeds;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstanceHelper;
import ca.ubc.cs.beta.aclib.seedgenerator.InstanceSeedGenerator;
import ca.ubc.cs.beta.aclib.state.StateDeserializer;
import ca.ubc.cs.beta.aclib.state.StateFactory;
import ca.ubc.cs.beta.aclib.state.legacy.LegacyStateFactory;
import ca.ubc.cs.beta.aclib.state.nullFactory.NullStateFactory;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluatorBuilder;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.loader.TargetAlgorithmEvaluatorLoader;
import ca.ubc.cs.beta.aclib.trajectoryfile.TrajectoryFileEntry;
import ca.ubc.cs.beta.smac.AbstractAlgorithmFramework;
import ca.ubc.cs.beta.smac.SequentialModelBasedAlgorithmConfiguration;
import ca.ubc.cs.beta.smac.validation.Validator;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

import ec.util.MersenneTwister;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class AutomaticConfigurator 
{

	
	private static Logger logger;
	private static Marker exception;
	private static Marker stackTrace;
	
	private static List<ProblemInstance> instances;
	private static List<ProblemInstance> testInstances;
	
	private static InstanceSeedGenerator instanceSeedGen;
	private static InstanceSeedGenerator testInstanceSeedGen;
	private static String logLocation = "<NO LOG LOCATION SPECIFIED, FAILURE MUST HAVE OCCURED EARLY>";
	
	
	private static String instanceFileAbsolutePath;
	private static String instanceFeatureFileAbsolutePath;
	
	
	private static Map<String,  AbstractOptions> taeOptions;
	/**
	 * Executes SMAC then exits the JVM {@see System.exit()}
	 *  
	 * @param args string arguments
	 */
	public static void main(String[] args)
	{
		int returnValue = oldMain(args);
		
		if(logger != null)
		{
			logger.info("Returning with value: {}",returnValue);
		}
		
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
			
			String paramFile = options.scenarioConfig.algoExecOptions.paramFileDelegate.paramFile;
			logger.info("Parsing Parameter Space File", paramFile);
			ParamConfigurationSpace configSpace = null;
			
			
			String[] possiblePaths = { paramFile, options.experimentDir + File.separator + paramFile, options.scenarioConfig.algoExecOptions.algoExecDir + File.separator + paramFile };
			String lastParamFilePath = null;
			Random configSpacePRNG = new MersenneTwister(options.numRun + options.seedOffset +1000000);
			
			for(String path : possiblePaths)
			{
				try {
					logger.debug("Trying param file in path {} ", path);
					lastParamFilePath = path;
					//Map<String, String> subspace = options.scenarioConfig.paramFileDelegate.getSubspaceMap();
					configSpace = ParamFileHelper.getParamFileParser(path);
					break;
				}catch(IllegalStateException e)
				{ 
					if(e.getCause() instanceof FileNotFoundException)
					{
						//We don't care about this because we will just toss an exception if we don't find it
					} else
					{
						logger.warn("Error occured while trying to parse is {}"  , e.getMessage() );
					}
					
 
				}
			}
			
			if(configSpace == null)
			{
				throw new ParameterException("Could not find a valid parameter file, please check if there was a previous error");
			}
			
		
			
			
			
			
			String algoExecDir = options.scenarioConfig.algoExecOptions.algoExecDir;
			File f2 = new File(algoExecDir);
			if (!f2.isAbsolute()){
				f2 = new File(options.experimentDir + File.separator + algoExecDir);
			}
			AlgorithmExecutionConfig execConfig = new AlgorithmExecutionConfig(options.scenarioConfig.algoExecOptions.algoExec, f2.getAbsolutePath(), configSpace, false, options.scenarioConfig.algoExecOptions.deterministic, options.scenarioConfig.algoExecOptions.cutoffTime );
		
			
			
			
			StateFactory sf;
			
			switch(options.stateSerializer)
			{
				case NULL:
					sf = new NullStateFactory();
					break;
				case LEGACY:
					String savePath = options.scenarioConfig.outputDirectory + File.separator + options.runGroupName + File.separator + "state-run" + options.numRun + File.separator;
					
					File saveLocation = new File(savePath);
					if(!saveLocation.isAbsolute())
					{
						savePath = options.experimentDir + File.separator + savePath;
					}
					sf = new LegacyStateFactory(savePath, options.restoreStateFrom);
					break;
				default:
					throw new IllegalArgumentException("State Serializer specified is not supported");
			}
			
			
			
			if(options.scenarioConfig.algoExecOptions.taeOpts.verifySAT == null)
			{
				boolean verifySATCompatible = ProblemInstanceHelper.isVerifySATCompatible(instances);
				if(verifySATCompatible)
				{
					logger.debug("Instance Specific Information is compatible with Verifying SAT, enabling option");
					options.scenarioConfig.algoExecOptions.taeOpts.verifySAT = true;
				} else
				{
					logger.debug("Instance Specific Information is NOT compatible with Verifying SAT, disabling option");
					options.scenarioConfig.algoExecOptions.taeOpts.verifySAT = false;
				}
				
			
			} else if(options.scenarioConfig.algoExecOptions.taeOpts.verifySAT == true)
			{
				boolean verifySATCompatible = ProblemInstanceHelper.isVerifySATCompatible(instances);
				if(!verifySATCompatible)
				{
					logger.warn("Verify SAT set to true, but some instances have instance specific information that isn't in {SAT, SATISFIABLE, UNKNOWN, UNSAT, UNSATISFIABLE}");
				}
					
			}
			
			
			
			ParamConfiguration initialIncumbent = configSpace.getConfigurationFromString(options.initialIncumbent, StringFormat.NODB_SYNTAX);
		
			
			if(!initialIncumbent.equals(configSpace.getDefaultConfiguration()))
			{
				logger.info("Initial Incumbent set to \"{}\" ", initialIncumbent.getFormattedParamString(StringFormat.NODB_SYNTAX));
			} else
			{
				logger.info("Initial Incumbent is the default \"{}\" ", initialIncumbent.getFormattedParamString(StringFormat.NODB_SYNTAX));
			}
			
			
			TargetAlgorithmEvaluator algoEval = TargetAlgorithmEvaluatorBuilder.getTargetAlgorithmEvaluator(options.scenarioConfig.algoExecOptions.taeOpts, execConfig, true, taeOptions);
			

			if(options.modelHashCodeFile != null)
			{
				logger.info("Algorithm Execution will verify model Hash Codes");
				parseModelHashCodes(options.modelHashCodeFile);
			}
			
			EventManager eventManager = new EventManager();
			
			AbstractAlgorithmFramework smac;
			switch(options.execMode)
			{
				case ROAR:

					smac = new AbstractAlgorithmFramework(options,instances,algoEval,sf, configSpace, instanceSeedGen, rand, initialIncumbent, eventManager, configSpacePRNG);

					break;
				case SMAC:

					smac = new SequentialModelBasedAlgorithmConfiguration(options, instances, algoEval, options.expFunc.getFunction(),sf, configSpace, instanceSeedGen, rand, initialIncumbent, eventManager, configSpacePRNG);

					
					break;
				default:
					throw new IllegalArgumentException("Execution Mode Specified is not supported");
			}
			
			

			if(options.saveContextWithState)
			{
				sf.copyFileToStateDir("param-file.txt", new File(lastParamFilePath));
				
				if(instanceFileAbsolutePath != null)
				{
					sf.copyFileToStateDir("instances.txt", new File(instanceFileAbsolutePath));
				}
				
				if(instanceFeatureFileAbsolutePath != null)
				{
					sf.copyFileToStateDir("instance-features.txt", new File(instanceFeatureFileAbsolutePath));
				}
				
				if ((options.scenarioConfig.scenarioFile != null) && (options.scenarioConfig.scenarioFile.exists()))
				{
					sf.copyFileToStateDir("scenario.txt", options.scenarioConfig.scenarioFile);
				}
				
				
				
			}
			
			
			if(options.restoreIteration != null)
			{
				restoreState(options, restoreSF, smac, configSpace,options.scenarioConfig.intraInstanceObj,options.scenarioConfig.interInstanceObj,options.scenarioConfig.runObj, instances, execConfig);
			}
			
				
			smac.run();
			List<TrajectoryFileEntry> tfes = smac.getTrajectoryFileEntries();
			SortedMap<TrajectoryFileEntry, Double> performance;
			if(options.doValidation)
			{
			
				//Don't use the same TargetAlgorithmEvaluator as above as it may have runhashcode and other crap that is probably not applicable for validation
				
				if(options.validationOptions.maxTimestamp == -1)
				{
					options.validationOptions.maxTimestamp = options.scenarioConfig.tunerTimeout;
				}
				
				TargetAlgorithmEvaluator validatingTae =TargetAlgorithmEvaluatorBuilder.getTargetAlgorithmEvaluator(options.scenarioConfig.algoExecOptions.taeOpts, execConfig, false, taeOptions);
				String outputDir = options.scenarioConfig.outputDirectory + File.separator + options.runGroupName + File.separator;

				performance  = (new Validator()).validate(testInstances,options.validationOptions,options.scenarioConfig.algoExecOptions.cutoffTime, testInstanceSeedGen, validatingTae, outputDir, options.scenarioConfig.runObj, options.scenarioConfig.intraInstanceObj, options.scenarioConfig.interInstanceObj, tfes, options.numRun,true);	
			} else
			{
				performance = new TreeMap<TrajectoryFileEntry, Double>();
				performance.put(tfes.get(tfes.size()-1), Double.POSITIVE_INFINITY);
				
			}
			
			
			
			smac.logIncumbentPerformance(performance);
			smac.afterValidationStatistics();
			smac.logSMACResult(performance);
			
			
			logger.info("SMAC Completed Successfully. Log: " + logLocation);
			
			
			return ACLibReturnValues.SUCCESS;
		} catch(Throwable t)
		{
			System.out.flush();
			System.err.flush();
			
			System.err.println("Error occured running SMAC ( " + t.getClass().getSimpleName() + " : "+ t.getMessage() +  ")\nError Log: " + logLocation);
			System.err.flush();
			
				if(logger != null)
				{
					
					logger.error(exception, "Message: {}",t.getMessage());

					if(!(t instanceof ParameterException))
					{
						logger.info("Maybe try running in DEBUG mode if you are missing information");
						logger.error(exception, "Exception:{}", t.getClass().getCanonicalName());
						StringWriter sWriter = new StringWriter();
						PrintWriter writer = new PrintWriter(sWriter);
						t.printStackTrace(writer);
						logger.error(stackTrace, "StackTrace:{}",sWriter.toString());
						
					}
						
					logger.info("Exiting SMAC with failure. Log: " + logLocation);
				
					logger.info("Please see above for the available options. Further information is available in the following documents:");
					logger.info("- The FAQ (doc/faq.pdf) contains commonly asked questions regarding troubleshooting, and usage.");
					logger.info("- The Quickstart Guide (doc/quickstart.pdf) gives a simple example for getting up and running.");
					logger.info("- The Manual (doc/manual.pdf) contains detailed information on file format semantics.");

					
					
					t = t.getCause();
				} else
				{
					if(t instanceof ParameterException )
					{
						
						System.err.println(t.getMessage());
						t.printStackTrace();
					} else
					{
						t.printStackTrace();
					}
					
				}
		
				
				if(t instanceof ParameterException)
				{
					return ACLibReturnValues.PARAMETER_EXCEPTION;
				}
				
				if(t instanceof StateSerializationException)
				{
					return ACLibReturnValues.SERIALIZATION_EXCEPTION;
				}
				
				if(t instanceof TrajectoryDivergenceException)
				{
					return ACLibReturnValues.TRAJECTORY_DIVERGENCE;
				}
				
				return ACLibReturnValues.OTHER_EXCEPTION;
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
		taeOptions = TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators();
		SMACOptions config = new SMACOptions();
		JCommander com = JCommanderHelper.getJCommander(config, taeOptions);
		
		com.setProgramName("smac");
		try {
			
			
			//JCommanderHelper.parse(com, args);
			try {
				checkArgsForUsageScreenValues(args,config);
				args = processScenarioStateRestore(args);
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
				
				logLocation = config.scenarioConfig.outputDirectory + File.separator + config.runGroupName + File.separator + "log-run" + config.numRun+ ".txt";
				
				System.out.println("*****************************\nLogging to: " + logLocation +  "\n*****************************");
				//Generally has the format: ${OUTPUTDIR}/${RUNGROUPDIR}/log-run${NUMRUN}.txt
				logger = LoggerFactory.getLogger(AutomaticConfigurator.class);
				exception = MarkerFactory.getMarker("EXCEPTION");
				stackTrace = MarkerFactory.getMarker("STACKTRACE");
				
				VersionTracker.setClassLoader(TargetAlgorithmEvaluatorLoader.getClassLoader());
				VersionTracker.logVersions();
				
				
			}
			
			logger.trace("Command Line Options Parsed");
			
			
			
			if(config.adaptiveCapping == null)
			{
				switch(config.scenarioConfig.runObj)
				{
				case RUNTIME:
					config.adaptiveCapping = true;
					break;
					
				case QUALITY:
					config.adaptiveCapping = false;
					break;
					
				default:
					//You need to add something new here
					throw new IllegalStateException("Not sure what to default too");
				}
			}
			
			
			
			
			
			validateObjectiveCombinations(config.scenarioConfig, config.adaptiveCapping);
			
			logCallString(args);
			
			/*
			
			*/
			
			if(config.logLevel.lessVerbose(config.consoleLogLevel))
			{
				logger.warn("The console has been set to be more verbose than the log. This is generally an error, except if you have modified the conf.xml to have certain loggers be more specific");
				//throw new ParameterException("The console can NOT be more verbose than the logs (This will have no effect)");
				
			}
				
			
			
			 
			 if(logger.isDebugEnabled())
			 {
				Map<String, String> env = new TreeMap<String, String>(System.getenv());
					
				StringBuilder sb = new StringBuilder();
				 for (String envName : env.keySet()) {
					 sb.append(envName).append("=").append(env.get(envName)).append("\n");
					 
			           
			        }
				
				
					 
				 logger.debug("==========Enviroment Variables===========\n{}", sb.toString());
				 
				 
				 Map<Object,Object > props = new TreeMap<Object, Object>(System.getProperties());
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
				
				logger.debug("Hostname:{}", hostname);
				logger.debug("==========System Properties==============\n{}", sb.toString() );
			 }
			
		
			StringBuilder sb = new StringBuilder();
			for(Object o : com.getObjects())
			{
				sb.append(o.toString()).append("\n");
			}
				
			logger.info("==========Configuration Options==========\n{}", sb.toString());
			
			logger.info("Parsing instances from {}", config.scenarioConfig.instanceFile );
			InstanceListWithSeeds ilws;
			ilws = ProblemInstanceHelper.getInstances(config.scenarioConfig.instanceFile,config.experimentDir, config.scenarioConfig.instanceFeatureFile, config.scenarioConfig.checkInstanceFilesExist, config.numRun+config.seedOffset+1, (config.scenarioConfig.algoExecOptions.deterministic));
			
			instanceFileAbsolutePath = ilws.getInstanceFileAbsolutePath();
			instanceFeatureFileAbsolutePath = ilws.getInstanceFeatureFileAbsolutePath();
			
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
			
			try {
				ilws = ProblemInstanceHelper.getInstances(config.scenarioConfig.testInstanceFile, config.experimentDir, config.scenarioConfig.instanceFeatureFile, config.scenarioConfig.checkInstanceFilesExist, config.numRun+config.seedOffset+2,(config.scenarioConfig.algoExecOptions.deterministic ) );
				
			} catch(FeatureNotFoundException e)
			{
				ilws = ProblemInstanceHelper.getInstances(config.scenarioConfig.testInstanceFile, config.experimentDir, null, config.scenarioConfig.checkInstanceFilesExist, config.numRun+config.seedOffset+2,(config.scenarioConfig.algoExecOptions.deterministic ) );
			}
			
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
			//com.setColumnSize(getConsoleSize());
			try {
				ConfigToLaTeX.usage(ConfigToLaTeX.getParameters(com.getObjects().get(0), TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators()));
			} catch (Exception e1) {
				logger.error("Exception occured while trying to generate usage screen",e1);
				logger.error("This exception did NOT cause SMAC to crash");
			}
			throw e;
			
		} catch(ParameterException e)
		{
			try {
				ConfigToLaTeX.usage(ConfigToLaTeX.getParameters(com.getObjects().get(0),TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators()));
			} catch (Exception e1) {
				logger.error("Exception occured while trying to generate usage screen",e1);
				logger.error("This exception did NOT cause SMAC to crash");
			}
			
			throw e;
		}
	}
	

	
	private static String[] processScenarioStateRestore(String[] args) {
		
		
		ArrayList<String> inputArgs = new ArrayList<String>(Arrays.asList(args));
		
		
		ListIterator<String> inputIt =  inputArgs.listIterator();
		
		
		Iterator<String> firstPass = inputArgs.iterator();
		
		
		boolean foundIteration = false;
		while(firstPass.hasNext())
		{
			String arg = firstPass.next();
			if(arg.trim().equals("--restoreIteration") || arg.trim().equals("--restoreStateIteration"))
			{
				if(firstPass.hasNext())
				{
					foundIteration= true;
				}
			}
		}
		while(inputIt.hasNext())
		{
			String input = inputIt.next();
			
			if(input.trim().equals("--restoreScenario"))
			{
				if(!inputIt.hasNext())
				{
					throw new ParameterException("Failed to parse argument --restoreScenario expected 1 more argument");
				} else
				{
					String dir = inputIt.next();
					
					
					inputIt.add("--restoreStateFrom");
					inputIt.add(dir);
					if(!foundIteration)
					{
						inputIt.add("--restoreIteration");
						inputIt.add(String.valueOf(Integer.MAX_VALUE));
					}
					inputIt.add("--scenarioFile");
					inputIt.add(dir + File.separator + "scenario.txt");
					inputIt.add("--instanceFeatureFile");
					inputIt.add(dir + File.separator + "instance-features.txt");
					inputIt.add("--instanceFile");
					inputIt.add(dir + File.separator + "instances.txt");
					inputIt.add("--paramFile");
					inputIt.add(dir + File.separator + "param-file.txt");
					inputIt.add("--testInstanceFile");
					inputIt.add(dir + File.separator + "instances.txt");
					
				}
				
				
			}
			
		}
		
		return inputArgs.toArray(new String[0]);
	}


	private static void checkArgsForUsageScreenValues(String[] args, SMACOptions config) {
		/*
		@Parameter(names="--showHiddenParameters", description="show hidden parameters that no one has use for, and probably just break SMAC")
		public boolean showHiddenParameters = false;
		
		@Parameter(names={"--help","-?","/?","-h"}, description="show help")
		public boolean showHelp = false;
		
		@Parameter(names={"-v","--version"}, description="print version and exit")
		public boolean showVersion = false;
		*/
		
		try {
			Set<String> possibleValues = new HashSet<String>(Arrays.asList(args));
			
			String[] helpNames =  config.getClass().getField("showHelp").getAnnotation(Parameter.class).names();
			for(String helpName : helpNames)
			{
				if(possibleValues.contains(helpName))
				{
					ConfigToLaTeX.usage(ConfigToLaTeX.getParameters(config, TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators()));
					System.exit(ACLibReturnValues.SUCCESS);
				}
			}
			
			
			String[] hiddenNames =  config.getClass().getField("showHiddenParameters").getAnnotation(Parameter.class).names();
			for(String helpName : hiddenNames)
			{
				if(possibleValues.contains(helpName))
				{
					ConfigToLaTeX.usage(ConfigToLaTeX.getParameters(config, TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators()), true);
					System.exit(ACLibReturnValues.SUCCESS);
				}
			}
			
			
			String[] versionNames = config.getClass().getField("showVersion").getAnnotation(Parameter.class).names();
			for(String helpName : versionNames)
			{
				if(possibleValues.contains(helpName))
				{
					//Turn off logging
					System.setProperty("logback.configurationFile", "logback-off.xml");
					VersionTracker.setClassLoader(TargetAlgorithmEvaluatorLoader.getClassLoader());
					System.out.println(VersionTracker.getVersionInformation());
					
					
					System.exit(ACLibReturnValues.SUCCESS);
				}
			}
			
			
			
			
		} catch (Exception e) {
			
			throw new IllegalStateException(e);
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
