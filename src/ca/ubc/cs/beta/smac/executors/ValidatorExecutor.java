package ca.ubc.cs.beta.smac.executors;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import ca.ubc.cs.beta.aclib.algorithmrunner.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration;
import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.aclib.configspace.ParamFileHelper;
import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration.StringFormat;
import ca.ubc.cs.beta.aclib.execconfig.AlgorithmExecutionConfig;
import ca.ubc.cs.beta.aclib.misc.associatedvalue.AssociatedValue;
import ca.ubc.cs.beta.aclib.misc.jcommander.JCommanderHelper;
import ca.ubc.cs.beta.aclib.options.ValidationExecutorOptions;
import ca.ubc.cs.beta.aclib.probleminstance.InstanceListWithSeeds;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstanceHelper;
import ca.ubc.cs.beta.aclib.seedgenerator.InstanceSeedGenerator;
import ca.ubc.cs.beta.aclib.trajectoryfile.TrajectoryFileParser;
import ca.ubc.cs.beta.aclib.trajectoryfile.TrajectoryFileParser.TrajectoryFileEntry;
import ca.ubc.cs.beta.smac.validation.Validator;

public class ValidatorExecutor {

	private static Logger log = LoggerFactory.getLogger(ValidatorExecutor.class);
	private static Marker exception;
	private static Marker stackTrace;
	
