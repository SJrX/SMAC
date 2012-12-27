package ca.ubc.cs.beta.smac.validation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.SortedMap;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.com.bytecode.opencsv.CSVWriter;
import ca.ubc.cs.beta.aclib.algorithmrun.AlgorithmRun;
import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration;
import ca.ubc.cs.beta.aclib.objectives.OverallObjective;
import ca.ubc.cs.beta.aclib.objectives.RunObjective;
import ca.ubc.cs.beta.aclib.options.ValidationOptions;
import ca.ubc.cs.beta.aclib.options.ValidationRoundingMode;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstanceSeedPair;
import ca.ubc.cs.beta.aclib.runconfig.RunConfig;
import ca.ubc.cs.beta.aclib.seedgenerator.InstanceSeedGenerator;
import ca.ubc.cs.beta.aclib.seedgenerator.RandomInstanceSeedGenerator;
import ca.ubc.cs.beta.aclib.seedgenerator.SetInstanceSeedGenerator;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.deferred.TAECallback;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.deferred.WaitableTAECallback;
import ca.ubc.cs.beta.aclib.trajectoryfile.TrajectoryFileEntry;



public class Validator {

	
	private static Logger log = LoggerFactory.getLogger(Validator.class);
	/*
	public double validate(List<ProblemInstance> testInstances, ValidationOptions config,double cutoffTime,InstanceSeedGenerator testInstGen, TargetAlgorithmEvaluator validatingTae, String outputDir,RunObjective runObj,OverallObjective intraInstanceObjective, OverallObjective interInstanceObjective, TrajectoryFileEntry) {
		
		return validate(testInstances, incumbent, config, cutoffTime, testInstGen, validatingTae, outputDir, runObj, intraInstanceObjective, interInstanceObjective,tunerTime, 0,0, numRun);
	}
		*/
		
	
public SortedMap<TrajectoryFileEntry, Double>  validate(List<ProblemInstance> testInstances, final ValidationOptions options,final double cutoffTime,InstanceSeedGenerator testInstGen, TargetAlgorithmEvaluator validatingTae, 
		final String outputDir,
		final RunObjective runObj,
		final OverallObjective intraInstanceObjective, final OverallObjective interInstanceObjective,  List<TrajectoryFileEntry> tfes, final long numRun, boolean waitForRuns) 
		{

		int testInstancesCount = Math.min(options.numberOfTestInstances, testInstances.size());
		int testSeedsPerInstance = options.numberOfTestSeedsPerInstance;
		int validationRunsCount = options.numberOfValidationRuns;
		
		ValidationRoundingMode mode = options.validationRoundingMode;		
		
		List<ProblemInstanceSeedPair> pisps = new ArrayList<ProblemInstanceSeedPair>();
	
		if(testInstGen instanceof SetInstanceSeedGenerator)
		{
			if(validationRunsCount > testInstGen.getInitialInstanceSeedCount())
			{
				log.info("Clamping number of validation runs from {} to {} due to seed limit", validationRunsCount, testInstGen.getInitialInstanceSeedCount());
				validationRunsCount = testInstGen.getInitialInstanceSeedCount();
			}
			pisps = getValidationRuns(testInstances, (SetInstanceSeedGenerator) testInstGen, validationRunsCount);
		} else if(testInstGen instanceof RandomInstanceSeedGenerator)
		{
			pisps = getValidationRuns(testInstances, (RandomInstanceSeedGenerator) testInstGen,mode, validationRunsCount, testSeedsPerInstance, testInstancesCount);
		} else
		{
			throw new IllegalStateException("Unknown Instance Seed Generator specified");
		}
		
		log.info("Scheduling {} validation runs per incumbent", pisps.size());
		
		
		Set<TrajectoryFileEntry> tfesToUse = new TreeSet<TrajectoryFileEntry>();
		
		if(options.validateOnlyLastIncumbent)
		{
			
			TrajectoryFileEntry tfe = tfes.get(tfes.size() - 1);
			
			double tunerTime = (tfe.getTunerTime() > options.maxTimestamp) ? options.maxTimestamp : tfe.getTunerTime();
			
			TrajectoryFileEntry newTfe = new TrajectoryFileEntry(tfe.getConfiguration(), tunerTime, tfe.getEmpericalPerformance(), tfe.getACOverhead());
			
			tfesToUse.add(newTfe);
			
		} else
		{
			ConcurrentSkipListMap<Double,TrajectoryFileEntry> skipList = new ConcurrentSkipListMap<Double, TrajectoryFileEntry>();
			
			for(TrajectoryFileEntry tfe : tfes)
			{
				skipList.put(tfe.getTunerTime(), tfe);
			}
			if(options.maxTimestamp == -1)
			{
				options.maxTimestamp = skipList.floorKey(Double.MAX_VALUE);
			}
		
			for(double x = options.maxTimestamp; x > options.minTimestamp ; x /= options.multFactor)
			{
				TrajectoryFileEntry tfe = skipList.floorEntry(x).getValue();
				tfesToUse.add(new TrajectoryFileEntry(tfe.getConfiguration(), x, tfe.getEmpericalPerformance(), tfe.getACOverhead()));
				
				//If minTimestamp is zero, then we would never get there, but we will stop after 0.25
				//We don't put this in the loop condition, because we do always want atleast one run
				if( x  < 0.25) break; 
				
			}
		}
		
		final List<TrajectoryFileEntry> tfesToRun = new ArrayList<TrajectoryFileEntry>(tfesToUse.size());
		tfesToRun.addAll(tfesToUse);
		
		
		List<RunConfig> runConfigs = getRunConfigs(tfesToRun, pisps, cutoffTime);
		
		
		log.info("Validation needs {} algorithm runs  to validate {} trajectory file entries ", runConfigs.size(), tfesToUse.size());
		//List<AlgorithmRun> runs = validatingTae.evaluateRun(runConfigs);
		
		final AtomicReference<RuntimeException> exception = new AtomicReference<RuntimeException>();
		
		final SortedMap<TrajectoryFileEntry, Double> finalPerformance = new ConcurrentSkipListMap<TrajectoryFileEntry, Double>();
		
		WaitableTAECallback callback = new WaitableTAECallback(new TAECallback()
		{

			@Override
			public void onSuccess(List<AlgorithmRun> runs) {
				// TODO Auto-generated method stub
				try
				{
					writeInstanceRawResultsFile(runs, options, outputDir, numRun);
				} catch(IOException e)
				{
					log.error("Could not write results file", e);
				}
				
				
				try
				{
					writeInstanceSeedResultFile(runs, options, outputDir, runObj, numRun);
				} catch(IOException e)
				{
					log.error("Could not write results file", e);
				}
				
				
				try
				{
					Map<ParamConfiguration, Double> testSetPerformance = writeInstanceResultFile(runs, options, outputDir, cutoffTime, runObj, intraInstanceObjective, interInstanceObjective, numRun);
					
					
					for(TrajectoryFileEntry tfe : tfesToRun)
					{
						finalPerformance.put(tfe, testSetPerformance.get(tfe.getConfiguration()));
					}

					appendInstanceResultFile(outputDir, finalPerformance,  numRun,options);
					
					
				} catch(IOException e)
				{
					log.error("Could not write results file:", e);
				}
				
			}

			@Override
			public void onFailure(RuntimeException t) {
				exception.set(t);
			}
			
		});

		
		validatingTae.evaluateRunsAsync(runConfigs, callback);
		
		
		if(!validatingTae.areRunsPersisteted() || waitForRuns)
		{
			log.info("Waiting until completion");
			callback.waitForCompletion();
		}
		
	
		if(exception.get() != null)
		{
			throw exception.get();
		}
		
		return finalPerformance;

		
		
		
		
	}






private List<RunConfig> getRunConfigs(List<TrajectoryFileEntry> tfes, List<ProblemInstanceSeedPair> pisps, double cutoffTime) 
{
	
	Set<ParamConfiguration> configs = new HashSet<ParamConfiguration>();
	for(TrajectoryFileEntry tfe : tfes)
	{
		configs.add(tfe.getConfiguration());
	}
	
	
	
	List<RunConfig> runConfigs  = new ArrayList<RunConfig>(pisps.size()*tfes.size());
	for(ParamConfiguration config: configs)
	{
		for(ProblemInstanceSeedPair pisp : pisps)
		{
			runConfigs.add(new RunConfig(pisp, cutoffTime,config));
		}
	}
	
	return runConfigs;
}






