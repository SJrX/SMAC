package ca.ubc.cs.beta.smac.validation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.SortedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.com.bytecode.opencsv.CSVWriter;
import ca.ubc.cs.beta.aclib.algorithmrun.AlgorithmRun;
import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration;
import ca.ubc.cs.beta.aclib.configspace.ParamConfiguration.StringFormat;
import ca.ubc.cs.beta.aclib.exceptions.DeveloperMadeABooBooException;
import ca.ubc.cs.beta.aclib.exceptions.DuplicateRunException;
import ca.ubc.cs.beta.aclib.execconfig.AlgorithmExecutionConfig;
import ca.ubc.cs.beta.aclib.objectives.OverallObjective;
import ca.ubc.cs.beta.aclib.objectives.RunObjective;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.aclib.probleminstance.ProblemInstanceSeedPair;
import ca.ubc.cs.beta.aclib.runconfig.RunConfig;
import ca.ubc.cs.beta.aclib.runhistory.NewRunHistory;
import ca.ubc.cs.beta.aclib.runhistory.RunHistory;
import ca.ubc.cs.beta.aclib.seedgenerator.InstanceSeedGenerator;
import ca.ubc.cs.beta.aclib.seedgenerator.RandomInstanceSeedGenerator;
import ca.ubc.cs.beta.aclib.seedgenerator.SetInstanceSeedGenerator;
import ca.ubc.cs.beta.aclib.smac.ValidationOptions;
import ca.ubc.cs.beta.aclib.smac.ValidationRoundingMode;
import ca.ubc.cs.beta.aclib.state.StateFactory;
import ca.ubc.cs.beta.aclib.state.StateSerializer;
import ca.ubc.cs.beta.aclib.state.legacy.LegacyStateFactory;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluatorCallback;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.WaitableTAECallback;
import ca.ubc.cs.beta.aclib.trajectoryfile.TrajectoryFileEntry;



