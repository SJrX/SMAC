package ca.ubc.cs.beta.smac.executors;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import ca.ubc.cs.beta.aeatk.algorithmexecutionconfiguration.AlgorithmExecutionConfiguration;
import ca.ubc.cs.beta.aeatk.configspace.ParamConfiguration;
import ca.ubc.cs.beta.aeatk.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.aeatk.configspace.ParamConfiguration.StringFormat;
import ca.ubc.cs.beta.aeatk.logging.CommonMarkers;
import ca.ubc.cs.beta.aeatk.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.aeatk.misc.returnvalues.ACLibReturnValues;
import ca.ubc.cs.beta.aeatk.misc.spi.SPIClassLoaderHelper;
import ca.ubc.cs.beta.aeatk.misc.version.JavaVersionInfo;
import ca.ubc.cs.beta.aeatk.misc.version.OSVersionInfo;
import ca.ubc.cs.beta.aeatk.misc.version.VersionTracker;
import ca.ubc.cs.beta.aeatk.options.AbstractOptions;
import ca.ubc.cs.beta.aeatk.probleminstance.InstanceListWithSeeds;
import ca.ubc.cs.beta.aeatk.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aeatk.random.SeedableRandomPool;
import ca.ubc.cs.beta.aeatk.random.SeedableRandomPoolConstants;
import ca.ubc.cs.beta.aeatk.seedgenerator.InstanceSeedGenerator;
import ca.ubc.cs.beta.aeatk.smac.ValidationExecutorOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.base.cli.CommandLineTargetAlgorithmEvaluatorFactory;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.base.cli.CommandLineTargetAlgorithmEvaluatorOptions;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.init.TargetAlgorithmEvaluatorBuilder;
import ca.ubc.cs.beta.aeatk.targetalgorithmevaluator.init.TargetAlgorithmEvaluatorLoader;
import ca.ubc.cs.beta.aeatk.trajectoryfile.TrajectoryFile;
import ca.ubc.cs.beta.aeatk.trajectoryfile.TrajectoryFileEntry;
import ca.ubc.cs.beta.smac.misc.version.SMACVersionInfo;
import ca.ubc.cs.beta.smac.validation.Validator;

public class ValidatorExecutor {

	private static Logger log;
	private static Marker exception;
	private static Marker stackTrace;
	