	private static List<ProblemInstanceSeedPair> getValidationRuns(List<ProblemInstance> pis,
			RandomInstanceSeedGenerator testInstGen, ValidationRoundingMode mode,
			int validationRunsCount, int testSeedsPerInstance,
			int testInstancesCount) {
		
		
		
		
		
		int numRuns = 0;
		
		switch(mode)
		{
		case UP:			
			numRuns = Math.round( (float) (Math.ceil(validationRunsCount / (float) testInstancesCount) * testInstancesCount));
			break;
		case NONE:
			numRuns = Math.min(validationRunsCount, testSeedsPerInstance*testInstancesCount);
			break;
		default:
			throw new IllegalStateException("Unknown Rounding Mode");
		}
		
		
		List<ProblemInstance> pisToUse = testInstGen.getProblemInstanceOrder(pis);
		int runsScheduled = 0;
		List<ProblemInstanceSeedPair> pisps = new ArrayList<ProblemInstanceSeedPair>(numRuns);
		
		log.info("Creating Runs ");
endloop:
		while(true)
		{
			for(int i=0; i < testInstancesCount; i++)
			{
				ProblemInstance pi = pisToUse.get(i);
				pisps.add(new ProblemInstanceSeedPair(pi,testInstGen.getNextSeed(pi)));
				
				runsScheduled++;
				
				if(runsScheduled >= numRuns) break endloop;
			}
		}
		
		

		return pisps;
	}




