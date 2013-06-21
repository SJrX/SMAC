package ca.ubc.cs.beta.smac.executors;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;

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

import ca.ubc.cs.beta.aclib.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.aclib.misc.options.UsageSection;
import ca.ubc.cs.beta.aclib.misc.returnvalues.ACLibReturnValues;
import ca.ubc.cs.beta.aclib.misc.spi.SPIClassLoaderHelper;
import ca.ubc.cs.beta.aclib.misc.version.VersionTracker;
import ca.ubc.cs.beta.aclib.options.AbstractOptions;
import ca.ubc.cs.beta.aclib.options.ConfigToLaTeX;
import ca.ubc.cs.beta.aclib.probleminstance.InstanceListWithSeeds;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstanceHelper;
import ca.ubc.cs.beta.aclib.random.SeedableRandomPool;
import ca.ubc.cs.beta.aclib.random.SeedableRandomPoolConstants;
import ca.ubc.cs.beta.aclib.seedgenerator.InstanceSeedGenerator;
import ca.ubc.cs.beta.aclib.smac.ValidationExecutorOptions;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.init.TargetAlgorithmEvaluatorBuilder;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.init.TargetAlgorithmEvaluatorLoader;
import ca.ubc.cs.beta.aclib.trajectoryfile.TrajectoryFileParser;
import ca.ubc.cs.beta.aclib.trajectoryfile.TrajectoryFileEntry;
import ca.ubc.cs.beta.smac.validation.Validator;
import ec.util.MersenneTwister;

public class ValidatorExecutor {

	private static Logger log = LoggerFactory.getLogger(ValidatorExecutor.class);
	private static Marker exception;
	private static Marker stackTrace;
	
	public static void main(String[] args)
	{
		
		ValidationExecutorOptions options = new ValidationExecutorOptions();
		Map<String, AbstractOptions> taeOptions = TargetAlgorithmEvaluatorLoader.getAvailableTargetAlgorithmEvaluators();
		
		JCommander jcom = JCommanderHelper.getJCommander(options, taeOptions);
		
		jcom.setProgramName("validate");
		try {
			try {
				
				
				jcom.parse( args);
				
				log.info("==========Configuration Options==========\n{}", options.toString());
				VersionTracker.setClassLoader(SPIClassLoaderHelper.getClassLoader());
				VersionTracker.logVersions();
				
				//Logs the available target algorithm evaluators
				for(String name : taeOptions.keySet())
				{
					log.info("Target Algorithm Evaluator Available: {} ", name);
				}
				
				for(String name : jcom.getParameterFilesToRead())
				{
					log.info("Parsing (default) options from file: {} ", name);
				}
				
				
				
				if(options.incumbent != null && options.trajectoryFileOptions.trajectoryFile != null)
				{
					throw new ParameterException("You cannot specify both a configuration and a trajectory file");
				}
				
				
				
				//Set some default options
				if(options.trajectoryFileOptions.trajectoryFile != null)
				{ 
				
					if(options.tunerTime == -1)
					{
						options.tunerTime = options.scenarioConfig.tunerTimeout;
						log.info("Using Scenario Tuner Time {} seconds", options.tunerTime );
						
						
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
					
					if(options.empericalPerformance == -1)
					{
						options.empericalPerformance = 0;
						
					}
					
					log.info("Using manually set configurations");
				}
				
			
		
				//log.info("Parsing test instances from {}", options.scenarioConfig.testInstanceFile );
				
				
				//instanceFeatureFile = options.scenarioConfig.instanceFeatureFile;
				SeedableRandomPool pool = options.seedOptions.getSeedableRandomPool();
				InstanceListWithSeeds ilws = options.getTrainingAndTestProblemInstances(pool);
				
				
				
				List<ProblemInstance> testInstances = ilws.getInstances();
				InstanceSeedGenerator testInstanceSeedGen = ilws.getSeedGen();
				
	
				log.info("Parsing Parameter Space File", options.scenarioConfig.algoExecOptions.paramFileDelegate.paramFile);
				//ParamConfigurationSpace configSpace = null;
				Random configSpacePRNG = pool.getRandom(SeedableRandomPoolConstants.VALIDATE_RANDOM_CONFIG_POOL);
				
				AlgorithmExecutionConfig execConfig = options.getAlgorithmExecutionConfig();
				
				ParamConfigurationSpace configSpace = execConfig.getParamFile();
				
				
				
				List<TrajectoryFileEntry> tfes;
				if(options.trajectoryFileOptions.trajectoryFile != null)
				{
					log.info("Parsing Trajectory File {} " , options.trajectoryFileOptions.trajectoryFile.getAbsolutePath());
					
					
					tfes = options.trajectoryFileOptions.parseTrajectoryFile(configSpace);
					
					 
					 if(options.validationOptions.maxTimestamp == -1)
					 {
						 if(options.validationOptions.useWallClockTime)
						 {
							 options.validationOptions.maxTimestamp = options.scenarioConfig.tunerTimeout;
						 } else
						 {
							 options.validationOptions.maxTimestamp = options.scenarioConfig.tunerTimeout;
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
					
					
					List<ParamConfiguration> configToValidate = new ArrayList<ParamConfiguration>(); 
					//==== Parse the supplied configuration;
					int optionsSet=0;
					if(options.incumbent != null)
					{					
						log.info("Parsing Supplied Configuration");
						configToValidate.add(configSpace.getConfigurationFromString(options.incumbent, StringFormat.NODB_OR_STATEFILE_SYNTAX, configSpacePRNG));
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
					
							
					
					tfes = new ArrayList<TrajectoryFileEntry>();
					int i=0;
					for(ParamConfiguration config : configToValidate)
					{
						tfes.add(new TrajectoryFileEntry(config, options.tunerTime + i,options.wallTime, options.empericalPerformance, options.tunerOverheadTime + i));
						
						if(options.autoIncrementTunerTime)
						{
							i++;
						}
					}
					
				}
				
				options.checkProblemInstancesCompatibleWithVerifySAT(testInstances);
				
				TargetAlgorithmEvaluator validatingTae = TargetAlgorithmEvaluatorBuilder.getTargetAlgorithmEvaluator(options.scenarioConfig.algoExecOptions.taeOpts, execConfig, false,taeOptions);
				
				
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
						options.scenarioConfig.algoExecOptions.cutoffTime,
						testInstanceSeedGen,
						validatingTae,
						outputDir,
						options.scenarioConfig.runObj,
						options.scenarioConfig.intraInstanceObj,
						options.scenarioConfig.interInstanceObj,
						tfes,
						options.seedOptions.numRun,
						options.waitForPersistedRunCompletion);
				
				
				log.info("Validation Completed Successfully");
				validatingTae.notifyShutdown();
				System.exit(ACLibReturnValues.SUCCESS);
			} catch(ParameterException e)
			{
				
				
				//Converts the actual option objects into objects "UsageSection"s that are easy to manipulate
				List<UsageSection> sections = ConfigToLaTeX.getParameters(options, taeOptions);
				
				boolean showHiddenParameters = false;
				
				//A much nicer usage screen than JCommander's 
				ConfigToLaTeX.usage(sections, showHiddenParameters);
				
				
				
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
	

}
