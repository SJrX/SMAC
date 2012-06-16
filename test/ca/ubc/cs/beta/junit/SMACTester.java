package ca.ubc.cs.beta.junit;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import ca.ubc.cs.beta.config.JCommanderHelper;
import ca.ubc.cs.beta.config.ScenarioConfig;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;

public class SMACTester {
	
	/*
	@Test
	public void test()
	{
		Class[] cls = { ParallelTest.class};
		Result r = JUnitCore.runClasses(ParallelComputer.methods(), cls);
		
		if(r.getFailureCount() > 0)
		{
			fail("Failed Test Occured");
			
		}
	}
		
	

	public static class ParallelTest 
	{
		*/
		
		
		public int runSMAC(String scenarioFile, boolean adaptiveCapping, int iterationLimit, boolean ROARMode, int restoreIteration, int id )
		{
			String experimentDir = (new File(scenarioFile)).getParent();
			String runID = new File(scenarioFile).getName() + "-JUNIT";
			
			/*ScenarioConfig config = new ScenarioConfig();
			String[] args = {"--scenarioFile", scenarioFile};
			JCommanderHelper.parse(new JCommander(config), args);
			
			System.out.println(config);
			if (true) return true;
			*/
			
			final ScenarioConfig sc = new ScenarioConfig();
			
			
			JCommander jcom = new JCommander(sc);
			String[] args = {"--scenarioFile",scenarioFile};
			
			JCommanderHelper.parse(jcom, args);
			
			boolean deterministic = (sc.deterministic > 0);
			
			
			String execString = "./smac --scenarioFile " +  scenarioFile + " --numIterations " + iterationLimit + " --runID "  + runID + "-" + id + " --experimentDir " + experimentDir + " --seed " + Math.abs((new Random()).nextInt()) + " --skipInstanceFileCheck --skipValidation ";
			
			
			
			String runDir = "/ubc/cs/research/arrow/seramage/software/smac";
			if(restoreIteration > 0)
			{
				execString += " --restoreStateFrom " + experimentDir + File.separator + "paramils-out" + File.separator +  runID + "-" + (id-1)+ File.separator + "state";
				execString += " --restoreIteration " + restoreIteration+ " ";
			}
			
			if(adaptiveCapping)
			{
				execString += " --adaptiveCapping ";
			}
			
			if(ROARMode)
			{
				execString += " --executionMode ROAR";
			}
			int iteration=0;
				
			boolean resultFound = false;
			Queue<String> last10Lines = new LinkedList<String>();
			
			try {
				Process p;
			
					System.out.println(execString);
					p = Runtime.getRuntime().exec(execString, new String[0], new File(runDir));
					BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
					
					
					
					
					String it = "Iteration ";
					
					String line;
					System.out.print("RUNNING:");
					System.out.flush();
					String regex = "running config \\d+ on instance \\d+ with seed (-?\\d+) and captime \\d+";
					Pattern pat = Pattern.compile(regex);
					while((line = in.readLine()) != null)
					{
						
						
						
						
						
						
						//System.out.println(line);
						last10Lines.add(line);
						if(last10Lines.size() > 20)
						{
							last10Lines.poll();
						}
						if(line.contains("SMAC Completed Successfully"))
						{
							System.out.println(" [SUCCESS]");
							resultFound = true;
						} if(line.contains("Exiting Application with failure"))
						{
							System.out.println(" [FAILURE DETECTED]");
							for(String s : last10Lines)
							{
								System.out.println(s);
							}
							throw new IllegalStateException("Application Exited With Failure");
						} if(line.contains(it + iteration))
						{
							System.out.print(" " + iteration  + ":");
							System.out.flush();
							iteration++;
						}
						
						
						Matcher m = pat.matcher(line);
						
						if(m.find())
						{
							System.out.print("R");
							
							int seed = Integer.valueOf(m.group(1));//System.out.println(m.group(1));
							
							
							if(deterministic)
							{
									if(seed != -1) 
									{
										p.destroy();
										in.close();
										fail("Expected seed to be -1 on run: " + m.group(0));
									}
							} else
							{
								if(seed == -1) 
								{
									p.destroy();
									in.close();
									fail("Expected seed to be >0 on run: " + m.group(0));
								}
							}
							
							
						
						}
					}
					
					BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
					
					
					while( (line = err.readLine()) != null)
					{
						System.out.print("\nERR>" + line);
						System.out.flush();
					}
					
					p.waitFor();
			} catch (IOException e) {

				e.printStackTrace();
				
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				
			}
			
			
			System.out.println("");
			
			if(!resultFound)
			{
				for(String s : last10Lines)
				{
					System.out.println(s);
				}
				fail("Could not find result");
			}
			return iteration;
			
			
			
		}
		
		
		public int restoreIteration(int v)
		{
			/**
			 * See: http://graphics.stanford.edu/~seander/bithacks.html#RoundUpPowerOf2
			 */
			v--;
			v |= v >> 1;
			v |= v >> 2;
			v |= v >> 4;
			v |= v >> 8;
			v |= v >> 16;
			v++;
			
			v /= 2;
			
			return v;
			
		}
		