	public static void main(String[] args)
	{
		
		
		
		ValidationExecutorOptions config = new ValidationExecutorOptions();
		
		
		
		JCommander com = new JCommander(config);
		com.setProgramName("validate");
		try {
			try {
				
				
				JCommanderHelper.parse(com, args);
				
				if(config.incumbent != null && config.trajectoryFile != null)
				{
					throw new ParameterException("You cannot specify both a configuration and a trajectory file");
				}
				
				ParamConfiguration configToValidate; 
				
				
				if(config.trajectoryFile != null)
				{
					log.info("Using Trajectory File {} " + config.trajectoryFile.getAbsolutePath());
					if(config.tunerTime == -1)
					{
						config.tunerTime = config.scenarioConfig.tunerTimeout;
						log.info("Using Scenario Tuner Time {} seconds", config.tunerTime );
						
						
					}
					
					
					
				} else
				{
					if(config.tunerTime == -1)
					{
						config.tunerTime = 0;
					}
					
					if(config.empericalPerformance == -1)
					{
						config.empericalPerformance = 0;
						
					}
						log.info("Using configuration specified on Command Line");
				}
				
			
		
				log.info("Parsing test instances from {}", config.scenarioConfig.instanceFile );
				InstanceListWithSeeds ilws = ProblemInstanceHelper.getInstances(config.scenarioConfig.testInstanceFile, config.experimentDir,config.scenarioConfig.instanceFeatureFile, !config.scenarioConfig.skipInstanceFileCheck, config.seed, Integer.MAX_VALUE);
				List<ProblemInstance> testInstances = ilws.getInstances();
				InstanceSeedGenerator testInstanceSeedGen = ilws.getSeedGen();
				
	
				log.info("Parsing Parameter Space File", config.scenarioConfig.paramFileDelegate.paramFile);
				ParamConfigurationSpace configSpace = null;
				
				
				String[] possiblePaths = { config.scenarioConfig.paramFileDelegate.paramFile, config.experimentDir + File.separator + config.scenarioConfig.paramFileDelegate.paramFile, config.scenarioConfig.algoExecOptions.algoExecDir + File.separator + config.scenarioConfig.paramFileDelegate.paramFile }; 
				for(String path : possiblePaths)
				{
					try {
						log.debug("Trying param file in path {} ", path);
						configSpace = ParamFileHelper.getParamFileParser(path,1234);
						break;
					} catch(IllegalStateException e)
					{ 
	
						
					
					}
				}
				
				double nearestTunerTime = 0;
				
				if(config.trajectoryFile != null)
				{
					ConcurrentSkipListMap<Double,TrajectoryFileEntry> skipList = TrajectoryFileParser.parseTrajectoryFile(config.trajectoryFile, configSpace);
					
					TrajectoryFileEntry tfe = skipList.floorEntry(config.tunerTime).getValue();
					nearestTunerTime = skipList.floorEntry(config.tunerTime).getKey();
					if(config.empericalPerformance == -1 )
					{
						config.empericalPerformance = tfe.getEmpericalPerformance();
					}
						
					
					
					if(config.tunerOverheadTime == -1)
					{
						config.tunerOverheadTime = tfe.getACOverhead();
					}
					
					configToValidate = tfe.getConfiguration();
					
					
				} else
				{
					if (config.tunerOverheadTime == -1)
					{
						config.tunerOverheadTime = 0;	
					}
					
					if(config.incumbent == null)
					{
						log.info("Validating Default Configuration");
						configToValidate = configSpace.getDefaultConfiguration();
					} else
					{
						log.info("Parsing Supplied Configuration");
						try {
							configToValidate = configSpace.getConfigurationFromString(config.incumbent, StringFormat.NODB_SYNTAX);
						} catch(RuntimeException e)
						{
							try {
								log.info("Being nice and checking if this is a STATEFILE encoded configuration");
								configToValidate = configSpace.getConfigurationFromString(config.incumbent, StringFormat.STATEFILE_SYNTAX);
							} catch(RuntimeException e2)
							{
								throw e;
							}
							
						}
					}
					
				}
				
				AlgorithmExecutionConfig execConfig = new AlgorithmExecutionConfig(config.scenarioConfig.algoExecOptions.algoExec, config.scenarioConfig.algoExecOptions.algoExecDir, configSpace, false);
				
				
				
				
				boolean concurrentRuns = (config.maxConcurrentAlgoExecs > 1);
				
				TargetAlgorithmEvaluator validatingTae = new TargetAlgorithmEvaluator(execConfig, concurrentRuns);
				
				
				String outputDir = System.getProperty("user.dir") + File.separator +"ValidationRun-" + (new SimpleDateFormat("yyyy-MM-dd--HH-mm-ss-SSS")).format(new Date()) +File.separator;
				
				if(config.useScenarioOutDir)
				{
					outputDir = config.scenarioConfig.outputDirectory + File.separator + "ValidationRun-" + (new SimpleDateFormat("yyyy-MM-dd--HH-mm-ss-SSS")).format(new Date()) +File.separator;
				}
				File f = new File(outputDir);
				if(!f.mkdirs())
				{
					throw new ParameterException("Couldn't make output Directory:" + outputDir);
				}
				
				Object[] arr = {config.tunerTime,nearestTunerTime, config.empericalPerformance, config.tunerOverheadTime, config.seed, configToValidate.getFormattedParamString(StringFormat.NODB_SYNTAX)};
				
				
				log.info("Begining Validation on tuner time: {} (trajectory file time: {}) emperical performance {}, overhead time: {}, numrun: {}, configuration  \"{}\" ", arr);
		
				(new Validator()).validate(testInstances,
						configToValidate,
						config.validationOptions,
						config.scenarioConfig.cutoffTime,
						testInstanceSeedGen,
						validatingTae,
						outputDir,
						config.scenarioConfig.runObj,
						config.scenarioConfig.intraInstanceObj,
						config.scenarioConfig.interInstanceObj,
						config.tunerTime,
						config.empericalPerformance,
						config.tunerOverheadTime,
						config.seed);
				
				log.info("Validation Completed Successfully");
			} catch(ParameterException e)
			{
				com.usage();
				throw e;
			}	
		} catch(Throwable t)
		{

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
				}
				
					
				
				
				
				log.info("Exiting Application with failure");
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
		/*(new Validator()).validate(testInstances, smac.getIncumbent(),config, testInstanceSeedGen, validatingTae);*/
	}
}
