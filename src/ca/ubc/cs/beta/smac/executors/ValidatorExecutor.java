package ca.ubc.cs.beta.smac.executors;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration;
import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.aclib.configspace.ParamFileHelper;
import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration.StringFormat;
import ca.ubc.cs.beta.aclib.exceptions.FeatureNotFoundException;
import ca.ubc.cs.beta.aclib.execconfig.AlgorithmExecutionConfig;

import ca.ubc.cs.beta.aclib.misc.returnvalues.ACLibReturnValues;
import ca.ubc.cs.beta.aclib.misc.version.VersionTracker;
import ca.ubc.cs.beta.aclib.options.ConfigToLaTeX;
import ca.ubc.cs.beta.aclib.options.ValidationExecutorOptions;
import ca.ubc.cs.beta.aclib.probleminstance.InstanceListWithSeeds;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstanceHelper;
import ca.ubc.cs.beta.aclib.seedgenerator.InstanceSeedGenerator;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluatorBuilder;
import ca.ubc.cs.beta.aclib.trajectoryfile.TrajectoryFileParser;
import ca.ubc.cs.beta.aclib.trajectoryfile.TrajectoryFileEntry;
import ca.ubc.cs.beta.smac.validation.Validator;

public class ValidatorExecutor {

	private static Logger log = LoggerFactory.getLogger(ValidatorExecutor.class);
	private static Marker exception;
	private static Marker stackTrace;
	
