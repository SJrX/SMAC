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
import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration.StringFormat;
import ca.ubc.cs.beta.aclib.configspace.tracking.ParamConfigurationOriginTracker;
import ca.ubc.cs.beta.aclib.configspace.tracking.RealParamConfigurationOriginTracker;
import ca.ubc.cs.beta.aclib.eventsystem.EventManager;
import ca.ubc.cs.beta.aclib.eventsystem.events.ac.AutomaticConfigurationEnd;
import ca.ubc.cs.beta.aclib.eventsystem.events.ac.IncumbentPerformanceChangeEvent;
import ca.ubc.cs.beta.aclib.eventsystem.events.basic.AlgorithmRunCompletedEvent;
import ca.ubc.cs.beta.aclib.eventsystem.events.model.ModelBuildStartEvent;
import ca.ubc.cs.beta.aclib.eventsystem.handlers.LogRuntimeStatistics;
import ca.ubc.cs.beta.aclib.eventsystem.handlers.ParamConfigurationIncumbentChangerOriginTracker;
import ca.ubc.cs.beta.aclib.eventsystem.handlers.ParamConfigurationOriginLogger;
import ca.ubc.cs.beta.aclib.execconfig.AlgorithmExecutionConfig;
import ca.ubc.cs.beta.aclib.model.builder.HashCodeVerifyingModelBuilder;
import ca.ubc.cs.beta.aclib.options.AbstractOptions;
import ca.ubc.cs.beta.aclib.probleminstance.InstanceListWithSeeds;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstanceOptions.TrainTestInstances;
import ca.ubc.cs.beta.aclib.random.SeedableRandomPool;
import ca.ubc.cs.beta.aclib.runhistory.NewRunHistory;
import ca.ubc.cs.beta.aclib.runhistory.RunHistory;
import ca.ubc.cs.beta.aclib.runhistory.ThreadSafeRunHistory;
import ca.ubc.cs.beta.aclib.runhistory.ThreadSafeRunHistoryWrapper;
import ca.ubc.cs.beta.aclib.seedgenerator.InstanceSeedGenerator;
import ca.ubc.cs.beta.aclib.smac.SMACOptions;
import ca.ubc.cs.beta.aclib.state.StateDeserializer;
import ca.ubc.cs.beta.aclib.state.StateFactory;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.init.TargetAlgorithmEvaluatorBuilder;
import ca.ubc.cs.beta.aclib.termination.CompositeTerminationCondition;
import ca.ubc.cs.beta.aclib.trajectoryfile.TrajectoryFileLogger;
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
	
	
	public void setInstancesAndSeedGenFromOptions(SMACOptions options, SeedableRandomPool pool) throws IOException
	{
		InstanceListWithSeeds ilws;	
		
		TrainTestInstances tti = options.getTrainingAndTestProblemInstances(pool);
		
		
		
		
		
		instanceSeedGen = tti.getTrainingInstances().getSeedGen();		
		//logger.info("Instance Seed Generator reports {} seeds ", instanceSeedGen.getInitialInstanceSeedCount());

		instances = tti.getTrainingInstances().getInstances();
		
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
		String outputDir = options.getOutputDirectory(runGroupName);
		StateFactory restoreSF = options.getRestoreStateFactory(outputDir);
	
		AlgorithmExecutionConfig execConfig =options.getAlgorithmExecutionConfig();
	
		ParamConfigurationSpace configSpace = execConfig.getParamFile();

		
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
		
		StateFactory sf = options.getSaveStateFactory(outputDir);
		CompositeTerminationCondition termCond = options.scenarioConfig.limitOptions.getTerminationConditions();
		
		LogRuntimeStatistics logRT = new LogRuntimeStatistics(rh, termCond, execConfig.getAlgorithmCutoffTime());
		TrajectoryFileLogger tLog = new TrajectoryFileLogger(rh, termCond, outputDir +  File.separator + "traj-run-" + options.seedOptions.numRun, initialIncumbent);
	
		
		termCond.registerWithEventManager(eventManager);
		eventManager.registerHandler(ModelBuildStartEvent.class, logRT);
		eventManager.registerHandler(IncumbentPerformanceChangeEvent.class,logRT);
		eventManager.registerHandler(AlgorithmRunCompletedEvent.class, logRT);
		eventManager.registerHandler(AutomaticConfigurationEnd.class, logRT);
		
		eventManager.registerHandler(IncumbentPerformanceChangeEvent.class, tLog);
		eventManager.registerHandler(AutomaticConfigurationEnd.class, tLog);
		
		ParamConfigurationOriginTracker configTracker = new RealParamConfigurationOriginTracker();
		configTracker.addConfiguration(initialIncumbent, "DEFAULT", "true");
		eventManager.registerHandler(AutomaticConfigurationEnd.class, new ParamConfigurationOriginLogger(configTracker, outputDir + File.separator + "state-run" + options.seedOptions.numRun + File.separator , rh, System.currentTimeMillis(), execConfig.getAlgorithmCutoffTime()));
		eventManager.registerHandler(IncumbentPerformanceChangeEvent.class, new ParamConfigurationIncumbentChangerOriginTracker(configTracker, rh, execConfig.getAlgorithmCutoffTime()));
		
		
		
		switch(options.execMode)
		{
			case ROAR:
				smac = new AbstractAlgorithmFramework(options,instances,algoEval,sf, configSpace, instanceSeedGen, initialIncumbent, eventManager, rh, pool, runGroupName, termCond, configTracker);
				break;
			case SMAC:
				smac = new SequentialModelBasedAlgorithmConfiguration(options, instances, algoEval, options.expFunc.getFunction(),sf, configSpace, instanceSeedGen,  initialIncumbent, eventManager, rh, pool, runGroupName, termCond, configTracker);
				break;
			default:
				throw new IllegalArgumentException("Execution Mode Specified is not supported");
		}
		
		if(options.stateOpts.restoreIteration != null)
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
			
			if(options.stateOpts.restoreIteration < 0)
			{
				throw new ParameterException("Iteration must be a non-negative integer");
			}
			
			StateDeserializer sd = sf.getStateDeserializer("it", options.stateOpts.restoreIteration, configSpace, instances, execConfig, rh);
			
			smac.restoreState(sd);
			
			
			
		}
		
}
