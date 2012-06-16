package ca.ubc.cs.beta.smac.executors;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

import ca.ubc.cs.beta.ac.config.ProblemInstance;
import ca.ubc.cs.beta.config.AlgorithmExecutionConfig;
import ca.ubc.cs.beta.config.JCommanderHelper;
import ca.ubc.cs.beta.config.ValidationExecutorConfiguration;
import ca.ubc.cs.beta.configspace.ParamConfiguration;
import ca.ubc.cs.beta.configspace.ParamConfiguration.StringFormat;
import ca.ubc.cs.beta.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.configspace.ParamFileHelper;
import ca.ubc.cs.beta.probleminstance.InstanceListWithSeeds;
import ca.ubc.cs.beta.probleminstance.ProblemInstanceHelper;
import ca.ubc.cs.beta.seedgenerator.InstanceSeedGenerator;
import ca.ubc.cs.beta.smac.ac.runners.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.smac.validation.Validator;

public class ValidatorExecutor {

	private static Logger log = LoggerFactory.getLogger(ValidatorExecutor.class);
	private static Marker exception;
	private static Marker stackTrace;
	
	public static void main(String[] args)
	{
		
		
		
		ValidationExecutorConfiguration config = new ValidationExecutorConfiguration();
		
		
		
		JCommander com = new JCommander(config);
		com.setProgramName("validate");
		try {
			try {
				
				
				JCommanderHelper.parse(com, args);
				
		
				log.info("Parsing test instances from {}", config.scenarioConfig.instanceFile );
				InstanceListWithSeeds ilws = ProblemInstanceHelper.getInstances(config.scenarioConfig.testInstanceFile, config.experimentDir,config.scenarioConfig.instanceFeatureFile, !config.scenarioConfig.skipInstanceFileCheck, config.seed, Integer.MAX_VALUE);
				List<ProblemInstance> testInstances = ilws.getInstances();
				InstanceSeedGenerator testInstanceSeedGen = ilws.getSeedGen();
				
	
				log.info("Parsing Parameter Space File", config.paramFile);
				ParamConfigurationSpace configSpace = null;
				
				
				String[] possiblePaths = { config.paramFile, config.experimentDir + File.separator + config.paramFile, config.scenarioConfig.algoExecConfig.algoExecDir + File.separator + config.paramFile }; 
				for(String path : possiblePaths)
				{
					try {
						log.debug("Trying param file in path {} ", path);
						configSpace = ParamFileHelper.getParamFileParser(path);
						break;
					} catch(IllegalStateException e)
					{ 
	
						
					
					}
				}
				AlgorithmExecutionConfig execConfig = new AlgorithmExecutionConfig(config.scenarioConfig.algoExecConfig.algoExec, config.scenarioConfig.algoExecConfig.algoExecDir, configSpace, false);
				
				
				
				
				boolean concurrentRuns = (config.maxConcurrentAlgoExecs > 1);
				
				TargetAlgorithmEvaluator validatingTae = new TargetAlgorithmEvaluator(execConfig, concurrentRuns);
				
				String outputDir = System.getProperty("user.dir") + File.separator +"ValidationRun-" + (new SimpleDateFormat("yyyy-MM-dd--HH-mm-ss-SSS")).format(new Date()) +File.separator;
				
				File f = new File(outputDir);
				if(!f.mkdirs())
				{
					throw new ParameterException("Couldn't make output Directory:" + outputDir);
				}
				ParamConfiguration configToValidate; 
				
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
				log.info("Begining Validation on {}", configToValidate.getFormattedParamString(StringFormat.NODB_SYNTAX));
				(new Validator()).validate(testInstances, configToValidate,config.validationOptions,config.scenarioConfig.cutoffTime, testInstanceSeedGen, validatingTae, outputDir, config.scenarioConfig.runObj, config.scenarioConfig.overallObj, config.tunerTime);
				
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