	public static void main(String[] args)
	{
		
		ValidationExecutorOptions options = new ValidationExecutorOptions();
		/*
		 * JCommander com = new JCommander(config, true, true);
		com.setProgramName("smac");
		try {
			
			
			//JCommanderHelper.parse(com, args);
			try {
				checkArgsForUsageScreenValues(args,config);
				com.parse(args);
		 */
		JCommander com = new JCommander(options, true, true);
		com.setProgramName("validate");
		try {
			try {
				
				
				com.parse( args);
				
				log.info("==========Configuration Options==========\n{}", options.toString());
				VersionTracker.setClassLoader(TargetAlgorithmEvaluatorBuilder.getClassLoader(options.scenarioConfig.algoExecOptions));
				VersionTracker.logVersions();
				
				
				if(options.incumbent != null && options.trajectoryFile != null)
				{
					throw new ParameterException("You cannot specify both a configuration and a trajectory file");
				}
				
				
				
				//Set some default options
				if(options.trajectoryFile != null)
				{ 
					log.info("Using Trajectory File {} " , options.trajectoryFile.getAbsolutePath());
					if(options.tunerTime == -1)
					{
						options.tunerTime = options.scenarioConfig.tunerTimeout;
						log.info("Using Scenario Tuner Time {} seconds", options.tunerTime );
						
						
					}
					
					
					
				} else
				{
					if(options.tunerTime == -1)
					{
						options.tunerTime = 0;
					}
					
					if(options.empericalPerformance == -1)
					{
						options.empericalPerformance = 0;
						
					}
					
					log.info("Using manually set configurations");
				}
				
			
		
				log.info("Parsing test instances from {}", options.scenarioConfig.testInstanceFile );
				
				String instanceFeatureFile = null;
				
				
				instanceFeatureFile = options.scenarioConfig.instanceFeatureFile;
				InstanceListWithSeeds ilws;
				
				
				String instanceFile ;
				if(options.validateTestInstances)
				{
					instanceFile = options.scenarioConfig.testInstanceFile;
				} else
				{
					instanceFile = options.scenarioConfig.instanceFile;
				}
				
				try {
					 ilws = ProblemInstanceHelper.getInstances(instanceFile, options.experimentDir,options.scenarioConfig.instanceFeatureFile, options.scenarioConfig.checkInstanceFilesExist, options.seed, Integer.MAX_VALUE);
				} catch(FeatureNotFoundException e)
				{
					ilws = ProblemInstanceHelper.getInstances(instanceFile, options.experimentDir,null, options.scenarioConfig.checkInstanceFilesExist, options.seed, Integer.MAX_VALUE);
				}
				
				List<ProblemInstance> testInstances = ilws.getInstances();
				InstanceSeedGenerator testInstanceSeedGen = ilws.getSeedGen();
				
	
				log.info("Parsing Parameter Space File", options.scenarioConfig.paramFileDelegate.paramFile);
				ParamConfigurationSpace configSpace = null;
				
				
				String[] possiblePaths = { options.scenarioConfig.paramFileDelegate.paramFile, options.experimentDir + File.separator + options.scenarioConfig.paramFileDelegate.paramFile, options.scenarioConfig.algoExecOptions.algoExecDir + File.separator + options.scenarioConfig.paramFileDelegate.paramFile }; 
				for(String path : possiblePaths)
				{
					try {
						log.debug("Trying param file in path {} ", path);
						configSpace = ParamFileHelper.getParamFileParser(path,options.configurationSeed);
						break;
					} catch(IllegalStateException e)
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
				
				double nearestTunerTime = 0;
				List<TrajectoryFileEntry> tfes;
				if(options.trajectoryFile != null)
				{
					log.info("Parsing trajectory file");
					 tfes = TrajectoryFileParser.parseTrajectoryFileAsList(options.trajectoryFile, configSpace);
					 
					 if(options.validationOptions.maxTimestamp == -1)
					 {
						 options.validationOptions.maxTimestamp = options.scenarioConfig.tunerTimeout;
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
					
					
					List<ParamConfiguration> configToValidate = new ArrayList<ParamConfiguration>(); 
					//==== Parse the supplied configuration;
					int optionsSet=0;
					if(options.incumbent != null)
					{					
						log.info("Parsing Supplied Configuration");
						configToValidate.add(configFromString(options.incumbent, configSpace));
						optionsSet++;
					}
					if(options.randomConfigurations > 0)
					{
						
						log.info("Generating {} random configurations to validate");
						for(int i=0; i < options.randomConfigurations; i++)
						{
							if(options.includeRandomAsFirstDefault && i==0)
							{
								log.debug("Using the default as the first configuration");
								configToValidate.add(configSpace.getDefaultConfiguration());
							} else
							{
								configToValidate.add(configSpace.getRandomConfiguration());
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
							configToValidate.add(configFromString(line, configSpace));
						}
						
						optionsSet++;
					}
					
					if(optionsSet == 0)
					{
						throw new ParameterException("You must set one of --trajectoryFile, --configuration, --configurationList, --randomConfiguration options");
					}
					if(optionsSet > 1)
					{
						throw new ParameterException("You can only set one of --trajectoryFile, --configuration, --configurationList, --randomConfiguration options");
					}
					
							
					
					tfes = new ArrayList<TrajectoryFileEntry>();
					int i=0;
					for(ParamConfiguration config : configToValidate)
					{
						
						
						tfes.add(new TrajectoryFileEntry(config, options.tunerTime + i, options.empericalPerformance, options.tunerOverheadTime + i));
						
						if(options.autoIncrementTunerTime)
						{
							i++;
						}
					}
					
					
					
				
					
				}
				
				String algoExecDir = options.scenarioConfig.algoExecOptions.algoExecDir;
				File f2 = new File(algoExecDir);
				if (!f2.isAbsolute()){
					f2 = new File(options.experimentDir + File.separator + algoExecDir);
				}
				
				
				AlgorithmExecutionConfig execConfig = new AlgorithmExecutionConfig(options.scenarioConfig.algoExecOptions.algoExec, f2.getAbsolutePath(), configSpace, false, options.scenarioConfig.algoExecOptions.deterministic, options.scenarioConfig.cutoffTime );
			
				
				//AlgorithmExecutionConfig execConfig = new AlgorithmExecutionConfig(options.scenarioConfig.algoExecOptions.algoExec, options.scenarioConfig.algoExecOptions.algoExecDir, configSpace, false, options.scenarioConfig.algoExecOptions.deterministic, options.scenarioConfig.cutoffTime);
				
				
				if(options.scenarioConfig.algoExecOptions.verifySAT == null)
				{
					boolean verifySATCompatible = ProblemInstanceHelper.isVerifySATCompatible(testInstances);
					if(verifySATCompatible)
					{
						log.debug("Instance Specific Information is compatible with Verifying SAT, enabling option");
						options.scenarioConfig.algoExecOptions.verifySAT = true;
					} else
					{
						log.debug("Instance Specific Information is NOT compatible with Verifying SAT, disabling option");
						options.scenarioConfig.algoExecOptions.verifySAT = false;
					}
					
				
				} else if(options.scenarioConfig.algoExecOptions.verifySAT == true)
				{
					boolean verifySATCompatible = ProblemInstanceHelper.isVerifySATCompatible(testInstances);
					if(!verifySATCompatible)
					{
						log.warn("Verify SAT set to true, but some instances have instance specific information that isn't in {SAT, SATISFIABLE, UNKNOWN, UNSAT, UNSATISFIABLE}");
					}
						
				}
				
				TargetAlgorithmEvaluator validatingTae = TargetAlgorithmEvaluatorBuilder.getTargetAlgorithmEvaluator(options.scenarioConfig, execConfig, false);
				
				
				String outputDir = System.getProperty("user.dir") + File.separator +"ValidationRun-" + (new SimpleDateFormat("yyyy-MM-dd--HH-mm-ss-SSS")).format(new Date()) +File.separator;
				
				if(options.useScenarioOutDir)
				{
					outputDir = options.scenarioConfig.outputDirectory + File.separator;
				}
				File f = new File(outputDir);
				
				
				if(!f.mkdirs() && !(f.exists() && f.isDirectory() && f.canWrite()))
				{
					throw new ParameterException("Couldn't make output Directory:" + outputDir);
				}
				
				
				
				
				//log.info("Begining Validation on tuner time: {} (trajectory file time: {}) emperical performance {}, overhead time: {}, numrun: {}, configuration  \"{}\" ", arr);
				log.info("Beginning Validation on {} entries", tfes.size());
				(new Validator()).validate(testInstances,
						options.validationOptions,
						options.scenarioConfig.cutoffTime,
						testInstanceSeedGen,
						validatingTae,
						outputDir,
						options.scenarioConfig.runObj,
						options.scenarioConfig.intraInstanceObj,
						options.scenarioConfig.interInstanceObj,
						tfes,
						options.numRun,
						options.waitForPersistedRunCompletion);
				
				
				log.info("Validation Completed Successfully");
				validatingTae.notifyShutdown();
				System.exit(ACLibReturnValues.SUCCESS);
			} catch(ParameterException e)
			{
				
				
				ConfigToLaTeX.usage(ConfigToLaTeX.getParameters(options));
				
				throw e;
			}	
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
	
	
	public static ParamConfiguration configFromString(String input, ParamConfigurationSpace configSpace)
	{
		
		if(input.toUpperCase().equals("DEFAULT") || input.toUpperCase().equals("<DEFAULT>"))
		{
			log.debug("Input is asking for the default configuration");
			ParamConfiguration config = configSpace.getDefaultConfiguration();
			log.debug("Configuration generated {}", config.getFormattedParamString(StringFormat.NODB_SYNTAX));
			return configSpace.getRandomConfiguration();
		}
		
		if(input.toUpperCase().equals("RANDOM") || input.toUpperCase().equals("<RANDOM>"))
		{
			log.debug("Input is asking for a random configuration");
			ParamConfiguration config = configSpace.getRandomConfiguration();
			log.debug("Configuration generated {}", config.getFormattedParamString(StringFormat.NODB_SYNTAX));
			return configSpace.getRandomConfiguration();
		}
		
		try {
			return configSpace.getConfigurationFromString(input, StringFormat.NODB_SYNTAX);
		} catch(RuntimeException e)
		{
			try {
				log.info("Being nice and checking if this is a STATEFILE encoded configuration");
				return configSpace.getConfigurationFromString(input, StringFormat.STATEFILE_SYNTAX);
			} catch(RuntimeException e2)
			{
				throw e;
			}
			
		}
		
	}
}
