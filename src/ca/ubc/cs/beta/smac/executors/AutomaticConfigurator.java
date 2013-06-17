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
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration;
import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration.StringFormat;
import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.aclib.configspace.ParamFileHelper;
import ca.ubc.cs.beta.aclib.eventsystem.EventManager;
import ca.ubc.cs.beta.aclib.exceptions.FeatureNotFoundException;
import ca.ubc.cs.beta.aclib.exceptions.StateSerializationException;
import ca.ubc.cs.beta.aclib.exceptions.TrajectoryDivergenceException;
import ca.ubc.cs.beta.aclib.execconfig.AlgorithmExecutionConfig;

import ca.ubc.cs.beta.aclib.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.aclib.misc.returnvalues.ACLibReturnValues;
import ca.ubc.cs.beta.aclib.misc.spi.SPIClassLoaderHelper;
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
import ca.ubc.cs.beta.aclib.random.SeedableRandomPool;
import ca.ubc.cs.beta.aclib.random.SeedableRandomPoolConstants;
import ca.ubc.cs.beta.aclib.runhistory.NewRunHistory;
import ca.ubc.cs.beta.aclib.runhistory.RunHistory;
import ca.ubc.cs.beta.aclib.runhistory.ThreadSafeRunHistory;
import ca.ubc.cs.beta.aclib.runhistory.ThreadSafeRunHistoryWrapper;
import ca.ubc.cs.beta.aclib.seedgenerator.InstanceSeedGenerator;
import ca.ubc.cs.beta.aclib.state.StateDeserializer;
import ca.ubc.cs.beta.aclib.state.StateFactory;
import ca.ubc.cs.beta.aclib.state.legacy.LegacyStateFactory;
import ca.ubc.cs.beta.aclib.state.nullFactory.NullStateFactory;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.init.TargetAlgorithmEvaluatorBuilder;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.init.TargetAlgorithmEvaluatorLoader;
import ca.ubc.cs.beta.aclib.trajectoryfile.TrajectoryFileEntry;
import ca.ubc.cs.beta.smac.AbstractAlgorithmFramework;
import ca.ubc.cs.beta.smac.SequentialModelBasedAlgorithmConfiguration;
import ca.ubc.cs.beta.smac.validation.Validator;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class AutomaticConfigurator 
{

	
	private static Logger log;
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
	private static SeedableRandomPool pool;
	/**
	 * Executes SMAC then exits the JVM {@see System.exit()}
	 *  
	 * @param args string arguments
	 */
	public static void main(String[] args)
	{
		int returnValue = oldMain(args);
		
		if(log != null)
		{
			log.info("Returning with value: {}",returnValue);
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
			
			
			log.info("Automatic Configurator Started");
			
		
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
					restoreSF = new LegacyStateFactory(options.scenarioConfig.outputDirectory + File.separator + runGroupName + File.separator + "state-run" + options.seedOptions.numRun + File.separator, options.restoreStateFrom);
					break;
				default:
					throw new IllegalArgumentException("State Serializer specified is not supported");
			}
			
			String paramFile = options.scenarioConfig.algoExecOptions.paramFileDelegate.paramFile;
			log.info("Parsing Parameter Space File", paramFile);
			ParamConfigurationSpace configSpace = null;
			
			
			String[] possiblePaths = { paramFile, options.experimentDir + File.separator + paramFile, options.scenarioConfig.algoExecOptions.algoExecDir + File.separator + paramFile };
			String lastParamFilePath = null;
			
			
			for(String path : possiblePaths)
			{
				try {
					log.debug("Trying param file in path {} ", path);
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
						log.warn("Error occured while trying to parse is {}"  , e.getMessage() );
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
					String savePath = options.scenarioConfig.outputDirectory + File.separator + runGroupName + File.separator + "state-run" + options.seedOptions.numRun + File.separator;
					
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
					log.debug("Instance Specific Information is compatible with Verifying SAT, enabling option");
					options.scenarioConfig.algoExecOptions.taeOpts.verifySAT = true;
				} else
				{
					log.debug("Instance Specific Information is NOT compatible with Verifying SAT, disabling option");
					options.scenarioConfig.algoExecOptions.taeOpts.verifySAT = false;
				}
				
			
			} else if(options.scenarioConfig.algoExecOptions.taeOpts.verifySAT == true)
			{
				boolean verifySATCompatible = ProblemInstanceHelper.isVerifySATCompatible(instances);
				if(!verifySATCompatible)
				{
					log.warn("Verify SAT set to true, but some instances have instance specific information that isn't in {SAT, SATISFIABLE, UNKNOWN, UNSAT, UNSATISFIABLE}");
				}
					
			}
			
			
			
			ParamConfiguration initialIncumbent = configSpace.getConfigurationFromString(options.initialIncumbent, StringFormat.NODB_SYNTAX);
		
			
			if(!initialIncumbent.equals(configSpace.getDefaultConfiguration()))
			{
				log.info("Initial Incumbent set to \"{}\" ", initialIncumbent.getFormattedParamString(StringFormat.NODB_SYNTAX));
			} else
			{
				log.info("Initial Incumbent is the default \"{}\" ", initialIncumbent.getFormattedParamString(StringFormat.NODB_SYNTAX));
			}
			
			
			TargetAlgorithmEvaluator tae = TargetAlgorithmEvaluatorBuilder.getTargetAlgorithmEvaluator(options.scenarioConfig.algoExecOptions.taeOpts, execConfig, true, true, taeOptions, null, new File(options.scenarioConfig.outputDirectory + File.separator + runGroupName + File.separator), options.seedOptions.numRun);
			

			if(options.modelHashCodeFile != null)
			{
				log.info("Algorithm Execution will verify model Hash Codes");
				parseModelHashCodes(options.modelHashCodeFile);
			}
			
			EventManager eventManager = new EventManager();
			
			AbstractAlgorithmFramework smac;
	
			ThreadSafeRunHistory rh = new ThreadSafeRunHistoryWrapper(new NewRunHistory(options.scenarioConfig.intraInstanceObj, options.scenarioConfig.interInstanceObj, options.scenarioConfig.runObj));
			
			
			switch(options.execMode)
			{
				case ROAR:

					smac = new AbstractAlgorithmFramework(options,instances,tae,sf, configSpace, instanceSeedGen, initialIncumbent, eventManager, rh, pool, runGroupName);

					break;
				case SMAC:

					smac = new SequentialModelBasedAlgorithmConfiguration(options, instances, tae, options.expFunc.getFunction(),sf, configSpace, instanceSeedGen, initialIncumbent, eventManager, rh,pool, runGroupName);

					
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
				restoreState(options, restoreSF, smac, configSpace, instances, execConfig, rh);
			}
			
			try {
				smac.run();
			} finally
			{
				tae.notifyShutdown();
			}
			
			pool.logUsage();
			
			List<TrajectoryFileEntry> tfes = smac.getTrajectoryFileEntries();
			SortedMap<TrajectoryFileEntry, Double> performance;
			if(options.doValidation)
			{
			
				//Don't use the same TargetAlgorithmEvaluator as above as it may have runhashcode and other crap that is probably not applicable for validation
				
				if(options.validationOptions.maxTimestamp == -1)
				{
					options.validationOptions.maxTimestamp = options.scenarioConfig.tunerTimeout;
				}
				
				options.scenarioConfig.algoExecOptions.taeOpts.trackRunsScheduled = false;
				
				TargetAlgorithmEvaluator validatingTae =TargetAlgorithmEvaluatorBuilder.getTargetAlgorithmEvaluator(options.scenarioConfig.algoExecOptions.taeOpts, execConfig, false, taeOptions);
				try {
					String outputDir = options.scenarioConfig.outputDirectory + File.separator + runGroupName + File.separator;
					performance  = (new Validator()).validate(testInstances,options.validationOptions,options.scenarioConfig.algoExecOptions.cutoffTime, testInstanceSeedGen, validatingTae, outputDir, options.scenarioConfig.runObj, options.scenarioConfig.intraInstanceObj, options.scenarioConfig.interInstanceObj, tfes, options.seedOptions.numRun,true);
				} finally
				{
					validatingTae.notifyShutdown();
				}
				
			} else
			{
				performance = new TreeMap<TrajectoryFileEntry, Double>();
				performance.put(tfes.get(tfes.size()-1), Double.POSITIVE_INFINITY);
				
			}
			
			
			
			
			smac.logIncumbentPerformance(performance);
			smac.afterValidationStatistics();
			smac.logSMACResult(performance);
			
			
			
			log.info("SMAC Completed Successfully. Log: " + logLocation);
			
			
			return ACLibReturnValues.SUCCESS;
		} catch(Throwable t)
		{
			System.out.flush();
			System.err.flush();
			
			System.err.println("Error occured running SMAC ( " + t.getClass().getSimpleName() + " : "+ t.getMessage() +  ")\nError Log: " + logLocation);
			System.err.flush();
			
				if(log != null)
				{
					
					log.error(exception, "Message: {}",t.getMessage());
					
					
					if(!(t instanceof ParameterException))
					{
						log.info("Maybe try running in DEBUG mode if you are missing information");
						
						log.error(exception, "Exception:{}", t.getClass().getCanonicalName());
						StringWriter sWriter = new StringWriter();
						PrintWriter writer = new PrintWriter(sWriter);
						t.printStackTrace(writer);
						log.error(stackTrace, "StackTrace:{}",sWriter.toString());
						
						
						
					} else
					{
						log.info("Don't forget that some options are set by default from files in ~/.aclib/");
						log.debug("Exception stack trace", t);
					}
						
					log.info("Exiting SMAC with failure. Log: " + logLocation);
				
					log.info("Please see above for the available options. Further information is available in the following documents:");
					log.info("- The FAQ (doc/faq.pdf) contains commonly asked questions regarding troubleshooting, and usage.");
					log.info("- The Quickstart Guide (doc/quickstart.pdf) gives a simple example for getting up and running.");
					log.info("- The Manual (doc/manual.pdf) contains detailed information on file format semantics.");

					
					
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
	

	

	
	private static void restoreState(SMACOptions options, StateFactory sf, AbstractAlgorithmFramework smac,  ParamConfigurationSpace configSpace, List<ProblemInstance> instances, AlgorithmExecutionConfig execConfig, RunHistory rh) {
		
		if(options.restoreIteration < 0)
		{
			throw new ParameterException("Iteration must be a non-negative integer");
		}
		
		StateDeserializer sd = sf.getStateDeserializer("it", options.restoreIteration, configSpace, instances, execConfig, rh);
		
		smac.restoreState(sd);
	}

	private static String runGroupName = "DEFAULT";
	
	/**
	 * Parsers Command Line Arguments and returns a options object
	 * @param args
	 * @return
	 */
	private static SMACOptions parseCLIOptions(String[] args) throws ParameterException, IOException
	{
		//DO NOT LOG UNTIL AFTER WE PARSE CONFIG OBJECT
		taeOptions = TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators();
		SMACOptions options = new SMACOptions();
		JCommander jcom = JCommanderHelper.getJCommander(options, taeOptions);
		
		jcom.setProgramName("smac");
		
		try {
			
			
			//JCommanderHelper.parse(com, args);
			try {
				try {
				checkArgsForUsageScreenValues(args,options);
				
				args = processScenarioStateRestore(args);
				jcom.parse(args);
				} finally
				{
					runGroupName = options.runGroupOptions.getFailbackRunGroup();
				}
				
				

				if(options.adaptiveCapping == null)
				{
					switch(options.scenarioConfig.runObj)
					{
					case RUNTIME:
						options.adaptiveCapping = true;
						break;
						
					case QUALITY:
						options.adaptiveCapping = false;
						break;
						
					default:
						//You need to add something new here
						throw new IllegalStateException("Not sure what to default too");
					}
				}
				
				
				
				
				
				
				runGroupName = options.getRunGroupName(taeOptions.values());
				File outputDir = new File(options.scenarioConfig.outputDirectory);
				if(!outputDir.exists())
				{
					outputDir.mkdir();
				}
				
				
				
			} finally
			{
				
				System.setProperty("OUTPUTDIR", options.scenarioConfig.outputDirectory);
				System.setProperty("RUNGROUPDIR", runGroupName);
				System.setProperty("NUMRUN", String.valueOf(options.seedOptions.numRun));
				System.setProperty("STDOUT-LEVEL", options.consoleLogLevel.name());
				System.setProperty("ROOT-LEVEL",options.logLevel.name());
				
				logLocation = options.scenarioConfig.outputDirectory + File.separator + runGroupName + File.separator + "log-run" + options.seedOptions.numRun+ ".txt";
				
				System.out.println("*****************************\nLogging to: " + logLocation +  "\n*****************************");
				//Generally has the format: ${OUTPUTDIR}/${RUNGROUPDIR}/log-run${NUMRUN}.txt
				log = LoggerFactory.getLogger(AutomaticConfigurator.class);
				exception = MarkerFactory.getMarker("EXCEPTION");
				stackTrace = MarkerFactory.getMarker("STACKTRACE");
				
				VersionTracker.setClassLoader(SPIClassLoaderHelper.getClassLoader());
				VersionTracker.logVersions();
				
				for(String name : jcom.getParameterFilesToRead())
				{
					log.info("Parsing (default) options from file: {} ", name);
				}
				
			}
			
			log.trace("Command Line Options Parsed");
			
			
			
			
			validateObjectiveCombinations(options.scenarioConfig, options.adaptiveCapping);
			
			logCallString(args);
			
			/*
			
			*/
			
			if(options.logLevel.lessVerbose(options.consoleLogLevel))
			{
				log.warn("The console has been set to be more verbose than the log. This is generally an error, except if you have modified the conf.xml to have certain loggers be more specific");
				//throw new ParameterException("The console can NOT be more verbose than the logs (This will have no effect)");
				
			}
				
			
			
			 
			 if(log.isDebugEnabled())
			 {
				Map<String, String> env = new TreeMap<String, String>(System.getenv());
					
				StringBuilder sb = new StringBuilder();
				 for (String envName : env.keySet()) {
					 sb.append(envName).append("=").append(env.get(envName)).append("\n");
					 
			           
			        }
				
				
					 
				 log.debug("==========Enviroment Variables===========\n{}", sb.toString());
				 
				 
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
				
				log.debug("Hostname:{}", hostname);
				log.debug("==========System Properties==============\n{}", sb.toString() );
			 }
			
		
			StringBuilder sb = new StringBuilder();
			for(Object o : jcom.getObjects())
			{
				sb.append(o.toString()).append("\n");
			}
				
			log.info("==========Configuration Options==========\n{}", sb.toString());
			pool = options.seedOptions.getSeedableRandomPool();
			
			log.info("Parsing instances from {}", options.scenarioConfig.instanceFile );
			InstanceListWithSeeds ilws;
			
			
			ilws = ProblemInstanceHelper.getInstances(options.scenarioConfig.instanceFile,options.experimentDir, options.scenarioConfig.instanceFeatureFile, options.scenarioConfig.checkInstanceFilesExist, pool.getRandom(SeedableRandomPoolConstants.INSTANCE_SEEDS).nextInt(), (options.scenarioConfig.algoExecOptions.deterministic));
			
			instanceFileAbsolutePath = ilws.getInstanceFileAbsolutePath();
			instanceFeatureFileAbsolutePath = ilws.getInstanceFeatureFileAbsolutePath();
			
			instanceSeedGen = ilws.getSeedGen();
			
			log.info("Instance Seed Generator reports {} seeds ", instanceSeedGen.getInitialInstanceSeedCount());
			if(instanceSeedGen.allInstancesHaveSameNumberOfSeeds())
			{
				log.info("Instance Seed Generator reports that all instances have the same number of available seeds");
			} else
			{
				log.error("Instance Seed Generator reports that some instances have a different number of seeds than others");
				throw new ParameterException("All Training Instances must have the same number of seeds in this version of SMAC");
			}
			
			instances = ilws.getInstances();
			
			
			log.info("Parsing test instances from {}", options.scenarioConfig.testInstanceFile );
			int testSeeds = pool.getRandom(SeedableRandomPoolConstants.TEST_SEED_INSTANCES).nextInt();
			try {
				ilws = ProblemInstanceHelper.getInstances(options.scenarioConfig.testInstanceFile, options.experimentDir, options.scenarioConfig.instanceFeatureFile, options.scenarioConfig.checkInstanceFilesExist, testSeeds,(options.scenarioConfig.algoExecOptions.deterministic ) );
				
			} catch(FeatureNotFoundException e)
			{
				ilws = ProblemInstanceHelper.getInstances(options.scenarioConfig.testInstanceFile, options.experimentDir, null, options.scenarioConfig.checkInstanceFilesExist, testSeeds,(options.scenarioConfig.algoExecOptions.deterministic ) );
			}
			
			testInstances = ilws.getInstances();
			testInstanceSeedGen = ilws.getSeedGen();
			
			log.info("Test Instance Seed Generator reports {} seeds ", testInstanceSeedGen.getInitialInstanceSeedCount());
			if(testInstanceSeedGen.allInstancesHaveSameNumberOfSeeds())
			{
				log.info("Test Seed Generator reports that all instances have the same number of available seeds");
			} else
			{
				log.info("Test Seed Generator reports that the number of seeds per instance varies.");
			}
			
			
			
			
			
			
			
				
			
			
			try {
				//We don't handle this more gracefully because this seems like a super rare incident.
				if(ManagementFactory.getThreadMXBean().isThreadCpuTimeEnabled())
				{
					log.debug("JVM Supports CPU Timing Measurements");
				} else
				{
					log.warn("This Java Virtual Machine has CPU Time Measurements disabled, tunerTimeout will not contain any SMAC Execution Time.");
				}
			} catch(UnsupportedOperationException e)
			{
				log.warn("This Java Virtual Machine does not support CPU Time Measurements, tunerTimeout will not contain any SMAC Execution Time Information (http://docs.oracle.com/javase/1.5.0/docs/api/java/lang/management/ThreadMXBean.html#setThreadCpuTimeEnabled(boolean))");
			}
			
			if(options.seedOptions.numRun + options.seedOptions.seedOffset < 0)
			{
				log.warn("NumRun {} plus Seed Offset {} should be positive, things may not seed correctly",options.seedOptions.numRun, options.seedOptions.seedOffset );
			}
			return options;
		} catch(IOException e)
		{
			//com.setColumnSize(getConsoleSize());
			try {
				ConfigToLaTeX.usage(ConfigToLaTeX.getParameters(jcom.getObjects().get(0), TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators()));
			} catch (Exception e1) {
				log.error("Exception occured while trying to generate usage screen",e1);
				log.error("This exception did NOT cause SMAC to crash");
			}
			throw e;
			
		} catch(ParameterException e)
		{
			try {
				
				ConfigToLaTeX.usage(ConfigToLaTeX.getParameters(jcom.getObjects().get(0),TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators()));
			} catch (Exception e1) {
				log.error("Exception occured while trying to generate usage screen",e1);
				log.error("This exception did NOT cause SMAC to crash");
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
					VersionTracker.setClassLoader(SPIClassLoaderHelper.getClassLoader());
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
		
		log.info("Call String:");
		log.info("{}", sb.toString());
	}
	
	private static Pattern modelHashCodePattern = Pattern.compile("^(Preprocessed|Random) Forest Built with Hash Code:\\s*\\d+?\\z");
	
	
	private static void parseModelHashCodes(File modelHashCodeFile) {
		log.info("Model Hash Code File Passed {}", modelHashCodeFile.getAbsolutePath());
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
						log.debug("Found Model Hash Code #{} on line #{} with contents:{}", array);
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
						log.trace("No Hash Code found on line: {}", line );
					}
					lineCount++;
				}
				if(hashCodeCount == 0)
				{
					log.warn("Hash Code File Specified, but we found no hash codes");
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
