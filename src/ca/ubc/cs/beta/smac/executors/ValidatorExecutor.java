package ca.ubc.cs.beta.smac.executors;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
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
				
				ParamConfiguration configToValidate; 
				
				
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
					
					log.info("Using configuration specified on Command Line");
				}
				
			
		
				log.info("Parsing test instances from {}", options.scenarioConfig.testInstanceFile );
				
				String instanceFeatureFile = null;
				
				
				instanceFeatureFile = options.scenarioConfig.instanceFeatureFile;
				InstanceListWithSeeds ilws;
				try {
					 ilws = ProblemInstanceHelper.getInstances(options.scenarioConfig.testInstanceFile, options.experimentDir,options.scenarioConfig.instanceFeatureFile, options.scenarioConfig.checkInstanceFilesExist, options.seed, Integer.MAX_VALUE);
				} catch(FeatureNotFoundException e)
				{
					ilws = ProblemInstanceHelper.getInstances(options.scenarioConfig.testInstanceFile, options.experimentDir,null, options.scenarioConfig.checkInstanceFilesExist, options.seed, Integer.MAX_VALUE);
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
						configSpace = ParamFileHelper.getParamFileParser(path,1234);
						break;
					} catch(IllegalStateException e)
					{ 
	
						
					
					}
				}
				
				double nearestTunerTime = 0;
				List<TrajectoryFileEntry> tfes;
				if(options.trajectoryFile != null)
				{
					
					 tfes = TrajectoryFileParser.parseTrajectoryFileAsList(options.trajectoryFile, configSpace);
					 
					 if(options.validationOptions.maxTimestamp == -1)
					 {
						 options.validationOptions.maxTimestamp = options.scenarioConfig.tunerTimeout;
					 }
				
				} else
				{
					if (options.tunerOverheadTime == -1)
					{
						options.tunerOverheadTime = 0;	
					}
					
					if(options.incumbent == null)
					{
						log.warn("No configuration supplied");
						
						
						configToValidate = configSpace.getDefaultConfiguration();
						log.info("To validate the incumbent please use --configuration \"" + configToValidate.getFormattedParamString(StringFormat.NODB_SYNTAX) + "\"");
						throw new ParameterException("Must supply a configuration to validate");
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
					
					tfes = Collections.singletonList(new TrajectoryFileEntry(configToValidate, options.tunerTime, options.empericalPerformance, options.tunerOverheadTime));
					
				}
				
				String algoExecDir = options.scenarioConfig.algoExecOptions.algoExecDir;
				File f2 = new File(algoExecDir);
				if (!f2.isAbsolute()){
					f2 = new File(options.experimentDir + File.separator + algoExecDir);
				}
				
				
				AlgorithmExecutionConfig execConfig = new AlgorithmExecutionConfig(options.scenarioConfig.algoExecOptions.algoExec, f2.getAbsolutePath(), configSpace, false, options.scenarioConfig.algoExecOptions.deterministic, options.scenarioConfig.cutoffTime );
			
				
				//AlgorithmExecutionConfig execConfig = new AlgorithmExecutionConfig(options.scenarioConfig.algoExecOptions.algoExec, options.scenarioConfig.algoExecOptions.algoExecDir, configSpace, false, options.scenarioConfig.algoExecOptions.deterministic, options.scenarioConfig.cutoffTime);
				
				
				
				
				
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
						options.numRun);
				
				
				log.info("Validation Completed Successfully");
				validatingTae.notifyShutdown();
				System.exit(SMACReturnValues.SUCCESS);
			} catch(ParameterException e)
			{
				
				
				ConfigToLaTeX.usage(ConfigToLaTeX.getParameters(options));
				
				throw e;
			}	
		} catch(Throwable t)
		{

			int returnValue = SMACReturnValues.OTHER_EXCEPTION;
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
					returnValue = SMACReturnValues.PARAMETER_EXCEPTION;
				}
				
					
				
				
				
				log.info("Exiting Application with failure");
				t = t.getCause();
				
			} else
			{
				if(t instanceof ParameterException )
				{
					returnValue = SMACReturnValues.PARAMETER_EXCEPTION;
					System.err.println(t.getMessage());
				} else
				{
					returnValue = SMACReturnValues.OTHER_EXCEPTION;
					t.printStackTrace();
				}
				
			}
			
			System.exit(returnValue);
		}
		/*(new Validator()).validate(testInstances, smac.getIncumbent(),options, testInstanceSeedGen, validatingTae);*/
	}
}