	private static List<ProblemInstanceSeedPair> getValidationRuns( List<ProblemInstance> pis,
			SetInstanceSeedGenerator testInstGen, int validationRunsCount) {

		List<ProblemInstance> instances = testInstGen.getProblemInstanceOrder(pis);
		int numRuns = Math.min(instances.size(), validationRunsCount);
		List<ProblemInstanceSeedPair> runs = new ArrayList<ProblemInstanceSeedPair>(numRuns);
		for( int i=0; i < numRuns; i++)
		{
			ProblemInstance pi = instances.get(i);
			runs.add(new ProblemInstanceSeedPair(pi,testInstGen.getNextSeed(pi)));
		}
		
		
		
		return runs;
	}




	/**
	 * Writes a CSV File which has the matrix of runs 
	 * @param runs
	 * @param validationOptions
	 * @param outputDir
	 * @param cutoffTime
	 * @param runObj
	 * @param overallObj
	 * @return - Overall objective over test set (For convinence)
	 * @throws IOException
	 */
	private static Map<ParamConfiguration, Double> writeInstanceResultFile(List<AlgorithmRun> runs,ValidationOptions validationOptions, String outputDir, double cutoffTime,  RunObjective runObj, OverallObjective intraInstanceObjective, OverallObjective interInstanceObjective, long numRun) throws IOException 
	{
		
		
		String suffix = (validationOptions.outputFileSuffix.trim().equals("")) ? "" : "-" + validationOptions.outputFileSuffix.trim();
		File f = new File(outputDir +  File.separator + "validationResultsMatrix"+suffix+"-run" + numRun + ".csv");
		log.info("Instance Validation Matrix Result Written to: {}", f.getAbsolutePath());
		
		CSVWriter writer = new CSVWriter(new FileWriter(f));
		
		Map<ParamConfiguration, Double> calculatedOverallObjectives = new LinkedHashMap<ParamConfiguration, Double>();
	
		
		Map<ParamConfiguration, List<AlgorithmRun>> configToRunMap = new LinkedHashMap<ParamConfiguration, List<AlgorithmRun>>();
		
		for(AlgorithmRun run : runs)
		{
			ParamConfiguration config = run.getRunConfig().getParamConfiguration();
			if(configToRunMap.get(config) == null)
			{
				configToRunMap.put(config, new ArrayList<AlgorithmRun>(1000));
			}
			configToRunMap.get(config).add(run);
			
		}
		
		for(Entry<ParamConfiguration, List<AlgorithmRun>> ent : configToRunMap.entrySet())
		{
			Map<ProblemInstance, List<AlgorithmRun>> map = new LinkedHashMap<ProblemInstance,List<AlgorithmRun>>();
			
			runs = ent.getValue();
			
			
			int maxRunLength =0;
			for(AlgorithmRun run : runs)
			{
				ProblemInstance pi = run.getRunConfig().getProblemInstanceSeedPair().getInstance();
				if(map.get(pi) == null)
				{
					map.put(pi, new ArrayList<AlgorithmRun>());
				}
				
				List<AlgorithmRun> myRuns = map.get(pi);
				
				
				myRuns.add(run);
				
				maxRunLength = Math.max(myRuns.size(), maxRunLength);
			}
			
		
			ArrayList<String> headerRow = new ArrayList<String>();
			headerRow.add("Config " + ent.getKey().toString());
			headerRow.add("Instance");
			headerRow.add("OverallObjective");
			
			for(int i=1; i <= maxRunLength; i++ )
			{
				headerRow.add("Run #" + i);
			}
			
			
			writer.writeNext(headerRow.toArray(new String[0]));
		
			
			
			List<Double> overallObjectives = new ArrayList<Double>();
			
			
			for(Entry<ProblemInstance, List<AlgorithmRun>> piRuns : map.entrySet())
			{
				List<String> outputLine = new ArrayList<String>();
				outputLine.add("");
				outputLine.add(piRuns.getKey().getInstanceName());
				List<AlgorithmRun> myRuns = piRuns.getValue();
				
				
	
				List<Double> results = new ArrayList<Double>(myRuns.size());
				
				for(int i=0; i < myRuns.size(); i++)
				{
					results.add(runObj.getObjective(myRuns.get(i)));
				}
				
				double overallResult = intraInstanceObjective.aggregate(results, cutoffTime);
				outputLine.add(String.valueOf(overallResult));
				
				overallObjectives.add(overallResult);
				for(AlgorithmRun run : piRuns.getValue())
				{
					outputLine.add(String.valueOf(runObj.getObjective(run)));
				}
				
				
				writer.writeNext(outputLine.toArray(new String[0]));
				
			}
			
			
			double overallObjective = interInstanceObjective.aggregate(overallObjectives,cutoffTime);
			String[] args = { "", "Overall Objective On Test Set", String.valueOf(overallObjective)};
			writer.writeNext(args);
			
			calculatedOverallObjectives.put(ent.getKey(), overallObjective);
			
		}
		
			writer.close();
			
			return calculatedOverallObjectives;
	}




