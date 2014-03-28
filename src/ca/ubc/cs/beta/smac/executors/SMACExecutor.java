package ca.ubc.cs.beta.smac.executors;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import ca.ubc.cs.beta.aclib.exceptions.StateSerializationException;
import ca.ubc.cs.beta.aclib.exceptions.TrajectoryDivergenceException;
import ca.ubc.cs.beta.aclib.execconfig.AlgorithmExecutionConfig;
import ca.ubc.cs.beta.aclib.logging.CommonMarkers;
import ca.ubc.cs.beta.aclib.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.aclib.misc.returnvalues.ACLibReturnValues;
import ca.ubc.cs.beta.aclib.misc.spi.SPIClassLoaderHelper;
import ca.ubc.cs.beta.aclib.misc.version.JavaVersionInfo;
import ca.ubc.cs.beta.aclib.misc.version.OSVersionInfo;
import ca.ubc.cs.beta.aclib.misc.version.VersionTracker;
import ca.ubc.cs.beta.aclib.misc.watch.AutoStartStopWatch;
import ca.ubc.cs.beta.aclib.misc.watch.StopWatch;
import ca.ubc.cs.beta.aclib.options.AbstractOptions;
import ca.ubc.cs.beta.aclib.probleminstance.InstanceListWithSeeds;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstanceOptions.TrainTestInstances;
import ca.ubc.cs.beta.aclib.random.SeedableRandomPool;
import ca.ubc.cs.beta.aclib.seedgenerator.InstanceSeedGenerator;
import ca.ubc.cs.beta.aclib.smac.SMACOptions;
import ca.ubc.cs.beta.aclib.state.StateFactoryOptions;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.base.cli.CommandLineAlgorithmRun;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.base.cli.CommandLineTargetAlgorithmEvaluatorFactory;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.base.cli.CommandLineTargetAlgorithmEvaluatorOptions;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.exceptions.TargetAlgorithmAbortException;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.init.TargetAlgorithmEvaluatorBuilder;
import ca.ubc.cs.beta.aclib.trajectoryfile.TrajectoryFileEntry;
import ca.ubc.cs.beta.smac.builder.SMACBuilder;
import ca.ubc.cs.beta.smac.configurator.AbstractAlgorithmFramework;
import ca.ubc.cs.beta.smac.misc.version.SMACVersionInfo;
import ca.ubc.cs.beta.smac.validation.Validator;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class SMACExecutor {

	
	private static Logger log;
	private static Marker exception;
	private static Marker stackTrace;
	
	private static String logLocation = "<NO LOG LOCATION SPECIFIED, FAILURE MUST HAVE OCCURED EARLY>";
	
	/*
	private static List<ProblemInstance> instances;
	private static List<ProblemInstance> testInstances;
	
	private static InstanceSeedGenerator instanceSeedGen;
	private static InstanceSeedGenerator testInstanceSeedGen;
	
	
	
	private static String instanceFileAbsolutePath;
	private static String instanceFeatureFileAbsolutePath;
	*/
	
	
	private static InstanceListWithSeeds trainingILWS;
	private static InstanceListWithSeeds testingILWS;
	
	
	private static Map<String,  AbstractOptions> taeOptions;
	private static SeedableRandomPool pool;
	
	private static String outputDir;
	
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
			log.debug("Returning with value: {}",returnValue);
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
			
			SMACBuilder smacBuilder = new SMACBuilder();
			
			//EventManager eventManager = smacBuilder.getEventManager();
			AlgorithmExecutionConfig execConfig = options.getAlgorithmExecutionConfig();
			
			AbstractAlgorithmFramework smac;
			smac = smacBuilder.getAutomaticConfigurator(execConfig,  trainingILWS, options, taeOptions, outputDir, pool);
			
			StopWatch watch = new AutoStartStopWatch();
			
			smac.run();
			
			watch.stop();
			smacBuilder.getLogRuntimeStatistics().logLastRuntimeStatistics();
			
			
			pool.logUsage();
			
			log.info("SMAC has finished. Reason: {}",smac.getTerminationReason() );
			List<TrajectoryFileEntry> tfes = smacBuilder.getTrajectoryFileLogger().getTrajectoryFileEntries();
			
			
			SortedMap<TrajectoryFileEntry, Double> performance;
			options.doValidation = (options.validationOptions.numberOfValidationRuns > 0) ? options.doValidation : false;
			if(options.doValidation)
			{
				
			
				//Don't use the same TargetAlgorithmEvaluator as above as it may have runhashcode and other crap that is probably not applicable for validation
				
				if(options.validationOptions.maxTimestamp == -1)
				{
					if(options.validationOptions.useWallClockTime)
					{
						if(options.scenarioConfig.limitOptions.runtimeLimit < Integer.MAX_VALUE)
						{
							options.validationOptions.maxTimestamp = options.scenarioConfig.limitOptions.runtimeLimit;
						} else
						{
							options.validationOptions.maxTimestamp = watch.time() / 1000.0;
						}
					} else
					{
						options.validationOptions.maxTimestamp = options.scenarioConfig.limitOptions.tunerTimeout;
					}
					
					
				}
				
				
				options.scenarioConfig.algoExecOptions.taeOpts.turnOffCrashes();
				
				int coreHint = 1;
				if(options.validationCores != null && options.validationCores > 0)
				{
					log.debug("Validation will use {} cores", options.validationCores);
					options.scenarioConfig.algoExecOptions.taeOpts.maxConcurrentAlgoExecs = options.validationCores;
					((CommandLineTargetAlgorithmEvaluatorOptions) taeOptions.get(CommandLineTargetAlgorithmEvaluatorFactory.NAME)).cores = options.validationCores;
					coreHint = options.validationCores;
				}
				
				TargetAlgorithmEvaluator validatingTae =TargetAlgorithmEvaluatorBuilder.getTargetAlgorithmEvaluator(options.scenarioConfig.algoExecOptions.taeOpts, execConfig, false, taeOptions);
				try {
					
					List<ProblemInstance> testInstances = testingILWS.getInstances();
					InstanceSeedGenerator testInstanceSeedGen = testingILWS.getSeedGen();
					
					performance  = (new Validator()).validate(testInstances,options.validationOptions,options.scenarioConfig.algoExecOptions.cutoffTime, testInstanceSeedGen, validatingTae, outputDir, options.scenarioConfig.runObj, options.scenarioConfig.getIntraInstanceObjective(), options.scenarioConfig.interInstanceObj, tfes, options.seedOptions.numRun,true, coreHint);
				} finally
				{
					validatingTae.notifyShutdown();
				}
				
			} else
			{
				performance = new TreeMap<TrajectoryFileEntry, Double>();
				performance.put(tfes.get(tfes.size()-1), Double.POSITIVE_INFINITY);
				
			}
			
			
			
			
			String incumbentPerformance = smac.logIncumbentPerformance(performance);
			

			String callString = smac.logSMACResult(performance);
			
			
			smacBuilder.getEventManager().shutdown();
			
			log.info("SMAC Result:\n=======================================================================================\nMinimized {}{}:\n{}\n{}\nAdditional information about run {} in: {}\n=======================================================================================",smac.getObjectiveToReport(), (performance.size() > 1) ? " over time": "" ,incumbentPerformance, callString, options.seedOptions.numRun, outputDir);
			//log.info("SMAC has finished. Reason: {}",smac.getTerminationReason() );
			//log.info("SMAC"+ (options.doValidation ? " & Validation" : "" ) +  " Completed Successfully. Log: " + logLocation);
			
			
			
			return ACLibReturnValues.SUCCESS;
		} catch(Throwable t)
		{
			System.out.flush();
			System.err.flush();
			
			System.err.println("Error occurred while running SMAC\n>Error Message:"+  t.getMessage() +  "\n>Encountered Exception:" + t.getClass().getSimpleName() +"\n>Error Log Location: " + logLocation);
			System.err.flush();
			
				if(log != null)
				{
					
					log.error(exception, "Message: {}",t.getMessage());
					
					
					if(t instanceof ParameterException)
					{
						log.info("Note that some options are read from files in the ~/.aeatk/ directory");
						log.debug("Exception stack trace", t);
						
						
					} else if(t instanceof TargetAlgorithmAbortException)
					{
						
						log.error(CommonMarkers.SKIP_CONSOLE_PRINTING, "A serious problem occured during target algorithm execution and we are aborting execution ",t );
						
						
						
						log.error("We tried to call the target algorithm wrapper, but this call failed.");
						log.error("The problem is (most likely) somewhere in the wrapper or with the arguments to SMAC.");
						log.error("The easiest way to debug this problem is to manually execute the call we tried and see why it did not return the correct result");
						log.error("The required output of the wrapper is something like \"Result for ParamILS: x,x,x,x,x\".);");
						//log.error("Specifically the regex we are matching is {}", CommandLineAlgorithmRun.AUTOMATIC_CONFIGURATOR_RESULT_REGEX);
					}	else
					{
						log.info("Maybe try running in DEBUG mode if you are missing information");
						
						log.error(exception, "Exception:{}", t.getClass().getCanonicalName());
						StringWriter sWriter = new StringWriter();
						PrintWriter writer = new PrintWriter(sWriter);
						t.printStackTrace(writer);
						log.error(stackTrace, "StackTrace:{}",sWriter.toString());
					}
						
					log.info("Exiting SMAC with failure. Log: " + logLocation);
					log.info("For a list of available commands use:  --help");
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
	
	
	private static String runGroupName = "DEFAULT";
	
	/**
	 * Parsers Command Line Arguments and returns a options object
	 * @param args
	 * @return
	 */
	private static SMACOptions parseCLIOptions(String[] args) throws ParameterException, IOException
	{
		//DO NOT LOG UNTIL AFTER WE PARSE CONFIG OBJECT
		
		SMACOptions options = new SMACOptions();
		taeOptions = options.scenarioConfig.algoExecOptions.taeOpts.getAvailableTargetAlgorithmEvaluators();
		JCommander jcom = JCommanderHelper.getJCommanderAndCheckForHelp(args, options, taeOptions);
		
		jcom.setProgramName("smac");
		
		try {
			try {
				try {
				
				
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
				
				if(options.randomForestOptions.logModel == null)
				{
					switch(options.scenarioConfig.runObj)
					{
					case RUNTIME:
						options.randomForestOptions.logModel = true;
						break;
					case QUALITY:
						options.randomForestOptions.logModel = false;
					}
				}

				runGroupName = options.getRunGroupName(taeOptions.values());
				//File outputDir = new File(options.scenarioConfig.outputDirectory);
				
				
				/*
				 * Build the Serializer object used in the model 
				 */
				outputDir = options.getOutputDirectory(runGroupName);
			
				File outputDirFile = new File(outputDir);
				
				if(!outputDirFile.exists())
				{
					outputDirFile.mkdirs();
					//Check again to ensure there isn't a race condition
					if(!outputDirFile.exists())
					{
						throw new ParameterException("Could not create all folders necessary for output directory: " + outputDir);
					}
					
				}
				
				
			} finally
			{
				
				options.logOptions.initializeLogging(outputDir, options.seedOptions.numRun);
				SMACExecutor.logLocation = options.logOptions.getLogLocation(outputDir,options.seedOptions.numRun);
				
				log = LoggerFactory.getLogger(SMACExecutor.class);
				
				exception = MarkerFactory.getMarker("EXCEPTION");
				stackTrace = MarkerFactory.getMarker("STACKTRACE");
				
				VersionTracker.setClassLoader(SPIClassLoaderHelper.getClassLoader());
				
				VersionTracker.logVersions();
				SMACVersionInfo s = new SMACVersionInfo();
				JavaVersionInfo j = new JavaVersionInfo();
				OSVersionInfo o = new OSVersionInfo();
				log.info(CommonMarkers.SKIP_FILE_PRINTING,"Version of {} is {}, running on {} and {} ", s.getProductName(), s.getVersion(), j.getVersion(), o.getVersion());
				
				
				for(String name : jcom.getParameterFilesToRead())
				{
					log.debug("Parsing (default) options from file: {} ", name);
				}
				
			}
			
			
			JCommanderHelper.logCallString(args, "smac");
			

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
			
			JCommanderHelper.logConfiguration(jcom);
			pool = options.seedOptions.getSeedableRandomPool();
			

			TrainTestInstances tti = options.getTrainingAndTestProblemInstances(pool, new SeedableRandomPool(options.validationSeed + options.seedOptions.seedOffset,pool.getInitialSeeds()));
			trainingILWS = tti.getTrainingInstances();
			testingILWS = tti.getTestInstances();
		
			try {
				//We don't handle this more gracefully because this seems like a super rare incident.
				if(ManagementFactory.getThreadMXBean().isThreadCpuTimeEnabled())
				{
					log.trace("JVM Supports CPU Timing Measurements");
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
			throw e;
			
		} catch(ParameterException e)
		{
		
			
			throw e;
		}
	}
	
	private static String[] processScenarioStateRestore(String[] args) {
		return StateFactoryOptions.processScenarioStateRestore(args);
	
	}
		


}