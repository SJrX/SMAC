package ca.ubc.cs.beta.smac.matlab;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.cs.beta.aclib.algorithmrun.AlgorithmRun;
import ca.ubc.cs.beta.aclib.algorithmrun.ExistingAlgorithmRun;
import ca.ubc.cs.beta.aclib.execconfig.AlgorithmExecutionConfig;
import ca.ubc.cs.beta.aclib.runconfig.RunConfig;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.TargetAlgorithmEvaluator;
import ca.ubc.cs.beta.aclib.targetalgorithmevaluator.factory.TargetAlgorithmEvaluatorFactory;
import ca.ubc.cs.beta.smac.SimpleExecutor;
import ca.ubc.cs.beta.smac.ac.exceptions.TargetAlgorithmExecutionException;

/**
 * This class roughly corresponds to the get_single_results.m.
 * 
 * This class may not be fully implemented as it is merely a staging ground to get stuff into Java
 * I fully expect this class will be replaced.
 * 
 * @author seramage
 *
 */
public class BatchRunner {
	private static final String TEMP_FILE_PREFIX = "algo-run-";
	private static final String TEMP_FILE_SUFFIX = ".ac-tmp";
	
	
	
	/**
	 * Makes a target run 
	 * 
	 * @param func - An instance of the Target Algorithm Configuration Problem
	 * @param theta_idx - pointer to the instance of parameter configurations
	 * @param instance_filenames - The instance file name we will run
	 * @param seed - Seed to run with
	 * @param censorLimits - 
	 */
	public void startManyRuns(Object func, Object theta_idx, Object instance_filenames, Object seed, Object censorLimits)
	{
		
		
		
	}
		
	/**
	 * This method roughly corresponds to: startRunsInBatch although as opposed to calling executeRun, it takes the result and validates 
	 * and some checks are handled internally in the AlgorithmRun objects.
	 * 
	 * @param runs - List of runs that have already been run()
	 * @return
	 */
	public List<AlgorithmRun> validateRuns(List<AlgorithmRun> runs)
	{
		for(AlgorithmRun run : runs)
		{
			if (!run.isRunResultWellFormed())
			{
				System.err.println("[ERROR]: Run contains a malformed result:\n" + run); 
					
			}
		}
		
		return runs;
	}
	
	private static TargetAlgorithmEvaluator tae = null;
	private static Logger log = null;
	
