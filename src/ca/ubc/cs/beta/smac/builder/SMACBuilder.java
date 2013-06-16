package ca.ubc.cs.beta.smac.builder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.beust.jcommander.ParameterException;

import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration;
import ca.ubc.cs.beta.aclib.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.aclib.configspace.ParamFileHelper;
import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration.StringFormat;
import ca.ubc.cs.beta.aclib.eventsystem.EventManager;
import ca.ubc.cs.beta.aclib.execconfig.AlgorithmExecutionConfig;
import ca.ubc.cs.beta.aclib.model.builder.HashCodeVerifyingModelBuilder;
import ca.ubc.cs.beta.aclib.options.AbstractOptions;
import ca.ubc.cs.beta.aclib.options.SMACOptions;
import ca.ubc.cs.beta.aclib.probleminstance.InstanceListWithSeeds;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstanceHelper;
import ca.ubc.cs.beta.aclib.random.SeedableRandomPool;
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
import ca.ubc.cs.beta.smac.AbstractAlgorithmFramework;
import ca.ubc.cs.beta.smac.SequentialModelBasedAlgorithmConfiguration;

/**
 * Builds an Automatic Configurator
 * @author Steve Ramage <seramage@cs.ubc.ca>
 */
public class SMACBuilder {

	private static transient Logger log = LoggerFactory.getLogger(SMACBuilder.class);
	
	private final EventManager eventManager; 
	
	
	public SMACBuilder()
	{
		this.eventManager = new EventManager();
	}
	
	
	public EventManager getEventManager()
	{
		return eventManager;
	}
	
	private List<ProblemInstance> instances = null;
	
	
	private InstanceSeedGenerator instanceSeedGen = null;
	
	
	public void setInstances(List<ProblemInstance> instances)
	{
		this.instances = instances;
	}
	
	public void setInstanceSeedGenerator(InstanceSeedGenerator insc)
	{
		this.instanceSeedGen = insc;
	}
	
	
	public List<ProblemInstance> getInstances()
	{
		return instances;
	}
	
	
	public void setInstancesAndSeedGenFromOptions(SMACOptions options) throws IOException
	{
		InstanceListWithSeeds ilws;
		
		ilws = ProblemInstanceHelper.getInstances(options.scenarioConfig.instanceFile,options.experimentDir, options.scenarioConfig.instanceFeatureFile, options.scenarioConfig.checkInstanceFilesExist, options.seedOptions.numRun+options.seedOptions.seedOffset+1, (options.scenarioConfig.algoExecOptions.deterministic));
		
		instanceSeedGen = ilws.getSeedGen();
		
		//logger.info("Instance Seed Generator reports {} seeds ", instanceSeedGen.getInitialInstanceSeedCount());
		
		
		
		instances = ilws.getInstances();
		
	}
	
	
	public AbstractAlgorithmFramework getSMAC(SMACOptions options,Map<String, AbstractOptions> taeOptions, TargetAlgorithmEvaluator tae)
	{
	
		
		SeedableRandomPool pool = options.seedOptions.getSeedableRandomPool(); 
		 

		
		
		
		if(instances == null)
		{
			throw new IllegalStateException("Instances must be set prior to getting SMAC Object");
		}
		
		if(instanceSeedGen == null)
		{
			throw new IllegalStateException("InstanceSeedGen must be set prior to getting SMAC Object");
		}
		
		if(instanceSeedGen.allInstancesHaveSameNumberOfSeeds())
		{
			//logger.info("Instance Seed Generator reports that all instances have the same number of available seeds");
			throw new ParameterException("All Training Instances must have the same number of seeds in this version of SMAC");
		} 
		
		
		String runGroupName = options.getRunGroupName(taeOptions.values());;
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
		for(String path : possiblePaths)
		{
			try {
				log.debug("Trying param file in path {} ", path);
				
				configSpace = ParamFileHelper.getParamFileParser(path);
				break;
			}catch(IllegalStateException e)
			{ 
			
			}
		}
		
		
		if(configSpace == null)
		{
			throw new ParameterException("Could not find param file");
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
		
		
		TargetAlgorithmEvaluator algoEval = TargetAlgorithmEvaluatorBuilder.getTargetAlgorithmEvaluator(options.scenarioConfig.algoExecOptions.taeOpts, execConfig,true,taeOptions,tae);
		

		if(options.modelHashCodeFile != null)
		{
			log.info("Algorithm Execution will verify model Hash Codes");
			parseModelHashCodes(options.modelHashCodeFile);
		}
		
		
		ParamConfiguration initialIncumbent = configSpace.getConfigurationFromString(options.initialIncumbent, StringFormat.NODB_SYNTAX);
		
		
		if(!initialIncumbent.equals(configSpace.getDefaultConfiguration()))
		{
			log.info("Initial Incumbent set to \"{}\" ", initialIncumbent.getFormattedParamString(StringFormat.NODB_SYNTAX));
		} else
		{
			log.info("Initial Incumbent is the default \"{}\" ", initialIncumbent.getFormattedParamString(StringFormat.NODB_SYNTAX));
		}
		
		
		AbstractAlgorithmFramework smac;
		ThreadSafeRunHistory rh = new ThreadSafeRunHistoryWrapper(new NewRunHistory(options.scenarioConfig.intraInstanceObj, options.scenarioConfig.interInstanceObj, options.scenarioConfig.runObj));
		
		switch(options.execMode)
		{
			case ROAR:
				smac = new AbstractAlgorithmFramework(options,instances,algoEval,sf, configSpace, instanceSeedGen, initialIncumbent, eventManager, rh, pool, runGroupName);
				break;
			case SMAC:
				smac = new SequentialModelBasedAlgorithmConfiguration(options, instances, algoEval, options.expFunc.getFunction(),sf, configSpace, instanceSeedGen,  initialIncumbent, eventManager, rh, pool, runGroupName);
				break;
			default:
				throw new IllegalArgumentException("Execution Mode Specified is not supported");
		}
		
		if(options.restoreIteration != null)
		{
			
			restoreState(options, restoreSF, smac, configSpace, instances, execConfig, rh);
		}
		
		
		return smac;
	}
	
	
	
	

	
	private static Pattern modelHashCodePattern = Pattern.compile("^(Preprocessed|Random) Forest Built with Hash Code:\\s*\\d+?\\z");
		private void parseModelHashCodes(File modelHashCodeFile) {
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
		
		private void restoreState(SMACOptions options, StateFactory sf, AbstractAlgorithmFramework smac,  ParamConfigurationSpace configSpace, List<ProblemInstance> instances, AlgorithmExecutionConfig execConfig, RunHistory rh) {
			
			if(options.restoreIteration < 0)
			{
				throw new ParameterException("Iteration must be a non-negative integer");
			}
			
			StateDeserializer sd = sf.getStateDeserializer("it", options.restoreIteration, configSpace, instances, execConfig, rh);
			
			smac.restoreState(sd);
			
			
			
		}
		
}
