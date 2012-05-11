package ca.ubc.cs.beta.smac.executors;



import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;


import ca.ubc.cs.beta.ac.config.AlgorithmExecutionConfig;
import ca.ubc.cs.beta.ac.config.ProblemInstance;
import ca.ubc.cs.beta.ac.config.ProblemInstanceSeedPair;
import ca.ubc.cs.beta.ac.config.RunConfig;
import ca.ubc.cs.beta.configspace.ParamConfiguration;
import ca.ubc.cs.beta.configspace.ParamConfigurationSpace;

import ca.ubc.cs.beta.smac.ac.runners.AlgorithmRunner;
import ca.ubc.cs.beta.smac.ac.runners.AutomaticConfiguratorFactory;
import ca.ubc.cs.beta.smac.ac.runs.AlgorithmRun;

/**
 * This class is roughly a translation of al_run_configs_in_file.rb
 * and should be a drop in replacement for it.
 *  
 * @author seramage
 *
 */
public class AlgorithmRunConfiguratorExecutor {

	
	
	public static Scanner getScannerForFile(String file) throws FileNotFoundException
	{
		File f = new File(file);
	
		//BufferedInputStream buf = new BufferedInputStream(new FileInputStream(f));
		
		Scanner pstream = new Scanner(new FileInputStream(f));
		
		return pstream;
		
	}
	
	public static void main(String[] args)
	{
		
		if(args.length != 2)
		{
			System.out.println("Usage: " + AlgorithmRunConfiguratorExecutor.class.getCanonicalName() + " <filename> <outfile>.\n");
			System.out.println("That file: <algo>\n<exec_path>\n<param_filename>\n<oncluster>\n");
			System.out.println("Every line after that: <instance_filename> <seed> <cutoff_time> <param_string>");
			System.out.println("One result per line is written into the outfile, in the same order as in <filename>.");
			System.exit(-1);
		}
		
		try {
		
		/**
		 * Parse values from Input File, and in some cases 
		 * 
		 */
		Scanner infile = getScannerForFile(args[0]);
		String algo = infile.nextLine();
		String exec_path = infile.nextLine();
		String param_filename = infile.nextLine();
		
		//NOTE: This value is ignored currently
		
		@SuppressWarnings("unused")
		String oncluster = infile.nextLine();
		
		
		
		File f = new File(param_filename);
		
		if(!f.exists())
		{
			throw new FileNotFoundException("Cannot find Parameter File");
		}
			
		ParamConfigurationSpace paramFile = new ParamConfigurationSpace(f);		
		
		
		
		AlgorithmExecutionConfig execConfig = new AlgorithmExecutionConfig(algo, exec_path, paramFile, false);
		
		f = new File(args[1]);
		
		
		if(!f.createNewFile())
		{
			//throw new IllegalArgumentException("Output File Already Exists");
		}
		
		
		
		
		System.out.println("[WARN]: File Format not robust to spaces");
		
		
		List<RunConfig> runConfigs = new LinkedList<RunConfig>();
		
		while(infile.hasNextLine())
		{
			String line = infile.nextLine();
			String[] lineArgs = line.split("\\s+",4);//Split 3 spaces
			if(lineArgs.length != 4)
			{
				throw new IllegalArgumentException("Line " + line + " should have been of the form <instance_filename> <seed> <cutoff_time> <param_string>");
			}
			
			ParamConfiguration param = paramFile.getConfigurationFromString(lineArgs[3], ParamConfiguration.StringFormat.NODB_SYNTAX);
			RunConfig runConfig =new RunConfig( new ProblemInstanceSeedPair(new ProblemInstance(lineArgs[0]), Long.valueOf(lineArgs[1])),Double.valueOf(lineArgs[2]),param); 
			runConfigs.add(runConfig);

		}

		System.out.println("[INFO]: Found " + runConfigs.size()+ " runs");
		
		PrintWriter resultFileOut = new PrintWriter(new FileOutputStream(f));
		
		AlgorithmRunner runner;
		boolean concurrent = true;
		if(concurrent)
		{
			runner = AutomaticConfiguratorFactory.getConcurrentAlgorithmRunner(execConfig, runConfigs);
		} else
		{
			runner = AutomaticConfiguratorFactory.getSingleThreadedAlgorithmRunner(execConfig, runConfigs);
		}
		
		
		List<AlgorithmRun> runResults = runner.run();
		
		for(AlgorithmRun run : runResults)
		{
				resultFileOut.write(run.getResultLine() + "\n");
				
		}
		
		resultFileOut.close();
		
		
		//System.exit(0);
		return;
		}catch(NumberFormatException e)
		{
			System.err.println("[ERROR]: Input file is malformed (invalid numerical value) : " + e.getMessage());
			
		} catch(NoSuchElementException e)
		{
			System.err.println("[ERROR]: Input file is malformed (expected atleast one more line)");
		
		} catch(FileNotFoundException e)
		{
			
			System.err.println("[ERROR]: Can't find input file: " + e.getMessage());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		
	
		System.exit(-1);
		
		
	}
	

}