	public List<AlgorithmRun> executeSurrogateRun(AlgorithmExecutionConfig execConfig, List<RunConfig> runConfigs, String[] dynamicClassPath)
	{
		
		
		 
		if(log == null)
		{
			/*
				Context lc = (Context) LoggerFactory.getILoggerFactory();
			    // print logback's internal status
			    StatusPrinter.print(lc);
			    */
			    log = LoggerFactory.getLogger(getClass());
			
			    
			 
			 Map<Object,Object > props = System.getProperties();
			 StringBuilder sb = new StringBuilder();
			 for (Entry<Object, Object> ent : props.entrySet())
			 {
				 
				 sb.append(ent.getKey().toString()).append("=").append(ent.getValue().toString()).append("\n");
				 
		           
		     }
			 
			 log.info("[System Properties] \n{}\n",sb.toString());
			 
			
			 Map<String, String> env = System.getenv();
				
			sb = new StringBuilder();
				 for (String envName : env.keySet()) {
					 sb.append(envName).append("=").append(env.get(envName)).append("\n");
					 
			           
			        }
				
				
			 log.info("[Enviroment Properties] \n{}\n", sb.toString());
		} 
		if(tae == null)
		{
			ArrayList<URL> urls = new ArrayList<URL>();
			for(String dynamiccp : dynamicClassPath)
			{
				try {
					urls.add(new File(dynamiccp).toURI().toURL());
				} catch (MalformedURLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			ClassLoader cl = new URLClassLoader(urls.toArray(new URL[0]));
			
	
			TargetAlgorithmEvaluatorFactory taeFact;
			try {
				taeFact = (TargetAlgorithmEvaluatorFactory) Class.forName("ca.ubc.cs.beta.models.surrogate.algorithmrun.SurrogateTargetAlgorithmEvaluatorFactory").newInstance();
				tae = taeFact.getTargetAlgorithmEvaluator(execConfig, 1);
				
				
				return tae.evaluateRun(runConfigs);
			} catch(RuntimeException e)
			{
				throw e;
			} catch (Exception e) {
				
				
				throw new RuntimeException(e);
			} 
		} else
		{
			return tae.evaluateRun(runConfigs);
		}
		
		
	}
	
	/**
	 * This class corresponds roughly to startManyRuns in Batch
	 * 
	 *  From the parameters it creates files required to execute the required required command line wrapper.
	 *  
	 * @param execConfig
	 * @param instanceRunConfig
	 * @return
	 */
	public List<AlgorithmRun> executeRun(AlgorithmExecutionConfig execConfig, List<RunConfig> instanceRunConfigs, String[] dynamicClassPath)
	{	
		
		if(execConfig.getAlgorithmExecutable().contains("ruby surrogate_wrapper.rb"))
		{
			return executeSurrogateRun(execConfig, instanceRunConfigs, dynamicClassPath);
		}
		/**
		 * MATLAB PORT NOTE: 
		 * The abstractions above are vastly different than the MATLAB version.
		 * The params should have been dealt with in the the construction of the instanceRunConfig objects.
		 */
		File tmpFile, tmpFileIn;
		PrintWriter out;
		
		/**
		 * Write out the file
		 */
		
		try { 
		 tmpFile = File.createTempFile(TEMP_FILE_PREFIX,TEMP_FILE_SUFFIX);
		 tmpFile.deleteOnExit();
		 
		 
		 out = new PrintWriter(tmpFile);
		 
		 tmpFileIn = File.createTempFile(TEMP_FILE_PREFIX,TEMP_FILE_SUFFIX);
		 tmpFile.deleteOnExit();
		 
		} catch(IOException e)
		{
			throw new TargetAlgorithmExecutionException("Could not create temp file for output");
		}
		
				
		out.println(execConfig.getAlgorithmExecutable());
		out.println(execConfig.getAlgorithmExecutionDirectory());
		out.println(execConfig.getParamFile().getParamFileName());
		out.println("-1");
		
		for(RunConfig instanceRunConfig: instanceRunConfigs)
		{
			if(instanceRunConfig.getProblemInstanceSeedPair().getInstance().getInstanceName().indexOf(" ") != -1)
			{
				throw new TargetAlgorithmExecutionException("Instance name contains a space which breaks the file format we are being asked to write to");
			}
			
			out.print(instanceRunConfig.getProblemInstanceSeedPair().getInstance().getInstanceName());
			out.print(" ");
			out.print(instanceRunConfig.getProblemInstanceSeedPair().getSeed());
			out.print(" ");
			out.print(instanceRunConfig.getCutoffTime());
			out.print(" ");
			out.print(instanceRunConfig.getParamConfiguration().getFormattedParamString("","=","'",", "));
			out.println();
			
		}
		
		out.close();
		
		
		/**
		 * Execute Algorithm Wrapper
		 */
		SimpleExecutor smExec = new SimpleExecutor();

		boolean rubyExecutor = false;
		String rootDir = ".";
		String execString = "";
		if(rubyExecutor)
		{
			execString = "ruby scripts/al_run_configs_in_file_nodb.rb ";
		} else
		{
			StringBuilder dynamicClassPathStr = new StringBuilder();
			for(String s : dynamicClassPath)
			{
				dynamicClassPathStr.append(s).append(File.pathSeparator);
			}
			
			System.out.println("Class path for Simple Executor defined in: " + this.getClass().getCanonicalName());
			
			
			execString = "java -cp " + dynamicClassPathStr.toString() + " ca.ubc.cs.beta.smac.executors.AlgorithmRunConfiguratorExecutor ";
		}
		
		execString += tmpFile.getAbsoluteFile() + " " +  tmpFileIn.getAbsoluteFile();
		smExec.run(execString, rootDir);
		
		/**
		 * Parse Results File
		 */
		List<AlgorithmRun> runs = new ArrayList<AlgorithmRun>(instanceRunConfigs.size());
		try {
			BufferedReader r = null;
			try {
				r = new BufferedReader(new FileReader(tmpFileIn));
			
			
				int i=0; 
				String result;
				//The output file has to be in the same order as the inputs
				while((result = r.readLine()) != null)
				{
					System.out.println("Trying to parse " + result);
					runs.add(new ExistingAlgorithmRun(execConfig, instanceRunConfigs.get(i), result));
					i++;
				}
			} finally
			{
				if(r != null) r.close();
			}
			
		} catch (FileNotFoundException e) {

			throw new TargetAlgorithmExecutionException("Can't read results from Algorithm runs, the output file was not found");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			throw new TargetAlgorithmExecutionException("CSV Parsing failed for output file");
		}
		return runs;
		
	}



}
 