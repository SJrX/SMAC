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

import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration;
import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.aclib.configspace.ParamFileHelper;
import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration.StringFormat;
import ca.ubc.cs.beta.aclib.execconfig.AlgorithmExecutionConfig;

import ca.ubc.cs.beta.aclib.options.ValidationExecutorOptions;
import ca.ubc.cs.beta.aclib.probleminstance.InstanceListWithSeeds;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstanceHelper;
import ca.ubc.cs.beta.aclib.seedgenerator.InstanceSeedGenerator;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.CommandLineTargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aclib.trajectoryfile.TrajectoryFileParser;
import ca.ubc.cs.beta.aclib.trajectoryfile.TrajectoryFileParser.TrajectoryFileEntry;
import ca.ubc.cs.beta.smac.validation.Validator;

public class ValidatorExecutor {

	private static Logger log = LoggerFactory.getLogger(ValidatorExecutor.class);
	private static Marker exception;
	private static Marker stackTrace;
	
	public static void main(String[] args)
	{
		
		ValidationExecutorOptions options = new ValidationExecutorOptions();
		
		JCommander com = new JCommander(options);
		com.setProgramName("validate");
		try {
			try {
				
				
				com.parse( args);
				
				if(options.incumbent != null && options.trajectoryFile != null)
				{
					throw new ParameterException("You cannot specify both a configuration and a trajectory file");
				}
				
				ParamConfiguration configToValidate; 
				
				
				if(options.trajectoryFile != null)
				{
					log.info("Using Trajectory File {} " + options.trajectoryFile.getAbsolutePath());
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
						log.info("Using configuration specified on Command Line");
				}
				
			
		
				log.info("Parsing test instances from {}", options.scenarioConfig.instanceFile );
				InstanceListWithSeeds ilws = ProblemInstanceHelper.getInstances(options.scenarioConfig.testInstanceFile, options.experimentDir,options.scenarioConfig.instanceFeatureFile, options.scenarioConfig.checkInstanceFilesExist, options.seed, Integer.MAX_VALUE);
				List<ProblemInstance> testInstances = ilws.getInstances();
				InstanceSeedGenerator testInstanceSeedGen = ilws.getSeedGen();
				
	
				log.info("Parsing Parameter Space File", options.scenarioConfig.paramFileDelegate.paramFile);
				ParamConfigurationSpace configSpace = null;
				
				
				String[] possiblePaths = { options.scenarioConfig.paramFileDelegate.paramFile, options.experimentDir + File.separator + options.scenarioConfig.paramFileDelegate.paramFile, options.scenarioConfig.algoExecOptions.algoExecDir + File.separator + options.scenarioConfig.paramFileDelegate.paramFile }; 
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
				
				if(options.trajectoryFile != null)
				{
					ConcurrentSkipListMap<Double,TrajectoryFileEntry> skipList = TrajectoryFileParser.parseTrajectoryFile(options.trajectoryFile, configSpace);
					
					TrajectoryFileEntry tfe = skipList.floorEntry(options.tunerTime).getValue();
					nearestTunerTime = skipList.floorEntry(options.tunerTime).getKey();
					if(options.empericalPerformance == -1 )
					{
						options.empericalPerformance = tfe.getEmpericalPerformance();
					}
						
					
					
					if(options.tunerOverheadTime == -1)
					{
						options.tunerOverheadTime = tfe.getACOverhead();
					}
					
					configToValidate = tfe.getConfiguration();
					
					
				} else
				{
					if (options.tunerOverheadTime == -1)
					{
						options.tunerOverheadTime = 0;	
					}
					
					if(options.incumbent == null)
					{
						log.info("Validating Default Configuration");
						configToValidate = configSpace.getDefaultConfiguration();
					} else
					{
						log.info("Parsing Supplied Configuration");
						try {
							configToValidate = configSpace.getConfigurationFromString(options.incumbent, StringFormat.NODB_SYNTAX);
						} catch(RuntimeException e)
						{
							try {
								log.info("Being nice and checking if this is a STATEFILE encoded configuration");
								configToValidate = configSpace.getConfigurationFromString(options.incumbent, StringFormat.STATEFILE_SYNTAX);
							} catch(RuntimeException e2)
							{
								throw e;
							}
							
						}
					}
					
				}
				
				AlgorithmExecutionConfig execConfig = new AlgorithmExecutionConfig(options.scenarioConfig.algoExecOptions.algoExec, options.scenarioConfig.algoExecOptions.algoExecDir, configSpace, false, options.scenarioConfig.algoExecOptions.deterministic, options.scenarioConfig.cutoffTime);
				
				
				
				
				boolean concurrentRuns = (options.maxConcurrentAlgoExecs > 1);
				
				TargetAlgorithmEvaluator validatingTae = new CommandLineTargetAlgorithmEvaluator(execConfig, concurrentRuns);
				
				
				String outputDir = System.getProperty("user.dir") + File.separator +"ValidationRun-" + (new SimpleDateFormat("yyyy-MM-dd--HH-mm-ss-SSS")).format(new Date()) +File.separator;
				
				if(options.useScenarioOutDir)
				{
					outputDir = options.scenarioConfig.outputDirectory + File.separator + "ValidationRun-" + (new SimpleDateFormat("yyyy-MM-dd--HH-mm-ss-SSS")).format(new Date()) +File.separator;
				}
				File f = new File(outputDir);
				if(!f.mkdirs())
				{
					throw new ParameterException("Couldn't make output Directory:" + outputDir);
				}
				
				Object[] arr = {options.tunerTime,nearestTunerTime, options.empericalPerformance, options.tunerOverheadTime, options.seed, configToValidate.getFormattedParamString(StringFormat.NODB_SYNTAX)};
				
				
				log.info("Begining Validation on tuner time: {} (trajectory file time: {}) emperical performance {}, overhead time: {}, numrun: {}, configuration  \"{}\" ", arr);
		
				(new Validator()).validate(testInstances,
						configToValidate,
						options.validationOptions,
						options.scenarioConfig.cutoffTime,
						testInstanceSeedGen,
						validatingTae,
						outputDir,
						options.scenarioConfig.runObj,
						options.scenarioConfig.intraInstanceObj,
						options.scenarioConfig.interInstanceObj,
						options.tunerTime,
						options.empericalPerformance,
						options.tunerOverheadTime,
						options.seed);
				
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
		/*(new Validator()).validate(testInstances, smac.getIncumbent(),options, testInstanceSeedGen, validatingTae);*/
	}
}