	private static void writeInstanceSeedResultFile(List<AlgorithmRun> runs,ValidationOptions validationOptions, String outputDir, RunObjective runObj, long numRun) throws IOException
	{
		
		String suffix = (validationOptions.outputFileSuffix.trim().equals("")) ? "" : "-" + validationOptions.outputFileSuffix.trim();
		File f = new File(outputDir + "validationInstanceSeedResult"+suffix+"-run" + numRun + ".csv");
		
		log.info("Instance Seed Result File Written to: {}", f.getAbsolutePath());
		CSVWriter writer = new CSVWriter(new FileWriter(f));
		
		
		if(validationOptions.validationHeaders)
		{
			String[] args = {"Configuration","Seed","Instance","Response"};
			writer.writeNext(args);
		}
		
		for(AlgorithmRun run : runs)
		{
			
			String[] args = { run.getRunConfig().getParamConfiguration().toString(), String.valueOf(run.getRunConfig().getProblemInstanceSeedPair().getSeed()),run.getRunConfig().getProblemInstanceSeedPair().getInstance().getInstanceName(), String.valueOf(runObj.getObjective(run)) };
			writer.writeNext(args);
		}
		
		writer.close();
		
	}




	private static void writeInstanceRawResultsFile(List<AlgorithmRun> runs,ValidationOptions validationOptions, String outputDir, long numRun) throws IOException
	{
		String suffix = (validationOptions.outputFileSuffix.trim().equals("")) ? "" : "-" + validationOptions.outputFileSuffix.trim();
		File f = new File(outputDir + "rawValidationExecutionResults"+suffix+"-run" + numRun + ".csv");
		log.info("Instance Seed Result File Written to: {}", f.getAbsolutePath());
		CSVWriter writer = new CSVWriter(new FileWriter(f));
		
		
		if(validationOptions.validationHeaders)
		{
			String[] args = {"Configuration", "Seed", "Instance","Raw Result Line", "Result Line"};
			writer.writeNext(args);
		}
		
		for(AlgorithmRun run : runs)
		{
			
			String[] args = { run.getRunConfig().getParamConfiguration().toString(), String.valueOf(run.getRunConfig().getProblemInstanceSeedPair().getSeed()),run.getRunConfig().getProblemInstanceSeedPair().getInstance().getInstanceName(), run.rawResultLine(), run.getResultLine() };
			writer.writeNext(args);
		}
		
		writer.close();
		
	}
	

	private void appendInstanceResultFile(String outputDir, Map<TrajectoryFileEntry, Double> finalPerformance, long numRun, ValidationOptions validationOptions) throws IOException {
		
		String suffix = (validationOptions.outputFileSuffix.trim().equals("")) ? "" : "-" + validationOptions.outputFileSuffix.trim();
		File f = new File(outputDir +  File.separator + "classicValidationResults"+suffix+"-run" + numRun + ".csv");
	
		if(!f.exists())
		{
			f.createNewFile();
			
		} else
		{
			f.delete();
			f.createNewFile();
		}
		
		
		StringBuilder sb = new StringBuilder();
		for(Entry<TrajectoryFileEntry, Double > ent : finalPerformance.entrySet())
		{
			double tunerTime = ent.getKey().getTunerTime();
			double empericalPerformance = ent.getKey().getEmpericalPerformance();
			double testSetPerformance = ent.getValue();
			double acOverhead = ent.getKey().getACOverhead();
			
			sb.append(tunerTime).append(",").append(empericalPerformance).append(",").append(testSetPerformance).append(",").append(acOverhead).append("\n");
		}
		if(!f.canWrite())
		{
			log.error("Could not write trajectory file would have written: {}" , sb.toString());
			
		
			
		} else
		{
		
		
			PrintWriter output = new PrintWriter(new FileOutputStream(f,true));
			output.append(sb);
			
			output.close();
		}
		
}

	


	
}
