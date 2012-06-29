package ca.ubc.cs.beta.smac.validation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.com.bytecode.opencsv.CSVWriter;
import ca.ubc.cs.beta.ac.config.RunConfig;
import ca.ubc.cs.beta.config.ValidationOptions;
import ca.ubc.cs.beta.config.ValidationRoundingMode;
import ca.ubc.cs.beta.configspace.ParamConfiguration;
import ca.ubc.cs.beta.probleminstance.ProblemInstance;
import ca.ubc.cs.beta.probleminstance.ProblemInstanceSeedPair;
import ca.ubc.cs.beta.seedgenerator.InstanceSeedGenerator;
import ca.ubc.cs.beta.seedgenerator.RandomInstanceSeedGenerator;
import ca.ubc.cs.beta.seedgenerator.SetInstanceSeedGenerator;
import ca.ubc.cs.beta.smac.OverallObjective;
import ca.ubc.cs.beta.smac.RunObjective;
import ca.ubc.cs.beta.smac.ac.runners.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.smac.ac.runs.AlgorithmRun;

public class Validator {
	
	private static Logger log = LoggerFactory.getLogger(Validator.class);
	
	public void validate(List<ProblemInstance> testInstances, ParamConfiguration incumbent, ValidationOptions config,double cutoffTime,InstanceSeedGenerator testInstGen, TargetAlgorithmEvaluator validatingTae, 
			String outputDir,
			RunObjective runObj,
			OverallObjective intraInstanceObjective, OverallObjective interInstanceObjective, double tunerTime, long numRun) {
		
		validate(testInstances, incumbent, config, cutoffTime, testInstGen, validatingTae, outputDir, runObj, intraInstanceObjective, interInstanceObjective,tunerTime, 0,0, numRun);
	}
		
		
	
public void validate(List<ProblemInstance> testInstances, ParamConfiguration incumbent, ValidationOptions config,double cutoffTime,InstanceSeedGenerator testInstGen, TargetAlgorithmEvaluator validatingTae, 
		String outputDir,
		RunObjective runObj,
		OverallObjective intraInstanceObjective, OverallObjective interInstanceObjective, double tunerTime,  double empericalPerformance, double cpuTime, long numRun) {
		
		int testInstancesCount = Math.min(config.numberOfTestInstances, testInstances.size());
		int testSeedsPerInstance = config.numberOfTestSeedsPerInstance;
		int validationRunsCount = config.numberOfValidationRuns;
		
		ValidationRoundingMode mode = config.validationRoundingMode;		
		
		List<RunConfig> validationRuns = new ArrayList<RunConfig>();
	
		if(testInstGen instanceof SetInstanceSeedGenerator)
		{
			validationRuns = getValidationRuns(testInstances, (SetInstanceSeedGenerator) testInstGen, validationRunsCount, cutoffTime, incumbent);
		} else if(testInstGen instanceof RandomInstanceSeedGenerator)
		{
			validationRuns = getValidationRuns(testInstances, (RandomInstanceSeedGenerator) testInstGen,mode, validationRunsCount, testSeedsPerInstance, testInstancesCount, cutoffTime, incumbent);
		} else
		{
			throw new IllegalStateException("Unknown Instance Seed Generator specified");
		}
		
		
		

		log.info("Scheduling {} validation runs", validationRuns.size());
		List<AlgorithmRun> runs = validatingTae.evaluateRun(validationRuns);
		
		try
		{
			writeInstanceRawResultsFile(runs, config, outputDir, numRun);
		} catch(IOException e)
		{
			log.error("Could not write results file", e);
		}
		
		
		try
		{
			writeInstanceSeedResultFile(runs, config, outputDir, runObj, numRun);
		} catch(IOException e)
		{
			log.error("Could not write results file", e);
		}
		
		
		try
		{
			double testSetPerformance = writeInstanceResultFile(runs, config, outputDir, cutoffTime, runObj, intraInstanceObjective, interInstanceObjective, numRun);
			
			appendInstanceResultFile(outputDir, tunerTime, empericalPerformance, testSetPerformance, cpuTime, numRun);
		} catch(IOException e)
		{
			log.error("Could not write results file:", e);
		}
		
		
		//writeInstanceResultFile(runs, config);
		
		
	}






