package ca.ubc.cs.beta.smac.executors;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import au.com.bytecode.opencsv.CSVWriter;
import ca.ubc.cs.beta.ac.config.ProblemInstance;
import ca.ubc.cs.beta.ac.config.ProblemInstanceSeedPair;
import ca.ubc.cs.beta.ac.config.RunConfig;
import ca.ubc.cs.beta.config.AlgorithmExecutionConfig;
import ca.ubc.cs.beta.config.JCommanderHelper;
import ca.ubc.cs.beta.config.SMACConfig;
import ca.ubc.cs.beta.config.ValidationRoundingMode;
import ca.ubc.cs.beta.configspace.ParamConfiguration;
import ca.ubc.cs.beta.configspace.ParamConfigurationSpace;
import ca.ubc.cs.beta.configspace.ParamFileHelper;
import ca.ubc.cs.beta.probleminstance.InstanceListWithSeeds;
import ca.ubc.cs.beta.probleminstance.InstanceSeedGenerator;
import ca.ubc.cs.beta.probleminstance.ProblemInstanceHelper;
import ca.ubc.cs.beta.smac.AbstractAlgorithmFramework;
import ca.ubc.cs.beta.smac.OverallObjective;
import ca.ubc.cs.beta.smac.RunObjective;
import ca.ubc.cs.beta.smac.RunHashCodeVerifyingAlgorithmEvalutor;
import ca.ubc.cs.beta.smac.SequentialModelBasedAlgorithmConfiguration;
import ca.ubc.cs.beta.smac.ac.runners.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.smac.ac.runs.AlgorithmRun;
import ca.ubc.cs.beta.smac.model.builder.HashCodeVerifyingModelBuilder;
import ca.ubc.cs.beta.smac.state.StateDeserializer;
import ca.ubc.cs.beta.smac.state.StateFactory;
import ca.ubc.cs.beta.smac.state.legacy.LegacyStateFactory;
import ca.ubc.cs.beta.smac.state.nullFactory.NullStateFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public class AutomaticConfigurator 
{

	private static List<ProblemInstance> instances;
	private static List<ProblemInstance> testInstances;
	private static Logger logger;
	private static Marker exception;
	private static Marker stackTrace;
	private static InstanceSeedGenerator instanceSeedGen;
	private static InstanceSeedGenerator testInstanceSeedGen;
	public static void main(String[] args)
	{
		/*
		 * WARNING: DO NOT LOG ANYTHING UNTIL AFTER WE HAVE PARSED THE CLI OPTIONS
		 * AS THE CLI OPTIONS USE A TRICK TO ALLOW LOGGING TO BE CONFIGURABLE ON THE CLI
		 * IF YOU LOG PRIOR TO IT ACTIVATING, IT WILL BE IGNORED 
		 */
		try {
			SMACConfig config = parseCLIOptions(args);
			
			
			logger.info("Automatic Configuration Started");
			
			logger.info(config.toString());
			
			
			
			/*
			 * Build the Serializer object used in the model 
			 */
			StateFactory restoreSF;
			switch(config.statedeSerializer)
			{
				case NULL:
					restoreSF = new NullStateFactory();
					break;
				case LEGACY:
					restoreSF = new LegacyStateFactory(config.scenarioConfig.outputDirectory + File.separator + config.runID + File.separator + "state" + File.separator, config.restoreStateFrom);
					break;
				default:
					throw new IllegalArgumentException("State Serializer specified is not supported");
			}
			
			logger.info("Parsing Parameter Space File", config.paramFile);
			ParamConfigurationSpace configSpace = null;
			
			
			String[] possiblePaths = { config.paramFile, config.experimentDir + File.separator + config.paramFile, config.scenarioConfig.algoExecConfig.algoExecDir + File.separator + config.paramFile }; 
			for(String path : possiblePaths)
			{
				try {
					logger.debug("Trying param file in path {} ", path);
					configSpace = ParamFileHelper.getParamFileParser(path);
					break;
				} catch(IllegalStateException e)
				{ 

					
				
				}
			}
			
			
			if(configSpace == null)
			{
				throw new ParameterException("Could not find param file");
			}
			
			AlgorithmExecutionConfig execConfig = new AlgorithmExecutionConfig(config.scenarioConfig.algoExecConfig.algoExec, config.scenarioConfig.algoExecConfig.algoExecDir, configSpace, false);
		
			
			
			TargetAlgorithmEvaluator algoEval;
			boolean concurrentRuns = (config.maxConcurrentAlgoExecs > 1);
			if(config.runHashCodeFile != null)
			{
				logger.info("Algorithm Execution will verify run Hash Codes");
				Queue<Integer> runHashCodes = parseRunHashCodes(config.runHashCodeFile);
				algoEval = new RunHashCodeVerifyingAlgorithmEvalutor(execConfig, runHashCodes, concurrentRuns);
				 
			} else
			{
				logger.info("Algorithm Execution will NOT verify run Hash Codes");
				//TODO Seperate the generation of Run Hash Codes from verifying them
				algoEval = new RunHashCodeVerifyingAlgorithmEvalutor(execConfig, concurrentRuns);
			}

			if(config.modelHashCodeFile != null)
			{
				logger.info("Algorithm Execution will verify model Hash Codes");
				parseModelHashCodes(config.runHashCodeFile);
				
			
			}
			
			StateFactory sf;
			
			switch(config.stateSerializer)
			{
				case NULL:
					sf = new NullStateFactory();
					break;
				case LEGACY:
					sf = new LegacyStateFactory(config.scenarioConfig.outputDirectory + File.separator + config.runID + File.separator + "state" + File.separator, config.restoreStateFrom);
					break;
				default:
					throw new IllegalArgumentException("State Serializer specified is not supported");
			}
			
			
			
			AbstractAlgorithmFramework smac;
			switch(config.execMode)
			{
				case ROAR:
					smac = new AbstractAlgorithmFramework(config,instances, testInstances,algoEval,sf, configSpace);
					break;
				case SMAC:
					smac = new SequentialModelBasedAlgorithmConfiguration(config, instances, testInstances, algoEval, config.expFunc.getFunction(),sf, configSpace);
					break;
				default:
					throw new IllegalArgumentException("Execution Mode Specified is not supported");
			}
			
			if(config.restoreIteration != null)
			{
				restoreState(config, restoreSF, smac, configSpace,config.scenarioConfig.overallObj,config.scenarioConfig.runObj, instances, execConfig);
			}
			
				
			smac.run();
			
			TargetAlgorithmEvaluator validatingTae = new TargetAlgorithmEvaluator(execConfig, concurrentRuns);
			
			validate(smac.getIncumbent(),config, testInstanceSeedGen, validatingTae);
			
			logger.info("SMAC Completed Successfully");
			
			
			return;
		} catch(Throwable t)
		{
			
				if(logger != null)
				{
					
					logger.error(exception, "Message: {}",t.getMessage());

					if(!(t instanceof ParameterException))
					{
						logger.error(exception, "Exception:{}", t.getClass().getCanonicalName());
						StringWriter sWriter = new StringWriter();
						PrintWriter writer = new PrintWriter(sWriter);
						t.printStackTrace(writer);
						logger.error(stackTrace, "StackTrace:{}",sWriter.toString());
					}
					
						
					
					
					
					logger.info("Exiting Application with failure");
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
		
		
	}
	



	private static void validate(ParamConfiguration incumbent, SMACConfig config, InstanceSeedGenerator testInstGen, TargetAlgorithmEvaluator validatingTae) {
		
		long testInstancesCount = Math.min(config.numberOfTestInstances, testInstances.size());
		int testSeedsPerInstance = config.numberOfTestSeedsPerInstance;
		long validationRunsCount = config.numberOfValidationRuns;
		
		ValidationRoundingMode mode = config.validationRoundingMode;		
		
		List<RunConfig> validationRuns = new ArrayList<RunConfig>();
endloop:		
		for(int i=0; i < testSeedsPerInstance; i++)
		{

			switch(mode)
			{
				case DOWN:
					if(validationRuns.size() > 0)
					{
						/**
						 * Note the sign difference (rounding down is > and rounding up is >=)
						 * 
						 * If we have 10 instances and 10 seeds
						 * 
						 * At 90 we do the following
						 *      Target:   100       99
						 * UP             more     more
						 * 
						 * DOWN           more     stop
						 * 
						 */
						 if( (validationRuns.size() + testInstancesCount) > validationRunsCount)
						 {							 
							 break endloop;
						 }
					} else
					{
						logger.warn("Rounding down would mean 0 runs, scheduling at least one set of runs per incumbent" );
					}
					break;
				case UP:
					if( (validationRuns.size()) >= validationRunsCount)
					 {							 
						 break endloop;
					 }
					break;
				default:
					throw new IllegalStateException("Unknown Validation Mode");
			}
			for(int j=0; j< testInstancesCount; j++)
			{
				ProblemInstance pi = testInstances.get(j);
				if(testInstGen.hasNextSeed(pi))
				{
					long seed = testInstGen.getNextSeed(pi);
					validationRuns.add(new RunConfig(new ProblemInstanceSeedPair(pi,seed), config.scenarioConfig.cutoffTime, incumbent));
				} else
				{
					logger.warn("Not enough seeds for instance {} skipping run ", pi);
				}
				
			}
			
		}
		logger.info("Scheduling {} validation runs", validationRuns.size());
		List<AlgorithmRun> runs = validatingTae.evaluateRun(validationRuns);
		
		
		try
		{
			writeInstanceRawResultsFile(runs, config);
		} catch(IOException e)
		{
			logger.error("Could not write results file", e);
		}
		
		
		try
		{
			writeInstanceSeedResultFile(runs, config);
		} catch(IOException e)
		{
			logger.error("Could not write results file", e);
		}
		
		try
		{
			writeInstanceResultFile(runs, config);
		} catch(IOException e)
		{
			logger.error("Could not write results file:", e);
		}
		
		//writeInstanceResultFile(runs, config);
		
		
	}




	private static void writeInstanceResultFile(List<AlgorithmRun> runs,SMACConfig smacConfig) throws IOException 
	{
		Map<ProblemInstance, List<AlgorithmRun>> map = new LinkedHashMap<ProblemInstance,List<AlgorithmRun>>();
		
		File f = new File(smacConfig.scenarioConfig.outputDirectory + File.separator + smacConfig.runID +  File.separator + "validationResultsMatrix.csv");
		logger.info("Instance Validation Matrix Result Written to: {}", f.getAbsolutePath());
		CSVWriter writer = new CSVWriter(new FileWriter(f));
		
		
		
		
		int maxRunLength =0;
		for(AlgorithmRun run : runs)
		{
			ProblemInstance pi = run.getInstanceRunConfig().getAlgorithmInstanceSeedPair().getInstance();
			if(map.get(pi) == null)
			{
				map.put(pi, new ArrayList<AlgorithmRun>());
			}
			
			List<AlgorithmRun> myRuns = map.get(pi);
			
			
			myRuns.add(run);
			
			maxRunLength = Math.max(myRuns.size(), maxRunLength);
		}
		
		
		if(!smacConfig.noValidationHeaders)
		{
			ArrayList<String> headerRow = new ArrayList<String>();
			headerRow.add("Instance");
			headerRow.add("OverallObjective");
			
			for(int i=1; i <= maxRunLength; i++ )
			{
				headerRow.add("Run #" + i);
			}
			
			
			writer.writeNext(headerRow.toArray(new String[0]));
		}
		
		
		List<Double> overallObjectives = new ArrayList<Double>();
		OverallObjective overallObj = smacConfig.scenarioConfig.overallObj;
		
		for(Entry<ProblemInstance, List<AlgorithmRun>> piRuns : map.entrySet())
		{
			List<String> outputLine = new ArrayList<String>();
			outputLine.add(piRuns.getKey().getInstanceName());
			List<AlgorithmRun> myRuns = piRuns.getValue();
			
			RunObjective runObj = smacConfig.scenarioConfig.runObj;

			List<Double> results = new ArrayList<Double>(myRuns.size());
			
			for(int i=0; i < myRuns.size(); i++)
			{
				results.add(runObj.getObjective(myRuns.get(i)));
			}
			
			double overallResult = overallObj.aggregate(results, smacConfig.scenarioConfig.cutoffTime);
			outputLine.add(String.valueOf(overallResult));
			
			overallObjectives.add(overallResult);
			for(AlgorithmRun run : piRuns.getValue())
			{
				outputLine.add(String.valueOf(smacConfig.scenarioConfig.runObj.getObjective(run)));
			}
			
			
			writer.writeNext(outputLine.toArray(new String[0]));
			
		}
		
		
		String[] args = { "Overall Objective On Test Set", String.valueOf(overallObj.aggregate(overallObjectives, smacConfig.scenarioConfig.cutoffTime))};
		writer.writeNext(args);
		
		writer.close();
		
	}




	private static void writeInstanceSeedResultFile(List<AlgorithmRun> runs,SMACConfig smacConfig) throws IOException
	{
		
		File f = new File(smacConfig.scenarioConfig.outputDirectory + File.separator + smacConfig.runID +  File.separator + "validationResultsList.csv");
		logger.info("Instance Seed Result File Written to: {}", f.getAbsolutePath());
		CSVWriter writer = new CSVWriter(new FileWriter(f));
		
		
		if(!smacConfig.noValidationHeaders)
		{
			String[] args = {"Seed","Instance","Response"};
			writer.writeNext(args);
		}
		
		for(AlgorithmRun run : runs)
		{
			
			String[] args = { String.valueOf(run.getInstanceRunConfig().getAlgorithmInstanceSeedPair().getSeed()),run.getInstanceRunConfig().getAlgorithmInstanceSeedPair().getInstance().getInstanceName(), String.valueOf(smacConfig.scenarioConfig.runObj.getObjective(run)) };
			writer.writeNext(args);
		}
		
		writer.close();
		
	}




	private static void writeInstanceRawResultsFile(List<AlgorithmRun> runs,SMACConfig smacConfig) throws IOException
	{
		
		File f = new File(smacConfig.scenarioConfig.outputDirectory + File.separator + smacConfig.runID +  File.separator + "RawValidationExecutionResults.csv");
		logger.info("Instance Seed Result File Written to: {}", f.getAbsolutePath());
		CSVWriter writer = new CSVWriter(new FileWriter(f));
		
		
		if(!smacConfig.noValidationHeaders)
		{
			String[] args = {"Seed","Instance","Raw Result Line", "Result Line"};
			writer.writeNext(args);
		}
		
		for(AlgorithmRun run : runs)
		{
			
			String[] args = { String.valueOf(run.getInstanceRunConfig().getAlgorithmInstanceSeedPair().getSeed()),run.getInstanceRunConfig().getAlgorithmInstanceSeedPair().getInstance().getInstanceName(), run.rawResultLine(), run.getResultLine() };
			writer.writeNext(args);
		}
		
		writer.close();
		
	}


	

	private static void restoreState(SMACConfig config, StateFactory sf, AbstractAlgorithmFramework smac,  ParamConfigurationSpace configSpace, OverallObjective overallObj, RunObjective runObj, List<ProblemInstance> instances, AlgorithmExecutionConfig execConfig) {
		
		if(config.restoreIteration < 0)
		{
			throw new ParameterException("Iteration must be a non-negative integer");
		}
		
		StateDeserializer sd = sf.getStateDeserializer("it", config.restoreIteration, configSpace, overallObj, runObj, instances, execConfig);
		
		smac.restoreState(sd);
		
		
		
	}




	/**
	 * Parsers Command Line Arguments and returns a config object
	 * @param args
	 * @return
	 */
	private static SMACConfig parseCLIOptions(String[] args) throws ParameterException, IOException
	{
		//DO NOT LOG UNTIL AFTER WE PARSE CONFIG OBJECT
		SMACConfig config = new SMACConfig();
		JCommander com = new JCommander(config);
		com.setProgramName("smac");
		try {
			
			
			JCommanderHelper.parse(com, args);
			//com.parse(args);
			
			File outputDir = new File(config.scenarioConfig.outputDirectory);
			if(!outputDir.exists())
			{
				outputDir.mkdir();
			}
			
			System.setProperty("OUTPUTDIR", config.scenarioConfig.outputDirectory);
			System.setProperty("RUNID", config.runID);
			
			logger = LoggerFactory.getLogger(AutomaticConfigurator.class);
			exception = MarkerFactory.getMarker("EXCEPTION");
			stackTrace = MarkerFactory.getMarker("STACKTRACE");
			
			
			
			logger.trace("Command Line Options Parsed");
			logger.info("Parsing instances from {}", config.scenarioConfig.instanceFile );
			InstanceListWithSeeds ilws;
			ilws = ProblemInstanceHelper.getInstances(config.scenarioConfig.instanceFile,config.experimentDir, config.scenarioConfig.instanceFeatureFile, !config.scenarioConfig.skipInstanceFileCheck);
			instanceSeedGen = ilws.getSeedGen();
			instances = ilws.getInstances();
			
			
			logger.info("Parsing test instances from {}", config.scenarioConfig.instanceFile );
			ilws = ProblemInstanceHelper.getInstances(config.scenarioConfig.testInstanceFile, config.experimentDir, !config.scenarioConfig.skipInstanceFileCheck);
			testInstances = ilws.getInstances();
			testInstanceSeedGen = ilws.getSeedGen();
			
			return config;
		} catch(IOException e)
		{
			com.usage();
			throw e;
			
		} catch(ParameterException e)
		{
			com.usage();
			throw e;
		}
	}
	

	
	
	
	private static Pattern runHashCodePattern = Pattern.compile("^Run Hash Codes:\\d+( After \\d+ runs)?\\z");
	
	private static Pattern modelHashCodePattern = Pattern.compile("^(Preprocessed|Random) Forest Built with Hash Code:\\s*\\d+?\\z");
	
	
	private static void parseModelHashCodes(File modelHashCodeFile) {
		logger.info("Model Hash Code File Passed {}", modelHashCodeFile.getAbsolutePath());
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
						logger.debug("Found Model Hash Code #{} on line #{} with contents:{}", array);
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
						logger.trace("No Hash Code found on line: {}", line );
					}
					lineCount++;
				}
				if(hashCodeCount == 0)
				{
					logger.warn("Hash Code File Specified, but we found no hash codes");
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
	
	
	private static Queue<Integer> parseRunHashCodes(File runHashCodeFile) 
	{
		logger.info("Run Hash Code File Passed {}", runHashCodeFile.getAbsolutePath());
		Queue<Integer> runHashCodeQueue = new LinkedList<Integer>();
		BufferedReader bin = null;
		try {
			try{
				bin = new BufferedReader(new FileReader(runHashCodeFile));
			
				String line;
				int hashCodeCount=0;
				int lineCount = 1;
				while((line = bin.readLine()) != null)
				{
					
					Matcher m = runHashCodePattern.matcher(line);
					if(m.find())
					{
						Object[] array = { ++hashCodeCount, lineCount, line};
						logger.debug("Found Run Hash Code #{} on line #{} with contents:{}", array);
						int colonIndex = line.indexOf(":");
						int spaceIndex = line.indexOf(" ", colonIndex);
						String lineSubStr = line.substring(colonIndex+1,spaceIndex);
						runHashCodeQueue.add(Integer.valueOf(lineSubStr));
						
					} else
					{
						logger.trace("No Hash Code found on line: {}", line );
					}
					lineCount++;
				}
				if(hashCodeCount == 0)
				{
					logger.warn("Hash Code File Specified, but we found no hash codes");
				}
			
			} finally
			{
				if(bin != null) bin.close();
			}
			
		} catch(IOException e)
		{
			throw new RuntimeException(e);
		}
		
		
		return runHashCodeQueue;
		
	}

	
}