	public static void main(String[] args)
	{
		
		ValidationExecutorOptions options = new ValidationExecutorOptions();
		Map<String, AbstractOptions> taeOptions = TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators();
		
		
		try {
			JCommander jcom = JCommanderHelper.parseCheckingForHelpAndVersion(args,options, taeOptions);
			
			//String outputDir = System.getProperty("user.dir") + File.separator +"ValidationRun-" + (new SimpleDateFormat("yyyy-MM-dd--HH-mm-ss-SSS")).format(new Date()) +File.separator;
			
			if(options.useScenarioOutDir)
			{
				throw new ParameterException("--use-scenario-outdir is now deprecated. Output of files will be in the same directory of the trajectory files or the current working directory if there isn't one ");
				
				//outputDir = options.scenarioConfig.outputDirectory + File.separator;
			}
			
			options.logOptions.initializeLogging(new File(".").getCanonicalFile().getAbsolutePath(), options.seedOptions.numRun);
			
			log = LoggerFactory.getLogger(ValidatorExecutor.class);
			
			
			/*
			 * 	options.logOptions.initializeLogging(outputDir, options.seedOptions.numRun);
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
			
			
			
			
			 */
			log.debug("==========Configuration Options==========\n{}", options.toString());
			VersionTracker.setClassLoader(SPIClassLoaderHelper.getClassLoader());
			VersionTracker.logVersions();
			
			SMACVersionInfo s = new SMACVersionInfo();
			JavaVersionInfo j = new JavaVersionInfo();
			OSVersionInfo o = new OSVersionInfo();
			log.info(CommonMarkers.SKIP_FILE_PRINTING,"Version of {} is {}, running on {} and {} ", s.getProductName(), s.getVersion(), j.getVersion(), o.getVersion());
			
			JCommanderHelper.logCallString(args, "smac-validate");
			
			for(String name : jcom.getParameterFilesToRead())
			{
				log.debug("Parsing (default) options from file: {} ", name);
			}

			if(options.incumbent != null && options.trajectoryFileOptions.trajectoryFiles.size() > 0)
			{
				throw new ParameterException("You cannot specify both a configuration and a trajectory file");
			}
			
			
			if(options.validationOptions.numberOfValidationRuns == 0)
			{
				throw new ParameterException("You must be willing to do at least one run with the stand alone utility");
			}
			
			//Set some default options
			if(options.trajectoryFileOptions.trajectoryFiles.size() > 0)
			{ 
			
				if(options.tunerTime == -1)
				{
					options.tunerTime = options.scenarioConfig.limitOptions.tunerTimeout;
					log.debug("Using Scenario Tuner Time {} seconds", options.tunerTime );
				}
				
				if(options.wallTime == -1)
				{
					options.wallTime = options.tunerTime;
					//options.wallTime = options.scenarioConfig.
				}
				
				
			} else
			{
				if(options.tunerTime == -1)
				{
					options.tunerTime = 0;
				}
				
				if(options.wallTime == -1)
				{
					options.wallTime = 0;
				}
				
				if(options.empiricalPerformance == -1)
				{
					options.empiricalPerformance = 0;
					
				}
				
				log.debug("Using manually set configurations");
			}
			
			//log.info("Parsing test instances from {}", options.scenarioConfig.testInstanceFile );
			
			
			//instanceFeatureFile = options.scenarioConfig.instanceFeatureFile;
			SeedableRandomPool pool = options.seedOptions.getSeedableRandomPool();
			InstanceListWithSeeds ilws = options.getTrainingAndTestProblemInstances(pool);
			
			List<ProblemInstance> testInstances = ilws.getInstances();
			InstanceSeedGenerator testInstanceSeedGen = ilws.getSeedGen();
			

			log.debug("Parsing Parameter Space File", options.scenarioConfig.algoExecOptions.paramFileDelegate.paramFile);
			//ParamConfigurationSpace configSpace = null;
			Random configSpacePRNG = pool.getRandom(SeedableRandomPoolConstants.VALIDATE_RANDOM_CONFIG_POOL);
			
			AlgorithmExecutionConfiguration execConfig = options.getAlgorithmExecutionConfig();
			
			ParamConfigurationSpace configSpace = execConfig.getParameterConfigurationSpace();
			
			Set<TrajectoryFile> tfes = new TreeSet<TrajectoryFile>();
			if(options.trajectoryFileOptions.trajectoryFiles.size() > 0)
			{
				//log.debug("Parsing Trajectory File {} " , options.trajectoryFileOptions.trajectoryFiles.getAbsolutePath());
				
				
				tfes.addAll(options.trajectoryFileOptions.parseTrajectoryFiles(configSpace));

				 if(options.validationOptions.maxTimestamp == -1)
				 {
					 if(options.validationOptions.useWallClockTime)
					 {
						 options.validationOptions.maxTimestamp = options.scenarioConfig.limitOptions.runtimeLimit;
					 } else
					 {
						 options.validationOptions.maxTimestamp = options.scenarioConfig.limitOptions.tunerTimeout;
					 }
				 }
				 
				 if(options.randomConfigurations > 0) throw new ParameterException("Cannot validate both a trajectory file and random configurations");
				 if(options.configurationList != null) throw new ParameterException("Cannot validate both a trajectory file and a configuration list");
				 if(options.incumbent != null) throw new ParameterException("Cannot validate both a trajectory and a given configuration");
				 
			} else
			{
				if (options.tunerOverheadTime == -1)
				{
					options.tunerOverheadTime = 0;	
				}
				//We are explicitly setting configurations so validate all
				options.validationOptions.validateAll = true;
				
				
				File trajectoryFile = new File("cli");
				
				List<ParamConfiguration> configToValidate = new ArrayList<ParamConfiguration>(); 
				//==== Parse the supplied configuration;
				int optionsSet=0;
				if(options.incumbent != null)
				{					
					log.debug("Parsing Supplied Configuration");
					configToValidate.add(configSpace.getConfigurationFromString(options.incumbent, StringFormat.NODB_OR_STATEFILE_SYNTAX, configSpacePRNG));
					optionsSet++;
				}
				if(options.randomConfigurations > 0)
				{
					
					log.trace("Generating {} random configurations to validate");
					for(int i=0; i < options.randomConfigurations; i++)
					{
						if(options.includeRandomAsFirstDefault && i==0)
						{
							log.trace("Using the default as the first configuration");
							configToValidate.add(configSpace.getDefaultConfiguration());
						} else
						{
							configToValidate.add(configSpace.getRandomConfiguration(configSpacePRNG));
						}
					}
					optionsSet++;
					
				}
				
				if(options.configurationList != null)
				{
					BufferedReader reader = new BufferedReader(new FileReader(options.configurationList));
					
					
					String line; 
					while((line = reader.readLine()) != null)
					{
						
						if(line.trim().isEmpty()) 
						{
							continue;
						}
						configToValidate.add(configSpace.getConfigurationFromString(line, StringFormat.NODB_OR_STATEFILE_SYNTAX, configSpacePRNG));
					}
					
					optionsSet++;
					trajectoryFile = options.configurationList;
					reader.close();
				}
				
				if(optionsSet == 0)
				{
					throw new ParameterException("You must set one of --trajectoryFile, --configuration, --configurationList, --randomConfiguration options");
				}
				if(optionsSet > 1)
				{
					throw new ParameterException("You can only set one of --trajectoryFile, --configuration, --configurationList, --randomConfiguration options");
				}
				
						
				
				
				
				List<TrajectoryFileEntry> tfeList = new ArrayList<TrajectoryFileEntry>();
				int i=0;
				for(ParamConfiguration config : configToValidate)
				{
					tfeList.add(new TrajectoryFileEntry(config, options.tunerTime + i,options.wallTime, options.empiricalPerformance, options.tunerOverheadTime + i));
					
					if(options.autoIncrementTunerTime)
					{
						i++;
					}
				}
				
				tfes.add(new TrajectoryFile(trajectoryFile, tfeList));
			}
			
			options.checkProblemInstancesCompatibleWithVerifySAT(testInstances);
			
			log.debug("Hard coding abort on crash, checkSATConsistencyException abort on first run crash options to false as they do more harm than good here");
			options.scenarioConfig.algoExecOptions.taeOpts.checkSATConsistencyException = false;
			options.scenarioConfig.algoExecOptions.taeOpts.abortOnCrash = false;
			options.scenarioConfig.algoExecOptions.taeOpts.abortOnFirstRunCrash = false;
			
			options.scenarioConfig.algoExecOptions.taeOpts.turnOffCrashes();
			
			TargetAlgorithmEvaluator validatingTae = TargetAlgorithmEvaluatorBuilder.getTargetAlgorithmEvaluator(options.scenarioConfig.algoExecOptions.taeOpts,  false,taeOptions);
			
			
			
			
			
			//log.info("Begining Validation on tuner time: {} (trajectory file time: {}) empirical performance {}, overhead time: {}, numrun: {}, configuration  \"{}\" ", arr);
			
			try {
				int coreHint = Math.max(options.scenarioConfig.algoExecOptions.taeOpts.maxConcurrentAlgoExecs, ((CommandLineTargetAlgorithmEvaluatorOptions) taeOptions.get(CommandLineTargetAlgorithmEvaluatorFactory.NAME)).cores);
			(new Validator()).multiValidate(testInstances,
					options.validationOptions,
					options.scenarioConfig.algoExecOptions.cutoffTime,
					testInstanceSeedGen,
					validatingTae,
					options.scenarioConfig.getRunObjective(),
					options.scenarioConfig.getIntraInstanceObjective(),
					options.scenarioConfig.interInstanceObj,
					tfes,
					options.waitForPersistedRunCompletion, coreHint, execConfig);

			
			} finally
			{
				validatingTae.notifyShutdown();
			}
			
			log.info("Validation Completed Successfully");
			
			System.exit(ACLibReturnValues.SUCCESS);
			
		} catch(Throwable t)
		{

			int returnValue = ACLibReturnValues.OTHER_EXCEPTION;
			if(log != null)
			{
				
				log.error(exception, "Message: {}",t.getMessage());

				
				if(t instanceof NullPointerException)
				{
					log.error("This error is most likely caused by an improper input file format, make sure the files are non empty / in the right format");
				}
				if(!(t instanceof ParameterException))
				{
					log.error(exception, "Exception:{}", t.getClass().getCanonicalName());
					StringWriter sWriter = new StringWriter();
					PrintWriter writer = new PrintWriter(sWriter);
					t.printStackTrace(writer);
					log.error(stackTrace, "StackTrace:{}",sWriter.toString());
					returnValue = ACLibReturnValues.PARAMETER_EXCEPTION;
				}
				
				
				log.info("Exiting Application with failure");
				t = t.getCause();
				
			} else
			{
				if(t instanceof ParameterException )
				{
					returnValue = ACLibReturnValues.PARAMETER_EXCEPTION;
					System.err.println(t.getMessage());
				} else
				{
					returnValue = ACLibReturnValues.OTHER_EXCEPTION;
					t.printStackTrace();
				}
				
			}
			
			
			System.exit(returnValue);
		}
		/*(new Validator()).validate(testInstances, smac.getIncumbent(),options, testInstanceSeedGen, validatingTae);*/
	}
	

}