public class Validator {

	
	private static Logger log = LoggerFactory.getLogger(Validator.class);
	/*
	public double validate(List<ProblemInstance> testInstances, ValidationOptions config,double cutoffTime,InstanceSeedGenerator testInstGen, TargetAlgorithmEvaluator validatingTae, String outputDir,RunObjective runObj,OverallObjective intraInstanceObjective, OverallObjective interInstanceObjective, TrajectoryFileEntry) {
		
		return validate(testInstances, incumbent, config, cutoffTime, testInstGen, validatingTae, outputDir, runObj, intraInstanceObjective, interInstanceObjective,tunerTime, 0,0, numRun);
	}
		*/
		
	
public SortedMap<TrajectoryFileEntry, Double>  validate(List<ProblemInstance> testInstances, final ValidationOptions options,final double cutoffTime,final InstanceSeedGenerator testInstGen,final TargetAlgorithmEvaluator validatingTae, 
		final String outputDir,
		final RunObjective runObj,
		final OverallObjective intraInstanceObjective, final OverallObjective interInstanceObjective,  final List<TrajectoryFileEntry> tfes, final long numRun, boolean waitForRuns, AlgorithmExecutionConfig execConfig,int cores) 
		{

		int testInstancesCount = Math.min(options.numberOfTestInstances, testInstances.size());
		int testSeedsPerInstance = options.numberOfTestSeedsPerInstance;
		int validationRunsCount = options.numberOfValidationRuns;
		
		ValidationRoundingMode mode = options.validationRoundingMode;		
		
		List<ProblemInstanceSeedPair> pisps = new ArrayList<ProblemInstanceSeedPair>();
		
		double maxWallTimeStamp = 0;
		double maxTunerTimeStamp = 0;
		for(TrajectoryFileEntry tfe : tfes)
		{
			
			maxWallTimeStamp = Math.max(tfe.getWallTime(), maxWallTimeStamp);
			maxTunerTimeStamp = Math.max(tfe.getTunerTime(),maxTunerTimeStamp);
		}
		
		if(options.validateOnlyIfWallTimeReached > maxWallTimeStamp)
		{
			log.info("Maximum walltime was {} but we required {} seconds to have passed validating ", maxWallTimeStamp, options.validateOnlyIfWallTimeReached );
			return  new TreeMap<TrajectoryFileEntry,Double>();
		}
		
		if(options.validateOnlyIfTunerTimeReached > maxTunerTimeStamp)
		{
			log.info("Maximum Tuner Time was {} but we required {} seconds to have passed before validating ", maxTunerTimeStamp, options.validateOnlyIfTunerTimeReached );
			return  new TreeMap<TrajectoryFileEntry,Double>();
		}
		
		
		if(options.useWallClockTime)
		{
			options.outputFileSuffix = "walltime" + options.outputFileSuffix;
		} else
		{
			options.outputFileSuffix = "tunertime" + options.outputFileSuffix;
		}
	
		if(testInstGen instanceof SetInstanceSeedGenerator)
		{
			if(validationRunsCount > testInstGen.getInitialInstanceSeedCount())
			{
				log.debug("Clamping number of validation runs from {} to {} due to seed limit", validationRunsCount, testInstGen.getInitialInstanceSeedCount());
				validationRunsCount = testInstGen.getInitialInstanceSeedCount();
			}
			pisps = getValidationRuns(testInstances, (SetInstanceSeedGenerator) testInstGen, mode, validationRunsCount);
		} else if(testInstGen instanceof RandomInstanceSeedGenerator)
		{
			pisps = getValidationRuns(testInstances, (RandomInstanceSeedGenerator) testInstGen,mode, validationRunsCount, testSeedsPerInstance, testInstancesCount);
		} else
		{
			throw new IllegalStateException("Unknown Instance Seed Generator specified");
		}
		
		
		
		
		Set<TrajectoryFileEntry> tfesToUse = new TreeSet<TrajectoryFileEntry>();
		
		if(options.validateAll)
		{
			tfesToUse.addAll(tfes);
		} else if(options.validateOnlyLastIncumbent)
		{
			
			log.trace("Validating only the last incumbent");
			if(options.useWallClockTime)
			{
				TrajectoryFileEntry tfe = tfes.get(tfes.size() - 1);
				
				
				double wallTime = (tfe.getWallTime() > options.maxTimestamp) ? options.maxTimestamp : tfe.getWallTime();
				
				TrajectoryFileEntry newTfe = new TrajectoryFileEntry(tfe.getConfiguration(), tfe.getTunerTime(), wallTime, tfe.getEmpericalPerformance(), tfe.getACOverhead());
				
				tfesToUse.add(newTfe);
			} else
			{
				TrajectoryFileEntry tfe = tfes.get(tfes.size() - 1);
				
				double tunerTime = (tfe.getTunerTime() > options.maxTimestamp) ? options.maxTimestamp : tfe.getTunerTime();
				
				TrajectoryFileEntry newTfe = new TrajectoryFileEntry(tfe.getConfiguration(), tunerTime, tfe.getWallTime(), tfe.getEmpericalPerformance(), tfe.getACOverhead());
				
				tfesToUse.add(newTfe);
			}
			
		}  else 
		{
			ConcurrentSkipListMap<Double,TrajectoryFileEntry> skipList = new ConcurrentSkipListMap<Double, TrajectoryFileEntry>();
			
			for(TrajectoryFileEntry tfe : tfes)
			{
				if(options.useWallClockTime)
				{
					skipList.put(tfe.getWallTime(), tfe);
				} else
				{
					skipList.put(tfe.getTunerTime(), tfe);
				}
			}
			if(options.maxTimestamp == -1)
			{
				options.maxTimestamp = skipList.floorKey(Double.MAX_VALUE);
			}
			ParamConfiguration lastConfig = null;
			Double lastPerformance = Double.MAX_VALUE;
			for(double x = options.maxTimestamp; x > options.minTimestamp ; x /= options.multFactor)
			{
				
				Entry<Double, TrajectoryFileEntry> tfeEntry = skipList.floorEntry(x);
				
				TrajectoryFileEntry tfe;
				
				if(tfeEntry != null) 
				{
					tfe = tfeEntry.getValue();
				} else
				{
					tfe = new TrajectoryFileEntry(lastConfig, 0,0,lastPerformance,0);
				}
				/*if(options.useWallClockTime && tfeEntry.getKey() < x/options.multFactor)
				{	export MYSQL_CREATE_TABLES = "false"
					continue;
				}*/
				
				
				if(options.useWallClockTime)
				{
					tfesToUse.add(new TrajectoryFileEntry(tfe.getConfiguration(), x, x, tfe.getEmpericalPerformance(), tfe.getACOverhead()));
				} else
				{
					tfesToUse.add(new TrajectoryFileEntry(tfe.getConfiguration(),x, tfe.getWallTime(), tfe.getEmpericalPerformance(), tfe.getACOverhead()));
					
				}
				lastConfig = tfe.getConfiguration();
				lastPerformance = tfe.getEmpericalPerformance();
				//If minTimestamp is zero, then we would never get there, but we will stop after 0.25
				//We don't put this in the loop condition, because we do always want atleast one run
				if( x  < 0.01) break; 
				
			}
		}
		
		
		final List<TrajectoryFileEntry> tfesToRun = new ArrayList<TrajectoryFileEntry>(tfesToUse.size());
		tfesToRun.addAll(tfesToUse);
				
		
		Set<ParamConfiguration> configs = new HashSet<ParamConfiguration>();
		for(TrajectoryFileEntry tfe : tfes)
		{
			configs.add(tfe.getConfiguration());
		}
		

		
		List<RunConfig> runConfigs = getRunConfigs(configs, pisps, cutoffTime,execConfig);
		
		log.info("Validation needs {} algorithm runs to validate {} configurations found, each on {} problem instance seed pairs", runConfigs.size(), configs.size(),pisps.size());
		
		Date d = new Date(System.currentTimeMillis());
		DateFormat df = DateFormat.getDateTimeInstance();	
		
	
		
		Date endTime = new Date(System.currentTimeMillis() + (long) (1.1*(cutoffTime * runConfigs.size()  * 1000/ cores)));
		log.info("Validation start time: {}. Approximate worst-case end time: {}",df.format(d), df.format(endTime));
		
		
		//List<AlgorithmRun> runs = validatingTae.evaluateRun(runConfigs);
		
		final AtomicReference<RuntimeException> exception = new AtomicReference<RuntimeException>();
		
		final SortedMap<TrajectoryFileEntry, Double> finalPerformance = new ConcurrentSkipListMap<TrajectoryFileEntry, Double>();
		
		WaitableTAECallback callback = new WaitableTAECallback(new TargetAlgorithmEvaluatorCallback()
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
					if(runs.get(0) != null)
					{
						appendInstanceResultFile(outputDir, finalPerformance,  numRun,options, options.useWallClockTime, runs.get(0).getRunConfig(), validatingTae);
					} else
					{
						appendInstanceResultFile(outputDir, finalPerformance,  numRun,options, options.useWallClockTime, null, null);
					}
					
					
					
					
				} catch(IOException e)
				{
					log.error("Could not write results file:", e);
				}
				
				if(options.writeThetaMatrix)
				{
					try {
						ConcurrentHashMap<ParamConfiguration, Map<ProblemInstanceSeedPair, AlgorithmRun>> matrixRuns = new ConcurrentHashMap<ParamConfiguration, Map<ProblemInstanceSeedPair, AlgorithmRun>>();
						
						for(AlgorithmRun run : runs)
						{
							matrixRuns.putIfAbsent(run.getRunConfig().getParamConfiguration(), new HashMap<ProblemInstanceSeedPair, AlgorithmRun>());
							
							Map<ProblemInstanceSeedPair, AlgorithmRun> configRuns = matrixRuns.get(run.getRunConfig().getParamConfiguration());
							
							configRuns.put(run.getRunConfig().getProblemInstanceSeedPair(), run);
						}
						
						
						List<ParamConfiguration> configs = new ArrayList<ParamConfiguration>();
						
						for(AlgorithmRun run : runs)
						{
							if(configs.contains(run.getRunConfig().getParamConfiguration())) continue;
							configs.add(run.getRunConfig().getParamConfiguration());
						}
						
						
						writeConfigurationResultsMatrix(configs,matrixRuns, tfes, options, outputDir, numRun, runObj);
						
					} catch(IOException e)
					{
						log.error("Could not write matrix file:",e);
					}
				} 
				
				
				if(options.saveStateFile)
				{
					try {
						
						log.info("Writing state file  into " + outputDir);
						writeLegacyStateFile(outputDir, options.outputFileSuffix,runs, numRun, testInstGen, interInstanceObjective, interInstanceObjective, runObj);
					} catch(RuntimeException e)
					{
						log.error("Couldn't write state file:",e);
					}
				}
				
			}

			