		public void testSMAC(String scenarioFile)
		{
			/**
			 * AC, Iteration Limit, ROAR Mode, Restore Iteration
			 */
			int id=0;
			int lastIteration;
			System.out.println("ROAR");
			lastIteration = runSMAC(scenarioFile, false, 18, true, 0, id++);
			lastIteration = restoreIteration(lastIteration);
			System.out.println("Restore");
			runSMAC(scenarioFile, false, 21, true, lastIteration,id++);
			
			
			System.out.println("ROAR+AC");
			lastIteration = runSMAC(scenarioFile, true, 18, true, 0, id++);
			lastIteration = restoreIteration(lastIteration);
			System.out.println("Restore+AC");
			runSMAC(scenarioFile, true, 21, true, lastIteration,id++);
			
			
			System.out.println("SMAC");
			lastIteration = runSMAC(scenarioFile, false, 18, false, 0, id++);
			lastIteration = restoreIteration(lastIteration);
			System.out.println("Restore");
			runSMAC(scenarioFile, false, 21, false, lastIteration,id++);
			
			
			System.out.println("SMAC+AC");
			lastIteration = runSMAC(scenarioFile, true, 18, false, 0, id++);
			lastIteration = restoreIteration(lastIteration);
			
			System.out.println("Restore+AC");
			runSMAC(scenarioFile, true, 21, false, lastIteration,id++);

			
		}
		
		@Test
		public void testSPEARSurrogate()
		{
			String scenarioFile = "/ubc/cs/home/s/seramage/arrowspace/smac-test/spear/spear-surrogate.txt";
			testSMAC(scenarioFile);
		
			
		}
		
		@Test
		public void testSATENSTEIN()
		{
			
			String scenarioFile = "/ubc/cs/home/s/seramage/arrowspace/smac-test/satenstein/satenstein.txt";
			testSMAC(scenarioFile);
		}
		
		@Test
		public void testSPEAR()
		{
			String scenarioFile = "/ubc/cs/home/s/seramage/arrowspace/smac-test/example_spear/scenario-Spear-SWGCP-sat-small-train-small-test.txt";
			testSMAC(scenarioFile);
		}
		
		@Test
		public void testSAPS()
		{
			
			String scenarioFile = "/ubc/cs/home/s/seramage/arrowspace/smac-test/example_saps/scenario-Saps-SWGCP-sat-small-train-small-test.txt";
			testSMAC(scenarioFile);
		}
		
		@Test
		public void testCPLEX()
		{
			String scenarioFile = "/ubc/cs/home/s/seramage/arrowspace/smac-test/cplex_surrogate/scenario-Cplex-BIGMIX.txt";
			testSMAC(scenarioFile);
		}
		
		
		
		/*
	}
	*/
	
}