	private static List<RunConfig> getValidationRuns(List<ProblemInstance> pis,
			RandomInstanceSeedGenerator testInstGen, ValidationRoundingMode mode,
			int validationRunsCount, int testSeedsPerInstance,
			int testInstancesCount, double cutoffTime,
			ParamConfiguration incumbent) {
		
		
		List<ProblemInstance> instances = testInstGen.getProblemInstanceOrder(pis);
		
		
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
		List<RunConfig> runs = new ArrayList<RunConfig>(numRuns);
		int runsScheduled = 0;
		log.info("Creating Runs ");
endloop:
		while(true)
		{
			for(int i=0; i < testInstancesCount; i++)
			{
				ProblemInstance pi = pisToUse.get(i);
				runs.add(new RunConfig(new ProblemInstanceSeedPair(pi,testInstGen.getNextSeed(pi)), cutoffTime, incumbent));
				
				runsScheduled++;
				
				if(runsScheduled >= numRuns) break endloop;
			}
		}
		
		

		return runs;
	}




	private static List<RunConfig> getValidationRuns( List<ProblemInstance> pis,
			SetInstanceSeedGenerator testInstGen, int validationRunsCount, 
			double cutoffTime, ParamConfiguration incumbent) {

		List<ProblemInstance> instances = testInstGen.getProblemInstanceOrder(pis);
		int numRuns = Math.min(instances.size(), validationRunsCount);
		List<RunConfig> runs = new ArrayList<RunConfig>(numRuns);
		for( int i=0; i < numRuns; i++)
		{
			ProblemInstance pi = instances.get(i);
			runs.add(new RunConfig(new ProblemInstanceSeedPair(pi,testInstGen.getNextSeed(pi)), cutoffTime, incumbent));
		}
		
		
		
		return runs;
	}




	/**
	 * Writes a CSV File which has the matrix of runs 
	 * @param runs
	 * @param smacConfig
	 * @param outputDir
	 * @param cutoffTime
	 * @param runObj
	 * @param overallObj
	 * @return - Overall objective over test set (For convinence)
	 * @throws IOException
	 */
	private static double writeInstanceResultFile(List<AlgorithmRun> runs,ValidationOptions smacConfig, String outputDir, double cutoffTime,  RunObjective runObj, OverallObjective intraInstanceObjective, OverallObjective interInstanceObjective, long numRun) throws IOException 
	{
		Map<ProblemInstance, List<AlgorithmRun>> map = new LinkedHashMap<ProblemInstance,List<AlgorithmRun>>();
		
		File f = new File(outputDir +  File.separator + "validationResultsMatrix-run" + numRun + ".csv");
		log.info("Instance Validation Matrix Result Written to: {}", f.getAbsolutePath());
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
		
		
		for(Entry<ProblemInstance, List<AlgorithmRun>> piRuns : map.entrySet())
		{
			List<String> outputLine = new ArrayList<String>();
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
		String[] args = { "Overall Objective On Test Set", String.valueOf(overallObjective)};
		writer.writeNext(args);
		
		writer.close();
		
		return overallObjective;
	}




	private static void writeInstanceSeedResultFile(List<AlgorithmRun> runs,ValidationOptions smacConfig, String outputDir, RunObjective runObj, long numRun) throws IOException
	{
		
		File f = new File(outputDir + "validationInstanceSeedResult-run" + numRun + ".csv");
		log.info("Instance Seed Result File Written to: {}", f.getAbsolutePath());
		CSVWriter writer = new CSVWriter(new FileWriter(f));
		
		
		if(!smacConfig.noValidationHeaders)
		{
			String[] args = {"Seed","Instance","Response"};
			writer.writeNext(args);
		}
		
		for(AlgorithmRun run : runs)
		{
			
			String[] args = { String.valueOf(run.getInstanceRunConfig().getAlgorithmInstanceSeedPair().getSeed()),run.getInstanceRunConfig().getAlgorithmInstanceSeedPair().getInstance().getInstanceName(), String.valueOf(runObj.getObjective(run)) };
			writer.writeNext(args);
		}
		
		writer.close();
		
	}




	private static void writeInstanceRawResultsFile(List<AlgorithmRun> runs,ValidationOptions smacConfig, String outputDir, long numRun) throws IOException
	{
		
		File f = new File(outputDir + "rawValidationExecutionResults-run" + numRun + ".csv");
		log.info("Instance Seed Result File Written to: {}", f.getAbsolutePath());
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
	

	private void appendInstanceResultFile(String outputDir, double tunerTime,
		double empericalPerformance, double testSetPerformance, double cpuTime, long numRun) throws IOException {
		File f = new File(outputDir +  File.separator + "classicValidationResults-run" + numRun + ".csv");
	
		if(!f.exists())
		{
			f.createNewFile();
			
		}
		
		
		StringBuilder sb = new StringBuilder();
		sb.append(tunerTime).append(",").append(empericalPerformance).append(",").append(testSetPerformance).append(",").append(cpuTime).append("\n");
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