			@Override
			public void onFailure(RuntimeException t) {
				exception.set(t);
			}
			
		});

		
		validatingTae.evaluateRunsAsync(runConfigs, callback);
		
		
		if(!validatingTae.areRunsPersisted() || waitForRuns)
		{
			log.debug("Waiting until validation completion");
			callback.waitForCompletion();
		}
		
	
		if(exception.get() != null)
		{
			throw exception.get();
		}
		
		return finalPerformance;

		
		
		
		
	}







private List<RunConfig> getRunConfigs(Set<ParamConfiguration> configs, List<ProblemInstanceSeedPair> pisps, double cutoffTime,AlgorithmExecutionConfig execConfig) 
{
	
	List<RunConfig> runConfigs  = new ArrayList<RunConfig>(pisps.size()*configs.size());
	for(ParamConfiguration config: configs)
	{
		for(ProblemInstanceSeedPair pisp : pisps)
		{
			runConfigs.add(new RunConfig(pisp, cutoffTime,config,execConfig));
		}
	}
	
	return runConfigs;
}






	private static List<ProblemInstanceSeedPair> getValidationRuns(List<ProblemInstance> pis,RandomInstanceSeedGenerator testInstGen, ValidationRoundingMode mode,int validationRunsCount, int testSeedsPerInstance, int testInstancesCount) {
		
		int numRuns = 0;
		
		switch(mode)
		{
		case UP:			
			numRuns = Math.round( (float) (Math.ceil( validationRunsCount / (float) testInstancesCount ) * testInstancesCount));
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
	
	private static List<ProblemInstanceSeedPair> getValidationRuns( List<ProblemInstance> pis,	SetInstanceSeedGenerator testInstGen, ValidationRoundingMode mode,  int validationRunsCount)
	{

		List<ProblemInstance> instances = testInstGen.getProblemInstanceOrder(pis);
		
		int numRuns = 0;
		switch(mode)
		{
			case UP:
				numRuns = Math.round( (float) (Math.ceil( validationRunsCount / (float) instances.size() ) * instances.size()));
				break;
			case NONE:
				numRuns = Math.min(instances.size(), validationRunsCount);
				break;
			default:
				throw new IllegalStateException("Unknown mode: " + mode);
		}
		
		List<ProblemInstanceSeedPair> runs = new ArrayList<ProblemInstanceSeedPair>(numRuns);
		for( int i=0; i < numRuns; i++)
		{
			ProblemInstance pi = instances.get(i);
			runs.add(new ProblemInstanceSeedPair(pi,testInstGen.getNextSeed(pi)));
		}
		
		return runs;
	}

	/**
	 * Writes a matrix of the runs to the given file
	 * @param matrixRuns
	 * @param tfes
	 * @throws IOException 
	 */
	private static void writeConfigurationResultsMatrix( List<ParamConfiguration> inOrderConfigs, 
			ConcurrentHashMap<ParamConfiguration, Map<ProblemInstanceSeedPair, AlgorithmRun>> matrixRuns,
			List<TrajectoryFileEntry> tfes, ValidationOptions validationOptions, String outputDir, long numRun, RunObjective runObj) throws IOException {


		String suffix = (validationOptions.outputFileSuffix.trim().equals("")) ? "" : "-" + validationOptions.outputFileSuffix.trim();
		File f = new File(outputDir +  File.separator + "configurationMatrix"+suffix+"-run" + numRun + ".csv");
		log.debug("Validation Configuration/PISP Matrix Results Written to: {}", f.getAbsolutePath());
		
		CSVWriter writer = new CSVWriter(new FileWriter(f));
		try {
			List<ProblemInstanceSeedPair> pisps = new ArrayList<ProblemInstanceSeedPair>();

			Set<ParamConfiguration> doneConfigs = new HashSet<ParamConfiguration>();
			
			for(ParamConfiguration config : inOrderConfigs)
			{
				
				if(doneConfigs.contains(config)) continue;
				doneConfigs.add(config);
				Map<ProblemInstanceSeedPair, AlgorithmRun> runs = matrixRuns.get(config);
	
				if(runs == null) continue;
				if(pisps.isEmpty())
				{
					pisps.addAll(runs.keySet());
					
					Collections.sort(pisps, new Comparator<ProblemInstanceSeedPair>()
							{
		
								@Override
								public int compare(ProblemInstanceSeedPair o1,
										ProblemInstanceSeedPair o2) {
									
									if(o1.getInstance().equals(o2.getInstance()))
									{
										return (int) Math.signum(o1.getSeed() - o2.getSeed());
									} else
									{
										return o1.getInstance().getInstanceID() - o2.getInstance().getInstanceID();
									}
									
									
									
								}
						
							});
					
					ArrayList<String> headerRow = new ArrayList<String>();
					
					headerRow.add("");
					for(ProblemInstanceSeedPair pisp : pisps)
					{
						headerRow.add(pisp.getInstance().getInstanceName() + "," + pisp.getSeed());
					}
					
					String[] header = headerRow.toArray(new String[0]);
					
					writer.writeNext(header);
				}
				
			
				ArrayList<String> dataRow = new ArrayList<String>();
				dataRow.add(config.getFormattedParamString(StringFormat.NODB_SYNTAX));
				for(ProblemInstanceSeedPair pisp : pisps)
				{
					AlgorithmRun run = runs.get(pisp);
					if(run == null)
					{
						throw new IllegalStateException("Expected all configurations to have the exact same pisps");
					}
					
					if(!run.getRunConfig().getProblemInstanceSeedPair().equals(pisp))
					{
						throw new IllegalStateException("DataStructure corruption detected ");
					}
					dataRow.add(String.valueOf(runObj.getObjective(run)));
					
				}
				
				String[] nextRow = dataRow.toArray(new String[0]);
				
				writer.writeNext(nextRow);
			}
			
			
		} finally
		{
			writer.close();
		}
		
		
		
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
		log.debug("Instance Validation Matrix Result Written to: {}", f.getAbsolutePath());
		
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
		File f = new File(outputDir + File.separator +  "validationInstanceSeedResult"+suffix+"-run" + numRun + ".csv");
		
		log.debug("Instance Seed Result File Written to: {}", f.getAbsolutePath());
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
		File f = new File(outputDir + File.separator +  "rawValidationExecutionResults"+suffix+"-run" + numRun + ".csv");
		log.debug("Raw Validation Results File Written to: {}", f.getAbsolutePath());
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
	

	private void appendInstanceResultFile(String outputDir, Map<TrajectoryFileEntry, Double> finalPerformance, long numRun, ValidationOptions validationOptions, boolean useWallTime, RunConfig rc, TargetAlgorithmEvaluator tae) throws IOException {
		
		String suffix = (validationOptions.outputFileSuffix.trim().equals("")) ? "" : "-" + validationOptions.outputFileSuffix.trim();
		
		
		File classicValidationFile = new File(outputDir +  File.separator + "classicValidationResults"+suffix+"-run" + numRun + ".csv");
		
		File validationFile = new File(outputDir +  File.separator + "validationResults"+suffix+"-run" + numRun + ".csv");
		
		log.debug("Validation Results File Written to: {}", validationFile.getAbsolutePath());
		log.debug("Classic Validation Results File Written to: {}", classicValidationFile.getAbsolutePath());
	
		if(!classicValidationFile.exists())
		{
			classicValidationFile.createNewFile();
			
		} else
		{
			classicValidationFile.delete();
			classicValidationFile.createNewFile();
		}
		
		
		if(!validationFile.exists())
		{
			validationFile.createNewFile();
			
		} else
		{
			validationFile.delete();
			validationFile.createNewFile();
		}
		
	
		
		
		StringBuilder sbClassicValidation = new StringBuilder();
		StringBuilder sbValidation = new StringBuilder("\"Tuner Time\",\"Training (Emperical) Performance\",\"Test Set Performance\",\"AC Overhead Time\",\"Sample Call String\",\n");
		
		for(Entry<TrajectoryFileEntry, Double > ent : finalPerformance.entrySet())
		{
			double time;
					
			if(useWallTime)
			{
				time = ent.getKey().getWallTime();
			} else
			{
				time = ent.getKey().getTunerTime();
			}
			
			double empiricalPerformance = ent.getKey().getEmpericalPerformance();
			double testSetPerformance = ent.getValue();
			double acOverhead = ent.getKey().getACOverhead();
			
			sbClassicValidation.append(time).append(",").append(empiricalPerformance).append(",").append(testSetPerformance).append(",").append(acOverhead).append("\n");
			String callString = "Unavailable as we did no runs";
			if(tae != null && rc != null)
			{
				callString = tae.getManualCallString(rc);
			}
			sbValidation.append(time).append(",").append(empiricalPerformance).append(",").append(testSetPerformance).append(",").append(acOverhead).append(",\"").append(callString).append("\",\n");
		}
		if(!classicValidationFile.canWrite())
		{
			log.error("Could not write classic trajectory file would have written: {}" , sbClassicValidation.toString());
		} else
		{
			PrintWriter output = new PrintWriter(new FileOutputStream(classicValidationFile,true));
			output.append(sbClassicValidation);
			output.close();
		}
		
		if(!validationFile.canWrite())
		{
			log.error("Could not write trajectory file would have written: {}" , sbValidation.toString());
		} else
		{
			PrintWriter output = new PrintWriter(new FileOutputStream(validationFile,true));
			output.append(sbValidation);
			output.close();
		}
		
		
		
}
	
	private static void writeLegacyStateFile(String outputDir, String suffix, List<AlgorithmRun> runs, long numRun, InstanceSeedGenerator insc, OverallObjective interRunObjective, OverallObjective intraRunObjective, RunObjective runObj)
	{
		RunHistory rh = new NewRunHistory( interRunObjective, intraRunObjective, runObj);
		for(AlgorithmRun run : runs)
		{
			try {
				rh.append(run);
			} catch (DuplicateRunException e) {
				throw new DeveloperMadeABooBooException("Duplicate run was detected by runHistory, this shouldn't be possible in validation");
			}
		}
		
		StateFactory sf = new LegacyStateFactory(outputDir + File.separator + "state" + suffix +"-run" + numRun, null);
		
		
		
		StateSerializer ss = sf.getStateSerializer("it", 1);
		
		
		ss.setRunHistory(rh);
		ss.save();
			
	}

	


	
}
